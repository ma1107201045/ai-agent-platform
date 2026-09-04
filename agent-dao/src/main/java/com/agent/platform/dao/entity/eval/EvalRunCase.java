package com.agent.platform.dao.entity.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测任务用例明细
 */
@Data
@TableName("eval_run_case")
public class EvalRunCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long sampleId;

    private String question;

    private String reference;

    /** 应用/模型回答 */
    private String answer;

    /** 是否通过: 0否 1是 */
    private Integer passed;

    /** 得分(0-1) */
    private BigDecimal score;

    /** 耗时(毫秒) */
    private Integer latencyMs;

    private String error;

    private LocalDateTime createTime;
}
