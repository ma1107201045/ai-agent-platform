package com.agent.platform.dao.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 区间用量总览：汇总指标 + 按日趋势 + 应用/模型双维度排行
 */
@Data
@AllArgsConstructor
public class ChatUsageSummaryVO {
    /** 会话数（仅控制台会话去重） */
    private long conversations;
    /** 模型调用次数（用量事件数） */
    private long calls;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    /** 估算成本（元） */
    private double cost;
    /** 查询起始日期 yyyy-MM-dd */
    private String startDate;
    /** 查询结束日期 yyyy-MM-dd */
    private String endDate;
    /** 按日趋势（连续日期，空值补 0） */
    private List<ChatTrendPointVO> trend;
    /** 应用维度排行 */
    private List<ChatAppUsageVO> apps;
    /** 模型维度排行 */
    private List<ChatModelUsageVO> models;
}
