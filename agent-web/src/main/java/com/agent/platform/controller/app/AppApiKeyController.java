package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.app.AppApiKeyCreateDTO;
import com.agent.platform.dao.dto.app.AppApiKeyStatusDTO;
import com.agent.platform.dao.dto.app.AppApiKeyUpdateDTO;
import com.agent.platform.dao.entity.app.AppApiKey;
import com.agent.platform.service.app.AppApiKeyService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 应用 API 密钥（AppApiKey）管理接口。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service → Controller → URL」对齐规则：
 * <pre>
 *   表 app_api_key → 实体 AppApiKey → Mapper AppApiKeyMapper
 *                 → Service AppApiKeyService → 本类 AppApiKeyController
 *                 → URL /api/app/api-keys（kebab-case 复数）
 * </pre>
 * <p>密钥明文仅「创建 / 轮换」两个接口返回一次，其余场景一律只暴露前缀。
 */
@RestController
@RequestMapping("/api/app/api-keys")
@RequiredArgsConstructor
public class AppApiKeyController {

    private final AppApiKeyService apiKeyService;

    @GetMapping
    public Result<Page<AppApiKey>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long appId,
                                        @RequestParam(required = false) Integer status) {
        return Result.ok(apiKeyService.page(page, size, keyword, appId, status));
    }

    @PostMapping
    public Result<AppApiKey> create(@RequestBody AppApiKeyCreateDTO req) {
        return Result.ok(apiKeyService.create(
                req.getAppId(), req.getName(), req.getExpiresAt(), req.getRateLimit(), req.getRemark()));
    }

    /** 更新基础信息（expiresAt/rateLimit/remark 为空即清除） */
    @PutMapping("/{id}")
    public Result<AppApiKey> update(@PathVariable Long id, @RequestBody AppApiKeyUpdateDTO req) {
        return Result.ok(apiKeyService.update(
                id, req.getName(), req.getExpiresAt(), req.getRateLimit(), req.getRemark()));
    }

    /** 启用 / 禁用 */
    @PostMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id, @RequestBody AppApiKeyStatusDTO req) {
        apiKeyService.setStatus(id, req.getStatus());
        return Result.ok();
    }

    /** 轮换密钥：作废旧值并返回一次新明文 */
    @PostMapping("/{id}/rotate")
    public Result<AppApiKey> rotate(@PathVariable Long id) {
        return Result.ok(apiKeyService.rotate(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        apiKeyService.delete(id);
        return Result.ok();
    }
}
