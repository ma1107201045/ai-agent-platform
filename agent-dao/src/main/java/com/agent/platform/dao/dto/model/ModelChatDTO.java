package com.agent.platform.dao.dto.model;

import com.agent.platform.llm.model.ChatMessage;
import lombok.Data;

import java.util.List;

/**
 * 模型对话入参（非流式 / SSE 流式共用）
 */
@Data
public class ModelChatDTO {
    private Long modelId;
    /** 可选，覆盖默认模型 */
    private String model;
    private String systemPrompt;
    private String prompt;
    /** 可选，多轮历史 */
    private List<ChatMessage> messages;
    private Double temperature;
    private Integer maxTokens;
}
