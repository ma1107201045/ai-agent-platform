package com.agent.platform.service.eval;

import com.agent.platform.dao.entity.eval.EvalExperiment;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 评测对比实验服务
 */
public interface EvalExperimentService {

    Page<EvalExperiment> page(long page, long size, String keyword, Integer status);

    EvalExperiment get(Long id);

    /** 校验数据集存在且启用 */
    void requireDataset(Long datasetId);

    EvalExperiment create(EvalExperiment exp);

    EvalExperiment update(Long id, EvalExperiment req);

    void delete(Long id);
}
