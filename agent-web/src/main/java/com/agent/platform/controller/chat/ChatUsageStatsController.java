package com.agent.platform.controller.chat;

import com.agent.platform.common.result.Result;
import com.agent.platform.service.chat.ChatUsageStatsService;
import com.agent.platform.service.chat.ChatUsageStatsService.UsageOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用量统计接口（运营侧）：
 * 按日/按应用统计模型调用次数与 Token 消耗，支撑成本掌控。
 */
@RestController
@RequestMapping("/api/chat/stats")
@RequiredArgsConstructor
public class ChatUsageStatsController {

    private final ChatUsageStatsService usageStatsService;

    /** 最近 N 天用量总览 */
    @GetMapping("/usage")
    public Result<UsageOverview> usage(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(usageStatsService.usage(days));
    }
}
