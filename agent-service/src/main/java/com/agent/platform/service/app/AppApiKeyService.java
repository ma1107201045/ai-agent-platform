package com.agent.platform.service.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppApiKey;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.app.AppApiKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 应用 API 密钥服务（AppApiKey）。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service」对齐规则：
 * 表 app_api_key → 实体 AppApiKey → Mapper AppApiKeyMapper → 本类 AppApiKeyService。
 *
 * <p>安全设计：
 * <ul>
 *   <li>密钥明文 = sk- + 32 位随机十六进制，仅「创建 / 轮换」返回一次，落库仅存 SHA-256 哈希 + 前缀；</li>
 *   <li>列表仅展示前缀，防泄漏；一旦泄漏可通过「轮换」无感作废旧值；</li>
 *   <li>对外鉴权由 PortalPublicController 在携带 API Key 请求时调用 {@link #authenticate}。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AppApiKeyService {

    private static final String PLAIN_PREFIX = "sk-";
    /** 随机部分字节数（生成 32 位十六进制） */
    private static final int RANDOM_BYTES = 16;

    private final AppApiKeyMapper apiKeyMapper;
    private final AppAgentMapper appAgentMapper;

    /** keyId → [分钟桶, 计数]，进程内固定窗口限流（单机演示级） */
    private final ConcurrentHashMap<Long, long[]> rateWindows = new ConcurrentHashMap<>();

    // ==================== 密钥管理 ====================

    public Page<AppApiKey> page(long page, long size, String keyword, Long appId, Integer status) {
        LambdaQueryWrapper<AppApiKey> qw = new LambdaQueryWrapper<AppApiKey>()
                .eq(appId != null, AppApiKey::getAppId, appId)
                .eq(status != null, AppApiKey::getStatus, status)
                .orderByDesc(AppApiKey::getId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AppApiKey::getName, kw).or().like(AppApiKey::getKeyPrefix, kw));
        }
        Page<AppApiKey> result = apiKeyMapper.selectPage(new Page<>(page, size), qw);
        fillAppNames(result.getRecords());
        return result;
    }

    public AppApiKey getById(Long id) {
        AppApiKey key = apiKeyMapper.selectById(id);
        if (key == null) {
            throw new BizException("API 密钥不存在: " + id);
        }
        return key;
    }

    /** 创建密钥：明文仅在本次返回，落库仅存前缀与哈希 */
    @Transactional(rollbackFor = Exception.class)
    public AppApiKey create(Long appId, String name, LocalDateTime expiresAt, Integer rateLimit, String remark) {
        requireApp(appId);
        if (name == null || name.isBlank()) {
            throw new BizException("密钥名称不能为空");
        }
        if (name.length() > 64) {
            throw new BizException("密钥名称不能超过 64 字符");
        }
        if (rateLimit != null && rateLimit < 0) {
            throw new BizException("每分钟限流不能为负数");
        }
        String plain = generateKey();

        AppApiKey key = new AppApiKey();
        key.setTenantId(UserContext.getTenantId() == null ? 1L : UserContext.getTenantId());
        key.setAppId(appId);
        key.setName(name.trim());
        key.setKeyPrefix(displayPrefix(plain));
        key.setKeyHash(sha256Hex(plain));
        key.setStatus(1);
        key.setExpiresAt(expiresAt);
        key.setRateLimit(rateLimit);
        key.setUsageCount(0L);
        key.setRemark(remark);
        apiKeyMapper.insert(key);

        key.setAppName(appNameOf(appId));
        key.setPlainKey(plain);
        return key;
    }

    /** 更新基础信息（编辑框整体提交；expiresAt/rateLimit/remark 为空即清除） */
    public AppApiKey update(Long id, String name, LocalDateTime expiresAt, Integer rateLimit, String remark) {
        AppApiKey exists = getById(id);
        if (name == null || name.isBlank()) {
            throw new BizException("密钥名称不能为空");
        }
        if (rateLimit != null && rateLimit < 0) {
            throw new BizException("每分钟限流不能为负数");
        }
        exists.setName(name.trim());
        exists.setExpiresAt(expiresAt);
        exists.setRateLimit(rateLimit);
        exists.setRemark(remark);
        apiKeyMapper.updateById(exists);
        return exists;
    }

    public void setStatus(Long id, int status) {
        if (status != 0 && status != 1) {
            throw new BizException("状态值非法");
        }
        AppApiKey exists = getById(id);
        exists.setStatus(status);
        apiKeyMapper.updateById(exists);
        if (status == 0) {
            // 禁用时清理限流窗口，避免重启用后残留旧计数
            rateWindows.remove(id);
        }
    }

    public void delete(Long id) {
        getById(id);
        apiKeyMapper.deleteById(id);
        rateWindows.remove(id);
    }

    /** 轮换密钥：生成新明文并作废旧哈希，同时清零用量统计 */
    @Transactional(rollbackFor = Exception.class)
    public AppApiKey rotate(Long id) {
        AppApiKey exists = getById(id);
        String plain = generateKey();
        exists.setKeyPrefix(displayPrefix(plain));
        exists.setKeyHash(sha256Hex(plain));
        exists.setUsageCount(0L);
        exists.setLastUsedAt(null);
        apiKeyMapper.updateById(exists);
        rateWindows.remove(id);

        exists.setAppName(appNameOf(exists.getAppId()));
        exists.setPlainKey(plain);
        return exists;
    }

    // ==================== 对外调用鉴权 ====================

    /**
     * 明文密钥鉴权：密钥须存在、启用、未过期；
     * 通过后累计调用次数/最近使用时间，并执行进程内分钟级限流。
     *
     * @param appId     请求目标应用
     * @param plainKey  请求携带的明文密钥
     */
    public void authenticate(Long appId, String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            throw new BizException("API Key 不能为空");
        }
        AppApiKey key = apiKeyMapper.selectOne(new LambdaQueryWrapper<AppApiKey>()
                .eq(AppApiKey::getAppId, appId)
                .eq(AppApiKey::getKeyHash, sha256Hex(plainKey)));
        if (key == null) {
            throw new BizException("API Key 无效");
        }
        if (key.getStatus() == null || key.getStatus() != 1) {
            throw new BizException("API Key 已被禁用");
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException("API Key 已过期");
        }
        if (key.getRateLimit() != null && key.getRateLimit() > 0) {
            enforceRateLimit(key);
        }
        LambdaUpdateWrapper<AppApiKey> uw = new LambdaUpdateWrapper<AppApiKey>()
                .eq(AppApiKey::getId, key.getId())
                .setSql("usage_count = usage_count + 1")
                .set(AppApiKey::getLastUsedAt, LocalDateTime.now());
        apiKeyMapper.update(null, uw);
    }

    private void enforceRateLimit(AppApiKey key) {
        long minute = System.currentTimeMillis() / 60_000L;
        long[] window = rateWindows.compute(key.getId(), (k, old) -> {
            if (old == null || old[0] != minute) {
                return new long[]{minute, 1L};
            }
            old[1]++;
            return old;
        });
        if (window[1] > key.getRateLimit()) {
            throw new BizException("API Key 调用超限（每分钟上限 " + key.getRateLimit() + " 次）");
        }
    }

    // ==================== 内部工具 ====================

    private void requireApp(Long appId) {
        if (appId == null) {
            throw new BizException("请选择关联应用");
        }
        if (appAgentMapper.selectById(appId) == null) {
            throw new BizException("应用不存在: " + appId);
        }
    }

    private String appNameOf(Long appId) {
        AppAgent app = appAgentMapper.selectById(appId);
        return app == null ? null : app.getName();
    }

    private void fillAppNames(List<AppApiKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        Set<Long> appIds = keys.stream().map(AppApiKey::getAppId).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (!appIds.isEmpty()) {
            for (AppAgent app : appAgentMapper.selectBatchIds(appIds)) {
                names.put(app.getId(), app.getName());
            }
        }
        for (AppApiKey key : keys) {
            key.setAppName(names.get(key.getAppId()));
        }
    }

    /** 生成明文密钥：sk- + 32 位随机十六进制 */
    private String generateKey() {
        byte[] bytes = new byte[RANDOM_BYTES];
        new SecureRandom().nextBytes(bytes);
        return PLAIN_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /** 展示前缀（含随机部分前 9 位，形如 sk-1a2b3c4d5） */
    private String displayPrefix(String plain) {
        int end = Math.min(PLAIN_PREFIX.length() + 9, plain.length());
        return plain.substring(0, end);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
