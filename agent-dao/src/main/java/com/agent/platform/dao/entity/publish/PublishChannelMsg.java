package com.agent.platform.dao.entity.publish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道消息记录
 */
@Data
@TableName("publish_channel_msg")
public class PublishChannelMsg {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 渠道ID */
    private Long channelId;

    /** 应用ID */
    private Long appId;

    /** 方向: inbound入站/outbound出站 */
    private String direction;

    /** 事件类型（如 message/event） */
    private String eventType;

    /** 来源用户标识（openid 等） */
    private String fromUser;

    /** 消息内容 */
    private String content;

    /** 回复内容 */
    private String reply;

    /** 处理状态: success/failed/skipped */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    private LocalDateTime createTime;
}
