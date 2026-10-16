package com.agent.platform.service.model;

import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.llm.spi.RerankModel;

/**
 * 模型运行时服务：根据启用的模型/供应商记录创建 LLM 实例（对话 / 向量化 / 重排序），
 * 供对话、知识库、工作流等运行期功能直接调用。
 * <p>
 * 与管理配置域 {@link ModelService}（供应商/模型 CRUD、可用模型列表）分离，职责单一。
 */
public interface ModelRuntimeService {

    /**
     * 根据模型记录创建 ChatModel 实例（供应商须启用）
     */
    public ChatModel chatModelOf(Long modelId);

    /**
     * 根据模型记录创建 EmbeddingModel 实例
     */
    public EmbeddingModel embeddingModelOf(Long modelId);

    /**
     * 根据模型记录创建 RerankModel 实例
     */
    public RerankModel rerankModelOf(Long modelId);
}
