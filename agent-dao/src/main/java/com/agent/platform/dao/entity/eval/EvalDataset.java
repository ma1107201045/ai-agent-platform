package com.agent.platform.dao.entity.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测数据集
 */
@Data
@TableName("eval_dataset")
public class EvalDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    /** manual手动 / import导入 / feedback对话标注回流 */
    private String source;

    private Integer sampleCount;

    /** 0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
