package com.agent.platform.controller.chat;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.result.Result;
import com.agent.platform.dao.vo.chat.ChatUsageSummaryVO;
import com.agent.platform.service.chat.ChatUsageStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 用量统计接口（运营侧）：
 * 按日 / 按应用 / 按模型统计模型调用次数与输入输出 Token，按模型单价估算成本。
 */
@RestController
@RequestMapping("/api/chat/stats")
@RequiredArgsConstructor
public class ChatUsageStatsController {

    private static final int MAX_SPAN_DAYS = 365;

    private final ChatUsageStatsService usageStatsService;

    /**
     * 区间用量总览：汇总指标 + 按日趋势 + 应用/模型排行。
     *
     * @param appId     应用过滤（空 = 全部）
     * @param startDate 起始日期 yyyy-MM-dd（空 = 最近 7 天）
     * @param endDate   结束日期 yyyy-MM-dd（空 = 今天）
     */
    @GetMapping("/usage")
    public Result<ChatUsageSummaryVO> usage(@RequestParam(required = false) Long appId,
                                      @RequestParam(required = false) String startDate,
                                      @RequestParam(required = false) String endDate) {
        LocalDate from = parseDate(startDate, false);
        LocalDate to = parseDate(endDate, true);
        if (from != null && to != null && !from.isAfter(to)
                && from.plusDays(MAX_SPAN_DAYS - 1L).isBefore(to)) {
            throw new BizException("查询区间不能超过 " + MAX_SPAN_DAYS + " 天");
        }
        return Result.ok(usageStatsService.summary(appId, from, to));
    }

    private LocalDate parseDate(String value, boolean end) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("日期格式需为 yyyy-MM-dd: " + value);
        }
    }
}
