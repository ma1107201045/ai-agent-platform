package com.agent.platform.service.chat;

import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.chat.ChatUsage;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.chat.ChatUsageMapper;
import com.agent.platform.dao.mapper.model.ModelInfoMapper;
import com.agent.platform.dao.vo.chat.AppUsageVO;
import com.agent.platform.dao.vo.chat.ModelUsageVO;
import com.agent.platform.dao.vo.chat.TrendPointVO;
import com.agent.platform.dao.vo.chat.UsageSummaryVO;
import com.agent.platform.llm.model.Usage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量统计服务：基于 {@code chat_usage} 用量事件表聚合模型调用次数与 Token 消耗。
 *
 * <p>口径说明：
 * <ul>
 *   <li>每次模型调用（控制台会话 console / 公开 API public）落一条用量事件；</li>
 *   <li>输入/输出/总 Token 按事件落库，可精确到模型维度；</li>
 *   <li>成本按模型官方单价（元/百万 Token）估算，未知模型按 0 计。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ChatUsageStatsService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DATE_SQL = "DATE_FORMAT(create_time, '%Y-%m-%d')";

    /** 参考单价：元 / 百万 Token（输入, 输出）。仅覆盖内置已知模型，未知模型成本按 0 计 */
    private static final Map<String, double[]> MODEL_PRICE = new HashMap<>(Map.of(
            "deepseek-chat", new double[]{1.0, 2.0},
            "deepseek-reasoner", new double[]{4.0, 16.0}));

    private final ChatUsageMapper usageMapper;
    private final AppAgentMapper appAgentMapper;
    private final ModelInfoMapper modelInfoMapper;

    // ==================== 写入 ====================

    /**
     * 记录一次模型调用用量事件（调用成功且拿到 usage 时写入）。
     *
     * @param channel console 控制台会话 / public 公开 API
     * @param mode    direct / agent / workflow
     */
    public void recordUsage(Long tenantId, Long appId, Long conversationId, Long userId,
                            Long modelId, String channel, String mode, Usage usage) {
        if (usage == null || appId == null) {
            return;
        }
        ChatUsage u = new ChatUsage();
        u.setTenantId(tenantId == null ? 1L : tenantId);
        u.setAppId(appId);
        u.setConversationId(conversationId);
        u.setUserId(userId);
        u.setModelId(modelId);
        u.setChannel(channel == null || channel.isBlank() ? "console" : channel);
        u.setMode(mode);
        u.setPromptTokens(usage.promptTokens());
        u.setCompletionTokens(usage.completionTokens());
        u.setTotalTokens(usage.totalTokens());
        u.setCost(estimateCost(modelId, usage));
        u.setCreateTime(LocalDateTime.now());
        usageMapper.insert(u);
    }

    /** 按模型单价估算一次调用成本（元） */
    private BigDecimal estimateCost(Long modelId, Usage usage) {
        double[] price = priceOf(modelId);
        double cost = usage.promptTokens() / 1_000_000.0 * price[0]
                + usage.completionTokens() / 1_000_000.0 * price[1];
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }

    private double[] priceOf(Long modelId) {
        if (modelId == null) {
            return new double[]{0, 0};
        }
        ModelInfo model = modelInfoMapper.selectById(modelId);
        if (model == null || model.getName() == null) {
            return new double[]{0, 0};
        }
        return MODEL_PRICE.getOrDefault(model.getName(), new double[]{0, 0});
    }

    // ==================== 聚合 ====================

    /**
     * 区间用量总览：汇总指标 + 按日趋势（连续日期补 0）+ 应用/模型双维度排行。
     *
     * @param appId 应用过滤（null = 全部应用）
     * @param from  起始日期（null 默认今天往前 6 天）
     * @param to    结束日期（null 默认今天）
     */
    public UsageSummaryVO summary(Long appId, LocalDate from, LocalDate to) {
        if (from == null) {
            from = LocalDate.now().minusDays(6);
        }
        if (to == null) {
            to = LocalDate.now();
        }
        if (from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        List<AppUsageVO> apps = queryByApp(appId, from, to);
        List<ModelUsageVO> models = queryByModel(appId, from, to);
        long conversations = 0;
        long calls = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        long totalTokens = 0;
        double cost = 0;
        for (AppUsageVO app : apps) {
            conversations += app.getConversations();
            calls += app.getCalls();
            inputTokens += app.getInputTokens();
            outputTokens += app.getOutputTokens();
            totalTokens += app.getTotalTokens();
            cost += app.getCost();
        }
        return new UsageSummaryVO(conversations, calls, inputTokens, outputTokens, totalTokens, cost,
                from.format(DAY_FMT), to.format(DAY_FMT),
                queryTrend(appId, from, to), apps, models);
    }

    /** 按日趋势：日期连续补 0 */
    private List<TrendPointVO> queryTrend(Long appId, LocalDate from, LocalDate to) {
        QueryWrapper<ChatUsage> qw = rangeFilter(appId, from, to);
        qw.select(DATE_SQL + " AS day_date",
                        "COUNT(*) AS call_cnt",
                        "COALESCE(SUM(prompt_tokens), 0) AS input_tokens",
                        "COALESCE(SUM(completion_tokens), 0) AS output_tokens",
                        "COALESCE(SUM(total_tokens), 0) AS total_tokens")
                .groupBy(DATE_SQL)
                .orderByAsc("day_date");
        Map<String, TrendPointVO> indexed = new LinkedHashMap<>();
        for (Map<String, Object> r : usageMapper.selectMaps(qw)) {
            indexed.put(str(r.get("day_date")), new TrendPointVO(
                    str(r.get("day_date")),
                    toLong(r.get("call_cnt")),
                    toLong(r.get("input_tokens")),
                    toLong(r.get("output_tokens")),
                    toLong(r.get("total_tokens"))));
        }
        List<TrendPointVO> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.format(DAY_FMT);
            TrendPointVO point = indexed.get(key);
            if (point == null) {
                point = new TrendPointVO(key, 0, 0, 0, 0);
            }
            result.add(point);
        }
        return result;
    }

    /** 按应用排行（Token 降序） */
    private List<AppUsageVO> queryByApp(Long appId, LocalDate from, LocalDate to) {
        QueryWrapper<ChatUsage> qw = rangeFilter(appId, from, to);
        qw.select("app_id",
                        "COUNT(*) AS call_cnt",
                        "COUNT(DISTINCT conversation_id) AS conv_cnt",
                        "COALESCE(SUM(prompt_tokens), 0) AS input_tokens",
                        "COALESCE(SUM(completion_tokens), 0) AS output_tokens",
                        "COALESCE(SUM(total_tokens), 0) AS total_tokens",
                        "COALESCE(SUM(cost), 0) AS cost")
                .groupBy("app_id");
        List<Map<String, Object>> rows = usageMapper.selectMaps(qw);
        Map<Long, String> names = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long id = toLong(r.get("app_id"));
            if (id != null && id > 0) {
                AppAgent app = appAgentMapper.selectById(id);
                names.put(id, app == null ? null : app.getName());
            }
        }
        List<AppUsageVO> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long id = toLong(r.get("app_id"));
            String name = names.getOrDefault(id, null);
            result.add(new AppUsageVO(id, name == null || name.isBlank() ? "应用 #" + id : name,
                    toLong(r.get("conv_cnt")), toLong(r.get("call_cnt")),
                    toLong(r.get("input_tokens")), toLong(r.get("output_tokens")),
                    toLong(r.get("total_tokens")), toDouble(r.get("cost"))));
        }
        result.sort(Comparator.comparingLong(AppUsageVO::getTotalTokens).reversed());
        return result;
    }

    /** 按模型排行（Token 降序） */
    private List<ModelUsageVO> queryByModel(Long appId, LocalDate from, LocalDate to) {
        QueryWrapper<ChatUsage> qw = rangeFilter(appId, from, to);
        qw.select("model_id",
                        "COUNT(*) AS call_cnt",
                        "COALESCE(SUM(prompt_tokens), 0) AS input_tokens",
                        "COALESCE(SUM(completion_tokens), 0) AS output_tokens",
                        "COALESCE(SUM(total_tokens), 0) AS total_tokens",
                        "COALESCE(SUM(cost), 0) AS cost")
                .groupBy("model_id");
        List<Map<String, Object>> rows = usageMapper.selectMaps(qw);
        Map<Long, String> names = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long id = toLong(r.get("model_id"));
            if (id != null && id > 0) {
                ModelInfo model = modelInfoMapper.selectById(id);
                names.put(id, model == null ? null : model.getName());
            }
        }
        List<ModelUsageVO> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long id = toLong(r.get("model_id"));
            String name = id == null ? "未知模型" : names.getOrDefault(id, null);
            if (name == null || name.isBlank()) {
                name = "模型 #" + id;
            }
            result.add(new ModelUsageVO(id, name, toLong(r.get("call_cnt")),
                    toLong(r.get("input_tokens")), toLong(r.get("output_tokens")),
                    toLong(r.get("total_tokens")), toDouble(r.get("cost"))));
        }
        result.sort(Comparator.comparingLong(ModelUsageVO::getTotalTokens).reversed());
        return result;
    }

    /** 时间区间 + 应用过滤条件 */
    private QueryWrapper<ChatUsage> rangeFilter(Long appId, LocalDate from, LocalDate to) {
        QueryWrapper<ChatUsage> qw = new QueryWrapper<>();
        qw.ge("create_time", from.atStartOfDay());
        qw.lt("create_time", to.plusDays(1).atStartOfDay());
        if (appId != null) {
            qw.eq("app_id", appId);
        }
        return qw;
    }

    private long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private String str(Object v) {
        return v == null ? "" : v.toString();
    }

}
