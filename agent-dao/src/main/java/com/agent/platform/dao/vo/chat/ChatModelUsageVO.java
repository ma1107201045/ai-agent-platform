package com.agent.platform.dao.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用量模型维度排行项
 */
@Data
@AllArgsConstructor
public class ChatModelUsageVO {
    private Long modelId;
    private String modelName;
    private long calls;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private double cost;
}
