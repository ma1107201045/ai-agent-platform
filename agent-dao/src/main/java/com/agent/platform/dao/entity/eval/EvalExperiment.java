package com.agent.platform.dao.entity.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测对比实验
 */
@Data
@TableName("eval_experiment")
public class EvalExperiment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    /** 共用评测数据集ID */
    private Long datasetId;

    /** 0停用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
