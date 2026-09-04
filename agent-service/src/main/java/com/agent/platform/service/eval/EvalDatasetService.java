package com.agent.platform.service.eval;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.eval.EvalDataset;
import com.agent.platform.dao.entity.eval.EvalRun;
import com.agent.platform.dao.entity.eval.EvalSample;
import com.agent.platform.dao.mapper.eval.EvalDatasetMapper;
import com.agent.platform.dao.mapper.eval.EvalRunMapper;
import com.agent.platform.dao.mapper.eval.EvalSampleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评测数据集服务
 */
@Service
@RequiredArgsConstructor
public class EvalDatasetService {

    private final EvalDatasetMapper datasetMapper;
    private final EvalSampleMapper sampleMapper;
    private final EvalRunMapper runMapper;
    private final ObjectMapper objectMapper;

    public Page<EvalDataset> page(long page, long size, String keyword, Integer status) {
        LambdaQueryWrapper<EvalDataset> qw = new LambdaQueryWrapper<EvalDataset>()
                .eq(EvalDataset::getTenantId, tenant())
                .and(StringUtils.hasText(keyword),
                        w -> w.like(EvalDataset::getName, keyword).or().like(EvalDataset::getDescription, keyword))
                .eq(status != null, EvalDataset::getStatus, status)
                .orderByDesc(EvalDataset::getId);
        return datasetMapper.selectPage(new Page<>(page, size), qw);
    }

    /** 启用的数据集（下拉选项） */
    public List<EvalDataset> options() {
        return datasetMapper.selectList(new LambdaQueryWrapper<EvalDataset>()
                .eq(EvalDataset::getTenantId, tenant())
                .eq(EvalDataset::getStatus, 1)
                .orderByDesc(EvalDataset::getId));
    }

