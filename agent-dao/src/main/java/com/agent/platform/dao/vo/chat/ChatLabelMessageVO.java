package com.agent.platform.dao.vo.chat;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话标注-待标注消息展示模型
 */
@Data
public class ChatLabelMessageVO {

    /** 消息ID(chat_message.id) */
    private Long messageId;

    /** 会话ID */
    private Long conversationId;

    /** 会话标题 */
    private String conversationTitle;

    /** 应用ID（直连会话为0） */
    private Long appId;

    /** 应用名称 */
    private String appName;

    /** 消息内容 */
    private String content;

    /** 是否助手消息（固定 true，仅助手消息可标注） */
    private Boolean assistant;

    private LocalDateTime createTime;

    /** 是否有反馈 */
    private Boolean labeled;

    // ---------- 反馈信息 ----------
    private Long feedbackId;
    private String rating;
    private String labelType;
    private String correctedAnswer;
    private String note;
    private String createdByName;
    private LocalDateTime feedbackTime;
}
