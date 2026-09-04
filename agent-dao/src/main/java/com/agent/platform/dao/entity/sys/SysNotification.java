package com.agent.platform.dao.entity.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知
 */
@Data
@TableName("sys_notification")
public class SysNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 接收用户ID */
    private Long userId;

    /** 类型：system系统 / announcement公告 / run任务 / alert告警 */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 业务类型（如 announcement、schedule、alert） */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 是否已读：0未读 1已读（read 为 MySQL 保留字，需转义） */
    @TableField("`read`")
    private Integer read;

    /** 阅读时间 */
    private LocalDateTime readTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
