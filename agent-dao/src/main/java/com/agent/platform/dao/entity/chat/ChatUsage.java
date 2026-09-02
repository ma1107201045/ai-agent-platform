package com.agent.platform.dao.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型用量事件：控制台会话 / 公开 API 每次模型调用记一条
 */
@Data
@TableName("chat_usage")
public class ChatUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 应用ID */
    private Long appId;

    /** 会话ID（公开调用为空） */
    private Long conversationId;

    /** 用户ID（公开调用为空） */
    private Long userId;

    /** 模型ID */
    private Long modelId;

    /** 来源：console 控制台会话 / public 公开API */
    private String channel;

    /** 会话模式：direct / agent / workflow */
    private String mode;

    /** 输入 Token */
    private Long promptTokens;

    /** 输出 Token */
    private Long completionTokens;

    /** 总 Token（输入+输出） */
    private Long totalTokens;

    /** 估算成本（元），按模型单价计算 */
    private BigDecimal cost;

    /** 调用时间 */
    private LocalDateTime createTime;
}
