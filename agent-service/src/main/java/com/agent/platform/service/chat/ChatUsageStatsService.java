package com.agent.platform.service.chat;

import com.agent.platform.dao.vo.chat.ChatUsageSummaryVO;
import com.agent.platform.llm.model.Usage;
import java.time.LocalDate;

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
public interface ChatUsageStatsService {

    /**
     * 记录一次模型调用用量事件（调用成功且拿到 usage 时写入）。
     *
     * @param channel console 控制台会话 / public 公开 API
     * @param mode    direct / agent / workflow
     */
    public void recordUsage(Long tenantId, Long appId, Long conversationId, Long userId,
                            Long modelId, String channel, String mode, Usage usage);

    /**
     * 区间用量总览：汇总指标 + 按日趋势（连续日期补 0）+ 应用/模型双维度排行。
     *
     * @param appId 应用过滤（null = 全部应用）
     * @param from  起始日期（null 默认今天往前 6 天）
     * @param to    结束日期（null 默认今天）
     */
    public ChatUsageSummaryVO summary(Long appId, LocalDate from, LocalDate to);
}
