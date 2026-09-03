package com.agent.platform.dao.dto.app;

import com.agent.platform.llm.model.ChatMessage;
import lombok.Data;

import java.util.List;

/**
 * Agent 自主对话（规划-工具调用-观察）入参
 */
@Data
public class AppAgentChatDTO {
    /** 对话模型 ID（必填） */
    private Long modelId;
    /** 系统提示词（可空） */
    private String systemPrompt;
    /** 对话历史消息 */
    private List<ChatMessage> messages;
    /** 最大循环轮数（可空） */
    private Integer maxIterations;
}
