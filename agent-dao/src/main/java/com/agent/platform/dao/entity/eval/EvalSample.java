package com.agent.platform.dao.entity.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测样本
 */
@Data
@TableName("eval_sample")
public class EvalSample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String question;

    /** 参考答案 */
    private String reference;

    private String category;

    /** 0停用 1启用 */
    private Integer status;

    private LocalDateTime createTime;
}
