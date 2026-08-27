package com.agent.platform.controller;

import com.agent.platform.common.result.Result;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.model.EmbeddingResult;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.service.ModelService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * LLM 调用测试接口（用于验证 Provider 抽象层）
 */
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final ModelService modelService;

    /** 非流式对话 */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatReq request) {
        ChatModel model = modelService.chatModelOf(request.getModelId());
        ChatRequest req = ChatRequest.builder()
                .model(request.getModel())
                .messages(List.of(
                        ChatMessage.system(request.getSystemPrompt() == null ? "You are a helpful assistant." : request.getSystemPrompt()),
                        ChatMessage.user(request.getPrompt())))
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .build();
        return Result.ok(model.call(req));
    }

    /** 向量化 */
    @PostMapping("/embed")
    public Result<EmbeddingResult> embed(@RequestBody EmbedReq request) {
        EmbeddingModel model = modelService.embeddingModelOf(request.getModelId());
        return Result.ok(model.embed(request.getTexts()));
    }

    @Data
    public static class ChatReq {
        private Long modelId;
        private String model;         // 可选，覆盖默认模型
        private String systemPrompt;
        private String prompt;
        private Double temperature;
        private Integer maxTokens;
    }

    @Data
    public static class EmbedReq {
        private Long modelId;
        private List<String> texts;
    }
}
