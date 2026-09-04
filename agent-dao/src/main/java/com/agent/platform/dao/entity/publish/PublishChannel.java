package com.agent.platform.dao.entity.publish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布渠道：将智能体接入微信/飞书/钉钉/Web/Webhook 等终端
 */
@Data
@TableName("publish_channel")
public class PublishChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 绑定应用ID（app_agent.id） */
    private Long appId;

    /** 渠道名称 */
    private String name;

    /** 类型: wechat_mp公众号/feishu飞书/dingtalk钉钉/web网页/webhook */
    private String channelType;

    /** 渠道配置（JSON，含凭证/校验token等） */
    private String configJson;

    /** 描述 */
    private String description;

    /** 启用: 0停用 1启用 */
    private Integer enabled;

    /** 累计消息数 */
    private Long msgCount;

    /** 最近消息时间 */
    private LocalDateTime lastMsgAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
