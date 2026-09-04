package com.agent.platform.dao.entity.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警规则
 */
@Data
@TableName("ops_alert_rule")
public class OpsAlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 规则名称 */
    private String name;

    /** 指标：error_rate 错误率 / failures 运行失败数 / latency 平均延迟 / cost 成本 */
    private String metric;

    /** 比较符，默认 >= */
    private String operator;

    /** 阈值 */
    private BigDecimal threshold;

    /** 统计窗口(分钟)：5/60/1440 */
    private Integer windowMinutes;

    /** 级别：warning / critical */
    private String level;

    /** 通知渠道：notification,email,webhook（逗号分隔） */
    private String channels;

    /** Webhook 地址 */
    private String webhookUrl;

    /** 是否启用：0否 1是 */
    private Integer enabled;

    private String remark;

    /** 最近触发时间 */
    private LocalDateTime lastFireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
