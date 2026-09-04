package com.agent.platform.dao.entity.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测任务
 */
@Data
@TableName("eval_run")
public class EvalRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 所属对比实验ID(可为空) */
    private Long experimentId;

    private String name;

    private Long datasetId;

    /** 被测应用ID */
    private Long appId;

    /** 被测应用版本ID */
    private Long appVersionId;

    /** 被测模型ID(直连模型时) */
    private Long modelId;

    /** pending/running/success/failed/stopped */
    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    /** 通过率(0-1) */
    private BigDecimal passRate;

    /** 平均得分(0-1) */
    private BigDecimal avgScore;

    /** 报告(JSON) */
    private String reportJson;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String error;

    private Long createdBy;

    private LocalDateTime createTime;
}
