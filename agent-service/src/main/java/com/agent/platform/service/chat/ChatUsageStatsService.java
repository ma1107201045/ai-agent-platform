package com.agent.platform.service.chat;

import com.agent.platform.dao.mapper.chat.ChatMessageMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量统计服务：基于 chat_message 聚合调用次数与 Token 消耗。
 *
 * <p>口径说明：
 * <ul>
 *   <li>一条 assistant 消息计为一次模型调用（calls）；消息中的 tokens 为该次调用的 Token 总量；</li>
 *   <li>direct 模式、Agent（ReAct）模式的 Token 均已在消息落库时写入；</li>
 *   <li>工作流模式的 Token 待引擎汇总支持后计入，当前该部分调用 token 记为 0。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ChatUsageStatsService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_DAYS = 365;

    private final ChatMessageMapper messageMapper;

    /** 最近 N 天（含今天）的用量总览：指标汇总 + 按日趋势 + 按应用排行 */
    public UsageOverview usage(int days) {
        if (days <= 0 || days > MAX_DAYS) {
            days = 30;
        }
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(days - 1L).atStartOfDay();

        List<AppUsage> apps = buildByApp(messageMapper.usageByApp(start));
        List<DailyUsage> daily = buildDaily(messageMapper.usageDaily(start), today.minusDays(days - 1L), today);

        long conversations = 0;
        long calls = 0;
        long tokens = 0;
        for (AppUsage app : apps) {
            conversations += app.getConversations();
            calls += app.getCalls();
            tokens += app.getTokens();
        }
        return new UsageOverview(conversations, calls, tokens, daily, apps);
    }

    private List<AppUsage> buildByApp(List<Map<String, Object>> rows) {
        List<AppUsage> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object appId = r.get("app_id");
            String appName = r.get("app_name") == null ? "未命名应用" : r.get("app_name").toString();
            result.add(new AppUsage(
                    appId == null ? null : ((Number) appId).longValue(),
                    appName,
                    num(r.get("conv_cnt")),
                    num(r.get("call_cnt")),
                    num(r.get("token_cnt"))));
        }
        return result;
    }

    /** 补全无数据日期为 0，保证趋势图横轴连续 */
    private List<DailyUsage> buildDaily(List<Map<String, Object>> rows, LocalDate from, LocalDate to) {
        Map<String, long[]> indexed = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String day = r.get("day_date") == null ? "" : r.get("day_date").toString();
            indexed.put(day, new long[]{num(r.get("call_cnt")), num(r.get("token_cnt"))});
        }
        List<DailyUsage> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.format(DAY_FMT);
            long[] v = indexed.get(key);
            result.add(new DailyUsage(key, v == null ? 0 : v[0], v == null ? 0 : v[1]));
        }
        return result;
    }

    private long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    // ---------- 返回 DTO ----------

    @Data
    @AllArgsConstructor
    public static class UsageOverview {
        /** 会话数 */
        private long conversations;
        /** 调用次数（assistant 消息数） */
        private long calls;
        /** Token 总量 */
        private long tokens;
        /** 按日趋势（连续日期，空值补 0） */
        private List<DailyUsage> daily;
        /** 按应用排行（Token 降序） */
        private List<AppUsage> apps;
    }

    @Data
    @AllArgsConstructor
    public static class DailyUsage {
        private String date;
        private long calls;
        private long tokens;
    }

    @Data
    @AllArgsConstructor
    public static class AppUsage {
        private Long appId;
        private String appName;
        private long conversations;
        private long calls;
        private long tokens;
    }
}
