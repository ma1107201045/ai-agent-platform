package com.agent.platform.dao.dto.chat;

import lombok.Data;

/**
 * 发送消息入参（非流式 / SSE 流式共用）
 */
@Data
public class ChatConversationSendDTO {
    private String content;
    private Long modelId;
}
