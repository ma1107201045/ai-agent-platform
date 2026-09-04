package com.agent.platform.dao.vo.eval;

import com.agent.platform.dao.entity.eval.EvalRun;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评测任务视图（附目标/数据集名称）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvalRunVO extends EvalRun {

    private String datasetName;

    /** 被测应用名称（app 模式） */
    private String appName;

    /** 被测应用类型 */
    private String appType;

    /** 被测模型名称（model 模式） */
    private String modelName;
}
