package com.agent.platform.dao.dto.chat;

import lombok.Data;

/**
 * 创建聊天会话入参
 */
@Data
public class ChatConversationCreateDTO {
    private Long appId;
    private String title;
    /** direct / agent / workflow */
    private String mode;
    private Long modelId;
}
