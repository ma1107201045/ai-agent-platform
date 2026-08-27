package com.agent.platform.llm.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.agent.platform.llm.exception.LlmException;
import com.agent.platform.llm.model.*;
import com.agent.platform.llm.spi.ChatModel;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 Chat 模型实现（/chat/completions）
 */
@Slf4j
public class OpenAIChatModel implements ChatModel {

    private static final String CHAT_PATH = "/chat/completions";

    private final OpenAiHttpClient client;
    private final String modelName;
    private final Map<String, Object> extraBody;

    public OpenAIChatModel(ModelConfig config) {
        this.client = new OpenAiHttpClient(config.getBaseUrl(), config.getApiKey(),
                config.getExtra() != null ? (Duration) config.getExtra().get("timeout") : null);
        this.modelName = config.getModelName();
        this.extraBody = config.getExtra() != null ? config.getExtra() : Map.of();
    }

    @Override
    public String name() {
        return modelName;
    }

    @Override
    public ChatResponse call(ChatRequest request) {
        Map<String, Object> body = buildBody(request, false);
        JsonNode json = client.postJson(CHAT_PATH, body);
        JsonNode choices = json.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmException("LLM 响应缺少 choices");
        }
        JsonNode choice = choices.get(0);
        JsonNode message = choice.path("message");
        String content = message.path("content").isNull() ? null : message.path("content").asText();
        List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
        String finishReason = choice.path("finish_reason").asText(null);
        Usage usage = parseUsage(json.path("usage"));
        return ChatResponse.builder()
                .content(content)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .usage(usage)
                .model(json.path("model").asText(modelName))
                .build();
    }

    @Override
    public void stream(ChatRequest request, Consumer<ChatChunk> onChunk) {
        Map<String, Object> body = buildBody(request, true);
        HttpResponse<InputStream> response = client.postStream(CHAT_PATH, body);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || !line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode node = client.objectMapper().readTree(data);
                JsonNode choices = node.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode choice = choices.get(0);
                    ChatChunk chunk = ChatChunk.builder()
                            .delta(choice.path("delta").path("content").isNull()
                                    ? null : choice.path("delta").path("content").asText())
                            .toolCalls(parseToolCalls(choice.path("delta").path("tool_calls")))
                            .finishReason(choice.path("finish_reason").isNull()
                                    ? null : choice.path("finish_reason").asText())
                            .usage(parseUsage(node.path("usage")))
                            .model(node.path("model").asText(modelName))
                            .build();
                    onChunk.accept(chunk);
                }
            }
        } catch (Exception e) {
            throw new LlmException("LLM 流式读取异常: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : modelName);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage m : request.getMessages()) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role());
            msg.put("content", m.content());
            messages.add(msg);
        }
        body.put("messages", messages);
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.put("top_p", request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", buildTools(request.getTools()));
        }
        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat());
        }
        body.putAll(extraBody);
        body.put("stream", stream);
        return body;
    }

    private List<Map<String, Object>> buildTools(List<FunctionTool> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (FunctionTool tool : tools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            try {
                JsonNode params = client.objectMapper().readTree(tool.parametersJson());
                function.put("parameters", params);
            } catch (Exception e) {
                throw new LlmException("工具参数 JSON Schema 解析失败: " + tool.name(), e);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "function");
            item.put("function", function);
            result.add(item);
        }
        return result;
    }

    private List<ToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || toolCallsNode.isNull() || !toolCallsNode.isArray()) {
            return null;
        }
        List<ToolCall> result = new ArrayList<>();
        for (JsonNode tc : toolCallsNode) {
            String id = tc.path("id").asText(null);
            JsonNode function = tc.path("function");
            result.add(new ToolCall(id, function.path("name").asText(null), function.path("arguments").asText(null)));
        }
        return result.isEmpty() ? null : result;
    }

    private Usage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull()) {
            return Usage.empty();
        }
        return new Usage(
                usageNode.path("prompt_tokens").asLong(0),
                usageNode.path("completion_tokens").asLong(0),
                usageNode.path("total_tokens").asLong(0));
    }
}
