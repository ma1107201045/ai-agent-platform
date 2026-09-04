package com.agent.platform.controller.guard;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.guard.GuardAppBind;
import com.agent.platform.dao.entity.guard.GuardRule;
import com.agent.platform.dao.vo.guard.GuardAppBindVO;
import com.agent.platform.service.guard.GuardService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内容安全接口（敏感词/正则/注入规则 + 应用绑定）
 *
 * <p>URL：/api/guard</p>
 */
@RestController
@RequestMapping("/api/guard")
@RequiredArgsConstructor
public class GuardController {

    private final GuardService guardService;

    // ---------- 规则 ----------

    @GetMapping("/rules")
    public Result<Page<GuardRule>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String direction,
                                        @RequestParam(required = false) String matchType,
                                        @RequestParam(required = false) String action,
                                        @RequestParam(required = false) Integer enabled,
                                        @RequestParam(required = false) String keyword) {
        return Result.ok(guardService.page(page, size, direction, matchType, action, enabled, keyword));
    }

    @PostMapping("/rules")
    public Result<GuardRule> create(@RequestBody GuardRule rule) {
        return Result.ok(guardService.create(rule));
    }

    @PutMapping("/rules/{id}")
    public Result<GuardRule> update(@PathVariable Long id, @RequestBody GuardRule rule) {
        return Result.ok(guardService.update(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        guardService.delete(id);
        return Result.ok();
    }

    /** 命中测试：{ruleIds: [], text}，ruleIds 空表示使用全部启用规则 */
    @PostMapping("/rules/test")
    public Result<Map<String, Object>> test(@RequestBody Map<String, Object> body) {
        String text = body.get("text") == null ? null : String.valueOf(body.get("text"));
        List<Long> ids = parseIds(body.get("ruleIds"));
        return Result.ok(guardService.testHit(ids, text));
    }

    // ---------- 应用绑定 ----------

    @GetMapping("/binds")
    public Result<List<GuardAppBindVO>> binds() {
        return Result.ok(guardService.bindList());
    }

    @PutMapping("/binds/{appId}")
    public Result<GuardAppBind> saveBind(@PathVariable Long appId, @RequestBody Map<String, Object> body) {
        String mode = body.get("mode") == null ? null : String.valueOf(body.get("mode"));
        Integer enabled = body.get("enabled") == null ? null : Integer.valueOf(String.valueOf(body.get("enabled")));
        return Result.ok(guardService.saveBind(appId, parseIds(body.get("ruleIds")), mode, enabled));
    }

    @DeleteMapping("/binds/{appId}")
    public Result<Void> deleteBind(@PathVariable Long appId) {
        guardService.deleteBind(appId);
        return Result.ok();
    }

    private List<Long> parseIds(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof List<?> list) {
            List<Long> out = new java.util.ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(Long.valueOf(String.valueOf(o)));
                }
            }
            return out;
        }
        return null;
    }
}