    public EvalDataset get(Long id) {
        EvalDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BizException("评测数据集不存在: " + id);
        }
        return dataset;
    }

    public EvalDataset create(EvalDataset dataset) {
        if (!StringUtils.hasText(dataset.getName())) {
            throw new BizException("数据集名称不能为空");
        }
        dataset.setId(null);
        dataset.setTenantId(tenant());
        dataset.setSource(StringUtils.hasText(dataset.getSource()) ? dataset.getSource() : "manual");
        dataset.setSampleCount(0);
        dataset.setStatus(dataset.getStatus() == null ? 1 : dataset.getStatus());
        dataset.setCreateTime(LocalDateTime.now());
        dataset.setUpdateTime(LocalDateTime.now());
        datasetMapper.insert(dataset);
        return dataset;
    }

    public EvalDataset update(Long id, EvalDataset req) {
        EvalDataset dataset = get(id);
        if (StringUtils.hasText(req.getName())) {
            dataset.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            dataset.setDescription(req.getDescription());
        }
        if (req.getStatus() != null) {
            dataset.setStatus(req.getStatus());
        }
        dataset.setUpdateTime(LocalDateTime.now());
        datasetMapper.updateById(dataset);
        return dataset;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        get(id);
        Long runCount = runMapper.selectCount(new LambdaQueryWrapper<EvalRun>().eq(EvalRun::getDatasetId, id));
        if (runCount != null && runCount > 0) {
            throw new BizException("该数据集已被评测任务引用，请先删除相关评测任务");
        }
        sampleMapper.delete(new LambdaQueryWrapper<EvalSample>().eq(EvalSample::getDatasetId, id));
        datasetMapper.deleteById(id);
    }

    // ---------- 样本 ----------

    public Page<EvalSample> samples(long page, long size, Long datasetId, String keyword) {
        get(datasetId);
        return sampleMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<EvalSample>()
                .eq(EvalSample::getDatasetId, datasetId)
                .and(StringUtils.hasText(keyword),
                        w -> w.like(EvalSample::getQuestion, keyword).or().like(EvalSample::getReference, keyword))
                .orderByDesc(EvalSample::getId));
    }

    public EvalSample addSample(Long datasetId, EvalSample sample) {
        get(datasetId);
        if (!StringUtils.hasText(sample.getQuestion())) {
            throw new BizException("提问内容不能为空");
        }
        sample.setId(null);
        sample.setDatasetId(datasetId);
        sample.setStatus(sample.getStatus() == null ? 1 : sample.getStatus());
        sample.setCreateTime(LocalDateTime.now());
        sampleMapper.insert(sample);
        changeSampleCount(datasetId, 1);
        return sample;
    }

    public EvalSample updateSample(Long datasetId, Long sampleId, EvalSample req) {
        get(datasetId);
        EvalSample sample = sampleMapper.selectById(sampleId);
        if (sample == null || !datasetId.equals(sample.getDatasetId())) {
            throw new BizException("样本不存在");
        }
        if (StringUtils.hasText(req.getQuestion())) {
            sample.setQuestion(req.getQuestion().trim());
        }
        if (req.getReference() != null) {
            sample.setReference(req.getReference());
        }
        if (req.getCategory() != null) {
            sample.setCategory(req.getCategory());
        }
        if (req.getStatus() != null) {
            sample.setStatus(req.getStatus());
        }
        sampleMapper.updateById(sample);
        return sample;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSample(Long datasetId, Long sampleId) {
        get(datasetId);
        if (sampleMapper.deleteById(sampleId) > 0) {
            changeSampleCount(datasetId, -1);
        }
    }

    /**
     * 批量导入样本：支持两种格式
     * <ul>
     *   <li>JSON 数组 / 每行一个 JSON 对象：[{question, reference, category}]</li>
     *   <li>每行一条样本，用制表符分隔：提问\t参考答案</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public int importSamples(Long datasetId, String text) {
        get(datasetId);
        if (!StringUtils.hasText(text)) {
            throw new BizException("导入内容不能为空");
        }
        List<EvalSample> samples = new ArrayList<>();
        String trimmed = text.trim();
        try {
            if (trimmed.startsWith("[")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = objectMapper.readValue(trimmed, List.class);
                for (Map<String, Object> item : list) {
                    EvalSample s = new EvalSample();
                    s.setQuestion(String.valueOf(item.getOrDefault("question", "")));
                    s.setReference(item.get("reference") == null ? null : String.valueOf(item.get("reference")));
                    s.setCategory(item.get("category") == null ? null : String.valueOf(item.get("category")));
                    if (StringUtils.hasText(s.getQuestion())) {
                        samples.add(s);
                    }
                }
            }
        } catch (Exception ignore) {
            // 非 JSON 格式，走逐行解析
        }
        if (samples.isEmpty()) {
            for (String line : trimmed.split("\\r?\\n")) {
                String row = line.trim();
                if (row.isEmpty()) {
                    continue;
                }
                String[] parts = row.split("\\t", 2);
                EvalSample s = new EvalSample();
                s.setQuestion(parts[0].trim());
                s.setReference(parts.length > 1 && StringUtils.hasText(parts[1]) ? parts[1].trim() : null);
                s.setCategory(null);
                if (StringUtils.hasText(s.getQuestion())) {
                    samples.add(s);
                }
            }
        }
        if (samples.isEmpty()) {
            throw new BizException("未解析到任何有效样本，请检查格式");
        }
        int count = 0;
        for (EvalSample s : samples) {
            s.setId(null);
            s.setDatasetId(datasetId);
            s.setStatus(1);
            s.setCreateTime(LocalDateTime.now());
            sampleMapper.insert(s);
            count++;
        }
        changeSampleCount(datasetId, count);
        return count;
    }

    private void changeSampleCount(Long datasetId, int delta) {
        datasetMapper.update(null, new LambdaUpdateWrapper<EvalDataset>()
                .eq(EvalDataset::getId, datasetId)
                .setSql("sample_count = sample_count + " + delta));
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
