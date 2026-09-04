package com.agent.platform.dao.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话标注-消息反馈
 */
@Data
@TableName("chat_message_feedback")
public class ChatMessageFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 被标注消息ID(chat_message.id) */
    private Long messageId;

    /** 会话ID */
    private Long conversationId;

    /** 应用ID（直连会话时为0） */
    private Long appId;

    /** 会话所属用户ID */
    private Long userId;

    /** 评分: good好/bad差 */
    private String rating;

    /** 标签: correct准确/incorrect错误/hallucination幻觉/off_topic跑题/vague含糊 */
    private String labelType;

    /** 补充参考答案 */
    private String correctedAnswer;

    /** 标注说明 */
    private String note;

    /** 标注人ID */
    private Long createdBy;

    private LocalDateTime createTime;
}
