package com.agent.platform.dao.dto.app;

import com.agent.platform.llm.model.ChatMessage;
import lombok.Data;

import java.util.List;

/**
 * 运行应用工作流入参（按画布 DSL 执行）
 */
@Data
public class AppAgentRunDTO {
    /** 对话消息列表（最后一条 user 消息作为本次输入） */
    private List<ChatMessage> messages;
}
