package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务执行记录
 */
@Data
@TableName("app_schedule_log")
public class AppScheduleLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 任务ID */
    private Long scheduleId;

    /** 任务名称快照 */
    private String scheduleName;

    /** 应用ID */
    private Long appId;

    /** 应用名称快照 */
    private String appName;

    /** 触发方式：scheduled自动 / manual手动 */
    private String triggerBy;

    /** 执行结果：success成功 / failed失败 */
    private String status;

    /** 结果摘要 / 错误信息 */
    private String message;

    /** 耗时(毫秒) */
    private Integer costMs;

    private LocalDateTime createTime;
}
