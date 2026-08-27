package com.agent.platform.llm.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.agent.platform.llm.exception.LlmException;
import com.agent.platform.llm.model.EmbeddingResult;
import com.agent.platform.llm.model.ModelConfig;
import com.agent.platform.llm.model.Usage;
import com.agent.platform.llm.spi.EmbeddingModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容向量模型实现（/embeddings）
 */
public class OpenAIEmbeddingModel implements EmbeddingModel {

    private static final String EMBEDDING_PATH = "/embeddings";

    private final OpenAiHttpClient client;
    private final String modelName;

    public OpenAIEmbeddingModel(ModelConfig config) {
        this.client = new OpenAiHttpClient(config.getBaseUrl(), config.getApiKey(),
                config.getExtra() != null ? (java.time.Duration) config.getExtra().get("timeout") : null);
        this.modelName = config.getModelName();
    }

    @Override
    public String name() {
        return modelName;
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new EmbeddingResult(List.of(), Usage.empty());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("input", texts);
        JsonNode json = client.postJson(EMBEDDING_PATH, body);
        JsonNode data = json.path("data");
        if (!data.isArray()) {
            throw new LlmException("LLM 向量响应缺少 data");
        }
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            vectors.add(vector);
        }
        JsonNode usage = json.path("usage");
        Usage u = usage.isObject()
                ? new Usage(usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0), usage.path("total_tokens").asLong(0))
                : Usage.empty();
        return new EmbeddingResult(vectors, u);
    }
}
