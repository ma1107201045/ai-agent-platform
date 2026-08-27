package com.agent.platform.llm.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.agent.platform.llm.exception.LlmException;
import com.agent.platform.llm.model.ModelConfig;
import com.agent.platform.llm.model.RerankResult;
import com.agent.platform.llm.spi.RerankModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容重排序实现（/rerank，Jina / Cohere / 硅基流动等兼容接口）
 */
public class OpenAIRerankModel implements RerankModel {

    private static final String RERANK_PATH = "/rerank";

    private final OpenAiHttpClient client;
    private final String modelName;

    public OpenAIRerankModel(ModelConfig config) {
        this.client = new OpenAiHttpClient(config.getBaseUrl(), config.getApiKey(),
                config.getExtra() != null ? (java.time.Duration) config.getExtra().get("timeout") : null);
        this.modelName = config.getModelName();
    }

    @Override
    public String name() {
        return modelName;
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", topN);
        JsonNode json = client.postJson(RERANK_PATH, body);
        JsonNode results = json.path("results");
        if (!results.isArray()) {
            throw new LlmException("LLM 重排序响应缺少 results");
        }
        List<RerankResult> list = new ArrayList<>();
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            double score = item.path("relevance_score").asDouble(0);
            String text = index >= 0 && index < documents.size() ? documents.get(index) : "";
            list.add(new RerankResult(index, score, text));
        }
        return list;
    }
}
