package com.agent.platform.llm.provider.openai;

import com.agent.platform.llm.model.ModelConfig;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.llm.spi.LLMProvider;
import com.agent.platform.llm.spi.RerankModel;

/**
 * OpenAI 兼容协议供应商。
 * 覆盖：OpenAI、DeepSeek、通义千问(兼容模式)、Moonshot、智谱、本地 vLLM/Ollama(openai 模式) 等。
 * 供应商标识: openai-compatible
 */
public class OpenAiCompatibleProvider implements LLMProvider {

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public ChatModel createChatModel(ModelConfig config) {
        return new OpenAIChatModel(config);
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ModelConfig config) {
        return new OpenAIEmbeddingModel(config);
    }

    @Override
    public RerankModel createRerankModel(ModelConfig config) {
        return new OpenAIRerankModel(config);
    }
}
