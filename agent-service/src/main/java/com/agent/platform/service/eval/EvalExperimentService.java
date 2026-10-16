package com.agent.platform.service.eval;

import com.agent.platform.dao.entity.eval.EvalExperiment;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 评测对比实验服务
 */
public interface EvalExperimentService {

    public Page<EvalExperiment> page(long page, long size, String keyword, Integer status);

    public EvalExperiment get(Long id);

    /** 校验数据集存在且启用 */
    public void requireDataset(Long datasetId);

    public EvalExperiment create(EvalExperiment exp);

    public EvalExperiment update(Long id, EvalExperiment req);

    public void delete(Long id);
}
