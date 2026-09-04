package com.agent.platform.controller.ops;

import com.agent.platform.common.result.Result;
import com.agent.platform.service.ops.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 费用账单接口
 *
 * <p>URL：/api/ops/billing</p>
 */
@RestController
@RequestMapping("/api/ops/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    /** 月度汇总（含预算与超支提醒） */
    @GetMapping
    public Result<Map<String, Object>> summary(@RequestParam(required = false) String month) {
        return Result.ok(billingService.summary(month));
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(required = false) String month) {
        return Result.ok(billingService.dailyTrend(month));
    }

    @GetMapping("/by-app")
    public Result<List<Map<String, Object>>> byApp(@RequestParam(required = false) String month) {
        return Result.ok(billingService.byApp(month));
    }

    @GetMapping("/by-model")
    public Result<List<Map<String, Object>>> byModel(@RequestParam(required = false) String month) {
        return Result.ok(billingService.byModel(month));
    }

    /** 保存预算 {month,budget,notifyEnabled} */
    @PutMapping("/budget")
    public Result<Void> setBudget(@RequestBody Map<String, Object> body) {
        BigDecimal budget = body.get("budget") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(body.get("budget")));
        Integer notify = body.get("notifyEnabled") == null ? 0 : (Integer) body.get("notifyEnabled");
        billingService.setBudget(body.get("month") == null ? null : String.valueOf(body.get("month")), budget, notify);
        return Result.ok();
    }
}
