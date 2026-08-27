package com.agent.platform.llm.spi;

import com.agent.platform.llm.exception.LlmException;
import com.agent.platform.llm.model.ModelConfig;
import com.agent.platform.llm.provider.openai.OpenAiCompatibleProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 供应商工厂：根据配置创建模型实例。
 * 新供应商通过 register() 注册即可扩展。
 */
@Component
public class LLMFactory {

    private final Map<String, LLMProvider> providers = new ConcurrentHashMap<>();

    public LLMFactory() {
        register(new OpenAiCompatibleProvider());
    }

    public void register(LLMProvider provider) {
        providers.put(provider.name(), provider);
    }

    public LLMProvider getProvider(String providerName) {
        LLMProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new LlmException("未注册的模型供应商: " + providerName + "，可选: " + providers.keySet());
        }
        return provider;
    }

    public ChatModel createChatModel(ModelConfig config) {
        return getProvider(config.getProvider()).createChatModel(config);
    }

    public EmbeddingModel createEmbeddingModel(ModelConfig config) {
        return getProvider(config.getProvider()).createEmbeddingModel(config);
    }

    public RerankModel createRerankModel(ModelConfig config) {
        return getProvider(config.getProvider()).createRerankModel(config);
    }
}
