package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用定时任务配置
 */
@Data
@TableName("app_schedule")
public class AppSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 任务名称 */
    private String name;

    /** 关联应用ID */
    private Long appId;

    /** 应用名称快照 */
    private String appName;

    /** 触发类型：interval间隔 / daily每天 / weekly每周 */
    private String triggerType;

    /** 触发间隔(分钟)，interval 生效 */
    private Integer intervalMinutes;

    /** 触发时刻 HH:mm，daily/weekly 生效 */
    private String runTime;

    /** 周几 1-7（周一~周日），weekly 生效 */
    private Integer runWeekday;

    /** 触发时发送给应用的输入 */
    private String inputMessage;

    /** 是否启用：0停用 1启用 */
    private Integer enabled;

    /** 最近执行时间 */
    private LocalDateTime lastRunTime;

    /** 下次执行时间 */
    private LocalDateTime nextRunTime;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
