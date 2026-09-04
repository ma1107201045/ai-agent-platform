package com.agent.platform.dao.entity.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警事件（规则触发的记录）
 */
@Data
@TableName("ops_alert_event")
public class OpsAlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long ruleId;

    /** 冗余规则名（规则删除后仍可追溯） */
    private String ruleName;

    private String metric;

    /** 级别：warning / critical */
    private String level;

    /** 触发描述 */
    private String content;

    /** 状态：open 未处理 / handled 已处理 / ignored 已忽略 */
    private String status;

    /** 来源：manual 手动测试 / auto 自动触发 */
    private String source;

    private LocalDateTime triggerTime;

    private LocalDateTime handledTime;
}
