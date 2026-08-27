package com.agent.platform.service;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.ModelInfo;
import com.agent.platform.dao.entity.ModelProvider;
import com.agent.platform.dao.mapper.ModelInfoMapper;
import com.agent.platform.dao.mapper.ModelProviderMapper;
import com.agent.platform.llm.model.ModelConfig;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.llm.spi.LLMFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final LLMFactory llmFactory;

    // ---------- 供应商 ----------

    public Page<ModelProvider> providerPage(long page, long size) {
        return providerMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ModelProvider>().orderByDesc(ModelProvider::getId));
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

    public void deleteModel(Long id) {
        if (modelInfoMapper.deleteById(id) == 0) {
            throw new BizException("模型不存在: " + id);
        }
    }

    // ---------- LLM 实例 ----------

    /**
     * 可用的对话模型列表（供应商启用 + 模型启用），供前端下拉选择
     */
    public List<ChatModelInfo> chatModels() {
        List<ModelInfo> infos = modelInfoMapper.selectList(new LambdaQueryWrapper<ModelInfo>()
                .eq(ModelInfo::getModelType, "llm")
                .eq(ModelInfo::getStatus, 1)
                .orderByAsc(ModelInfo::getId));
        List<ChatModelInfo> result = new ArrayList<>();
        for (ModelInfo info : infos) {
            ModelProvider provider = providerMapper.selectById(info.getProviderId());
            if (provider == null || provider.getStatus() == null || provider.getStatus() != 1) {
                continue;
            }
            result.add(new ChatModelInfo(info.getId(), provider.getName(), info.getName(), info.getContextWindow()));
        }
        return result;
    }

    /**
     * 根据模型记录创建 ChatModel 实例
     */
    public ChatModel chatModelOf(Long modelId) {
        ModelInfo info = modelInfoMapper.selectById(modelId);
        if (info == null) {
            throw new BizException("模型不存在: " + modelId);
        }
        ModelProvider provider = getProvider(info.getProviderId());
        if (provider.getStatus() == null || provider.getStatus() != 1) {
            throw new BizException("模型供应商已禁用: " + provider.getName());
        }
        return llmFactory.createChatModel(buildConfig(provider, info));
    }

    /**
     * 根据模型记录创建 EmbeddingModel 实例
     */
    public EmbeddingModel embeddingModelOf(Long modelId) {
        ModelInfo info = modelInfoMapper.selectById(modelId);
        if (info == null) {
            throw new BizException("模型不存在: " + modelId);
        }
        ModelProvider provider = getProvider(info.getProviderId());
        return llmFactory.createEmbeddingModel(buildConfig(provider, info));
    }

    private ModelConfig buildConfig(ModelProvider provider, ModelInfo info) {
        return ModelConfig.builder()
                .provider(provider.getType())
                .baseUrl(provider.getBaseUrl())
                .apiKey(provider.getApiKey())
                .modelName(info.getName())
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class ChatModelInfo {
        private Long id;
        private String providerName;
        private String modelName;
        private Integer contextWindow;
    }
}
