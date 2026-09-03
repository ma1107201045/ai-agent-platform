package com.agent.platform.service.model;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.entity.model.ModelProvider;
import com.agent.platform.dao.mapper.model.ModelInfoMapper;
import com.agent.platform.dao.mapper.model.ModelProviderMapper;
import com.agent.platform.llm.model.ModelConfig;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.llm.spi.LLMFactory;
import com.agent.platform.llm.spi.RerankModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模型运行时服务：根据启用的模型/供应商记录创建 LLM 实例（对话 / 向量化 / 重排序），
 * 供对话、知识库、工作流等运行期功能直接调用。
 * <p>
 * 与管理配置域 {@link ModelService}（供应商/模型 CRUD、可用模型列表）分离，职责单一。
 */
@Service
@RequiredArgsConstructor
public class ModelRuntimeService {

    private final ModelProviderMapper providerMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final LLMFactory llmFactory;

    /**
     * 根据模型记录创建 ChatModel 实例（供应商须启用）
     */
    public ChatModel chatModelOf(Long modelId) {
        ModelInfo info = requireModel(modelId);
        ModelProvider provider = requireEnabledProvider(info.getProviderId());
        return llmFactory.createChatModel(buildConfig(provider, info));
    }

    /**
     * 根据模型记录创建 EmbeddingModel 实例
     */
    public EmbeddingModel embeddingModelOf(Long modelId) {
        ModelInfo info = requireModel(modelId);
        ModelProvider provider = requireProvider(info.getProviderId());
        return llmFactory.createEmbeddingModel(buildConfig(provider, info));
    }

    /**
     * 根据模型记录创建 RerankModel 实例
     */
    public RerankModel rerankModelOf(Long modelId) {
        ModelInfo info = requireModel(modelId);
        ModelProvider provider = requireProvider(info.getProviderId());
        return llmFactory.createRerankModel(buildConfig(provider, info));
    }

    private ModelInfo requireModel(Long modelId) {
        ModelInfo info = modelInfoMapper.selectById(modelId);
        if (info == null) {
            throw new BizException("模型不存在: " + modelId);
        }
        return info;
    }

    private ModelProvider requireProvider(Long providerId) {
        ModelProvider provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BizException("模型供应商不存在: " + providerId);
        }
        return provider;
    }

    private ModelProvider requireEnabledProvider(Long providerId) {
        ModelProvider provider = requireProvider(providerId);
        if (provider.getStatus() == null || provider.getStatus() != 1) {
            throw new BizException("模型供应商已禁用: " + provider.getName());
        }
        return provider;
    }

    private ModelConfig buildConfig(ModelProvider provider, ModelInfo info) {
        return ModelConfig.builder()
                .provider(provider.getType())
                .baseUrl(provider.getBaseUrl())
                .apiKey(provider.getApiKey())
                .modelName(info.getName())
                .build();
    }
}
