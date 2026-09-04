package com.agent.platform.controller.ops;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.ops.OpsAlertEvent;
import com.agent.platform.dao.entity.ops.OpsAlertRule;
import com.agent.platform.service.ops.OpsAlertService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 告警管理接口
 *
 * <p>URL：/api/ops/alerts（规则与事件）</p>
 */
@RestController
@RequestMapping("/api/ops/alerts")
@RequiredArgsConstructor
public class OpsAlertController {

    private final OpsAlertService alertService;

    /* ---------------- 规则 ---------------- */

    @GetMapping("/rules")
    public Result<Page<OpsAlertRule>> rules(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String keyword) {
        return Result.ok(alertService.rulePage(page, size, keyword));
    }

    @PostMapping("/rules")
    public Result<OpsAlertRule> createRule(@RequestBody OpsAlertRule rule) {
        return Result.ok(alertService.createRule(rule));
    }

    @PutMapping("/rules/{id}")
    public Result<OpsAlertRule> updateRule(@PathVariable Long id, @RequestBody OpsAlertRule rule) {
        return Result.ok(alertService.updateRule(id, rule));
    }

    /** 启停切换 {enabled: 0/1} */
    @PutMapping("/rules/{id}/enabled")
    public Result<Void> toggleRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object enabled = body.get("enabled");
        alertService.toggleRule(id, enabled == null ? 0 : ((Number) enabled).intValue());
        return Result.ok();
    }

    @DeleteMapping("/rules/{id}")
    public Result<Void> removeRule(@PathVariable Long id) {
        alertService.removeRule(id);
        return Result.ok();
    }

    /* ---------------- 事件 ---------------- */

    @GetMapping("/events")
    public Result<Page<OpsAlertEvent>> events(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long ruleId,
                                              @RequestParam(required = false) String keyword) {
        return Result.ok(alertService.eventPage(page, size, status, ruleId, keyword));
    }

    @GetMapping("/events/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(alertService.eventStats());
    }

    /** 手动触发一次测试事件 */
    @PostMapping("/events/test")
    public Result<OpsAlertEvent> fireTest(@RequestBody Map<String, Object> body) {
        Object ruleId = body.get("ruleId");
        return Result.ok(alertService.fireTest(((Number) ruleId).longValue()));
    }

    /** 标记处理/忽略 {status: handled/ignored/open} */
    @PutMapping("/events/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        alertService.updateEventStatus(id, String.valueOf(body.get("status")));
        return Result.ok();
    }

    @DeleteMapping("/events/{id}")
    public Result<Void> removeEvent(@PathVariable Long id) {
        alertService.removeEvent(id);
        return Result.ok();
    }
}
