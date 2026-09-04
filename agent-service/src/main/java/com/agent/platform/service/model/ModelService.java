package com.agent.platform.service.model;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.entity.model.ModelProvider;
import com.agent.platform.dao.mapper.model.ModelInfoMapper;
import com.agent.platform.dao.mapper.model.ModelProviderMapper;
import com.agent.platform.dao.vo.model.ModelInfoVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型管理服务
 */
@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelProviderMapper providerMapper;
    private final ModelInfoMapper modelInfoMapper;

    // ---------- 供应商 ----------

    public Page<ModelProvider> providerPage(long page, long size, String keyword) {
        LambdaQueryWrapper<ModelProvider> qw = new LambdaQueryWrapper<ModelProvider>()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(ModelProvider::getName, keyword)
                                .or().like(ModelProvider::getType, keyword)
                                .or().like(ModelProvider::getBaseUrl, keyword))
                .orderByDesc(ModelProvider::getId);
        return providerMapper.selectPage(new Page<>(page, size), qw);
    }

    public ModelProvider getProvider(Long id) {
        ModelProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException("模型供应商不存在: " + id);
        }
        return provider;
    }

    public ModelProvider createProvider(ModelProvider provider) {
        LocalDateTime now = LocalDateTime.now();
        provider.setId(null);
        provider.setStatus(provider.getStatus() == null ? 1 : provider.getStatus());
        provider.setCreateTime(now);
        provider.setUpdateTime(now);
        if (provider.getTenantId() == null) {
            provider.setTenantId(1L);
        }
        providerMapper.insert(provider);
        return provider;
    }

    public void updateProvider(ModelProvider provider) {
        getProvider(provider.getId());
        provider.setUpdateTime(LocalDateTime.now());
        providerMapper.updateById(provider);
    }

    public void deleteProvider(Long id) {
        getProvider(id);
        providerMapper.deleteById(id);
        modelInfoMapper.delete(new LambdaQueryWrapper<ModelInfo>().eq(ModelInfo::getProviderId, id));
    }

    // ---------- 模型 ----------

    public List<ModelInfo> modelsOf(Long providerId) {
        getProvider(providerId);
        return modelInfoMapper.selectList(
                new LambdaQueryWrapper<ModelInfo>().eq(ModelInfo::getProviderId, providerId).orderByDesc(ModelInfo::getId));
    }

    public ModelInfo createModel(ModelInfo model) {
        getProvider(model.getProviderId());
        LocalDateTime now = LocalDateTime.now();
        model.setId(null);
        model.setStatus(model.getStatus() == null ? 1 : model.getStatus());
        model.setCreateTime(now);
        model.setUpdateTime(now);
        modelInfoMapper.insert(model);
        return model;
    }

    public void updateModel(ModelInfo model) {
        if (modelInfoMapper.selectById(model.getId()) == null) {
            throw new BizException("模型不存在: " + model.getId());
        }
        model.setUpdateTime(LocalDateTime.now());
        modelInfoMapper.updateById(model);
    }

    public void deleteModel(Long id) {
        if (modelInfoMapper.deleteById(id) == 0) {
            throw new BizException("模型不存在: " + id);
        }
    }

    // ---------- 可用模型列表（供前端下拉 / 默认模型选择） ----------

    /**
     * 可用的对话模型列表（供应商启用 + 模型启用），供前端下拉选择
     */
    public List<ModelInfoVO> chatModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getModelType, "llm")
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }

    /**
     * 全部启用的模型列表（不限类型，供模型网关等场景下拉选择）
     */
    public List<ModelInfoVO> allModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }

    /**
     * 获取默认对话模型 ID（第一个启用的 LLM），无可用模型返回 null
     */
    public Long defaultChatModelId() {
        List<ModelInfoVO> list = chatModels();
        return list.isEmpty() ? null : list.get(0).getId();
    }

    /**
     * 可用的向量模型列表（供知识库配置下拉）
     */
    public List<ModelInfoVO> embeddingModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getModelType, "embedding")
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }

    /**
     * 可用的重排序模型列表
     */
    public List<ModelInfoVO> rerankModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getModelType, "rerank")
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ModelInfoVO> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ModelInfoVO(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }
}
