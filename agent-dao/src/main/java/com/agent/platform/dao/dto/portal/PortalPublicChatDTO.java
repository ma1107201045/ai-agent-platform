package com.agent.platform.dao.dto.portal;

import com.agent.platform.llm.model.ChatMessage;
import lombok.Data;

import java.util.List;

/**
 * 公开对话入参（消息为完整对话历史，最后一条必须是 user 消息）
 */
@Data
public class PortalPublicChatDTO {
    private List<ChatMessage> messages;
}
