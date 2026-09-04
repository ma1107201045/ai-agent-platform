package com.agent.platform.service.eval;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.eval.EvalDataset;
import com.agent.platform.dao.entity.eval.EvalExperiment;
import com.agent.platform.dao.entity.eval.EvalRun;
import com.agent.platform.dao.mapper.eval.EvalDatasetMapper;
import com.agent.platform.dao.mapper.eval.EvalExperimentMapper;
import com.agent.platform.dao.mapper.eval.EvalRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 评测对比实验服务
 */
@Service
@RequiredArgsConstructor
public class EvalExperimentService {

    private final EvalExperimentMapper experimentMapper;
    private final EvalRunMapper runMapper;
    private final EvalDatasetMapper datasetMapper;

    public Page<EvalExperiment> page(long page, long size, String keyword, Integer status) {
        return experimentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EvalExperiment>()
                        .eq(EvalExperiment::getTenantId, tenant())
                        .eq(status != null, EvalExperiment::getStatus, status)
                        .and(StringUtils.hasText(keyword),
                                w -> w.like(EvalExperiment::getName, keyword).or().like(EvalExperiment::getDescription, keyword))
                        .orderByDesc(EvalExperiment::getId));
    }

    public EvalExperiment get(Long id) {
        EvalExperiment exp = experimentMapper.selectById(id);
        if (exp == null) {
            throw new BizException("对比实验不存在: " + id);
        }
        return exp;
    }

    /** 校验数据集存在且启用 */
    public void requireDataset(Long datasetId) {
        EvalDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || dataset.getStatus() == null || dataset.getStatus() != 1) {
            throw new BizException("数据集不存在或已停用");
        }
    }

    public EvalExperiment create(EvalExperiment exp) {
        if (!StringUtils.hasText(exp.getName())) {
            throw new BizException("实验名称不能为空");
        }
        if (exp.getDatasetId() == null) {
            throw new BizException("请选择共用评测数据集");
        }
        requireDataset(exp.getDatasetId());
        exp.setId(null);
        exp.setTenantId(tenant());
        exp.setStatus(exp.getStatus() == null ? 1 : exp.getStatus());
        exp.setCreateTime(LocalDateTime.now());
        exp.setUpdateTime(LocalDateTime.now());
        experimentMapper.insert(exp);
        return exp;
    }

    public EvalExperiment update(Long id, EvalExperiment req) {
        EvalExperiment exp = get(id);
        if (StringUtils.hasText(req.getName())) {
            exp.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            exp.setDescription(req.getDescription());
        }
        if (req.getDatasetId() != null) {
            requireDataset(req.getDatasetId());
            exp.setDatasetId(req.getDatasetId());
        }
        if (req.getStatus() != null) {
            exp.setStatus(req.getStatus());
        }
        exp.setUpdateTime(LocalDateTime.now());
        experimentMapper.updateById(exp);
        return exp;
    }

    public void delete(Long id) {
        get(id);
        Long runCount = runMapper.selectCount(new LambdaQueryWrapper<EvalRun>().eq(EvalRun::getExperimentId, id));
        if (runCount != null && runCount > 0) {
            throw new BizException("实验下已有评测任务，请先删除这些任务");
        }
        experimentMapper.deleteById(id);
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
