package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppApiKey;
import com.agent.platform.dao.mapper.app.AppApiKeyMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

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
public interface AppApiKeyService {

    public Page<AppApiKey> page(long page, long size, String keyword, Long appId, Integer status);

    public AppApiKey getById(Long id);

    /** 创建密钥：明文仅在本次返回，落库仅存前缀与哈希 */
    public AppApiKey create(Long appId, String name, LocalDateTime expiresAt, Integer rateLimit, String remark);

    /** 更新基础信息（编辑框整体提交；expiresAt/rateLimit/remark 为空即清除） */
    public AppApiKey update(Long id, String name, LocalDateTime expiresAt, Integer rateLimit, String remark);

    public void setStatus(Long id, int status);

    public void delete(Long id);

    /** 轮换密钥：生成新明文并作废旧哈希，同时清零用量统计 */
    public AppApiKey rotate(Long id);

    /**
     * 明文密钥鉴权：密钥须存在、启用、未过期；
     * 通过后累计调用次数/最近使用时间，并执行进程内分钟级限流。
     *
     * @param appId     请求目标应用
     * @param plainKey  请求携带的明文密钥
     */
    public void authenticate(Long appId, String plainKey);
}
