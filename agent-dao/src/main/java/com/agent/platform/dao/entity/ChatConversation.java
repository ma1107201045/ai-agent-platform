package com.agent.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话
 */
@Data
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 创建用户ID */
    private Long userId;

    /** 应用ID */
    private Long appId;

    /** 会话标题 */
    private String title;

    /** 对话模式：direct直连模型 / workflow运行工作流 */
    private String mode;

    /** 使用的模型ID（direct 模式） */
    private Long modelId;

    /** 状态：0删除 1正常 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
