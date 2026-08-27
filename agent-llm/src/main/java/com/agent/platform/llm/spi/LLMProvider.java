package com.agent.platform.llm.spi;

import com.agent.platform.llm.model.ModelConfig;

/**
 * 模型供应商 SPI。一个供应商可创建多种模型实例（chat / embedding / rerank）。
 */
public interface LLMProvider {

    /** 供应商唯一标识，如 openai-compatible */
    String name();

    ChatModel createChatModel(ModelConfig config);

    EmbeddingModel createEmbeddingModel(ModelConfig config);

    default RerankModel createRerankModel(ModelConfig config) {
        throw new UnsupportedOperationException("供应商不支持重排序: " + name());
    }
}
