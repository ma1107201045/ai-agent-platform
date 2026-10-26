package com.agent.platform.service.eval;

import com.agent.platform.dao.entity.eval.EvalRun;
import com.agent.platform.dao.vo.eval.EvalRunVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;

/**
 * 评测任务服务：创建任务后在线程池中顺序跑完数据集样本，
 * 自动生成通过率 / 平均得分 / 耗时报告，支持中断与重跑。
 */
public interface EvalRunService {

    Page<EvalRunVO> page(long page, long size, String status, Long datasetId, Long experimentId, String keyword);

    EvalRunVO get(Long id);

    /** 评测中心聚合统计 */
    Map<String, Object> stats();

    /**
     * 创建并立即运行评测任务。
     * body: name/datasetId/experimentId(可选)/(appId + appVersionId 可选 或 modelId)
     */
    EvalRunVO createAndRun(EvalRun req);

    /** 重跑已结束/失败的任务 */
    EvalRunVO rerun(Long id);

    /** 停止运行中的任务（worker 会在下个样本前退出） */
    void stop(Long id);

    void delete(Long id);
}
