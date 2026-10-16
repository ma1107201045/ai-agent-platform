package com.agent.platform.service.eval;

import com.agent.platform.dao.entity.eval.EvalDataset;
import com.agent.platform.dao.entity.eval.EvalSample;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

/**
 * 评测数据集服务
 */
public interface EvalDatasetService {

    public Page<EvalDataset> page(long page, long size, String keyword, Integer status);

    /** 启用的数据集（下拉选项） */
    public List<EvalDataset> options();

    public EvalDataset get(Long id);

    public EvalDataset create(EvalDataset dataset);

    public EvalDataset update(Long id, EvalDataset req);

    public void delete(Long id);

    public Page<EvalSample> samples(long page, long size, Long datasetId, String keyword);

    public EvalSample addSample(Long datasetId, EvalSample sample);

    public EvalSample updateSample(Long datasetId, Long sampleId, EvalSample req);

    public void deleteSample(Long datasetId, Long sampleId);

    /**
     * 批量导入样本：支持两种格式
     * <ul>
     *   <li>JSON 数组 / 每行一个 JSON 对象：[{question, reference, category}]</li>
     *   <li>每行一条样本，用制表符分隔：提问\t参考答案</li>
     * </ul>
     */
    public int importSamples(Long datasetId, String text);
}
