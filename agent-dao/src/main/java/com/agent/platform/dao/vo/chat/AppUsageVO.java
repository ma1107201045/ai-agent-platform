package com.agent.platform.dao.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用量应用维度排行项
 */
@Data
@AllArgsConstructor
public class AppUsageVO {
    private Long appId;
    private String appName;
    private long conversations;
    private long calls;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private double cost;
}
