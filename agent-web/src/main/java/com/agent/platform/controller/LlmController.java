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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LLM 调用接口（对话测试 / 流式对话 / 向量化）
 */
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final ModelService modelService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    /** 可用对话模型列表（供前端下拉） */
    @GetMapping("/chat-models")
    public Result<List<ModelService.ChatModelInfo>> chatModels() {
        return Result.ok(modelService.chatModels());
    }

    /** 非流式对话 */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatReq request) {
        ChatModel model = modelService.chatModelOf(request.getModelId());
        return Result.ok(model.call(buildReq(request)));
    }

    /**
     * 流式对话（SSE）。事件：message 携带 ChatChunk JSON，结束事件 done 携带 [DONE]
     */
    @PostMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatReq request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        streamExecutor.execute(() -> {
            try {
                ChatModel model = modelService.chatModelOf(request.getModelId());
                model.stream(buildReq(request), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("message").data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** 向量化 */
    @PostMapping("/embed")
    public Result<EmbeddingResult> embed(@RequestBody EmbedReq request) {
        EmbeddingModel model = modelService.embeddingModelOf(request.getModelId());
        return Result.ok(model.embed(request.getTexts()));
    }

    private ChatRequest buildReq(ChatReq request) {
        ChatRequest.ChatRequestBuilder builder = ChatRequest.builder()
                .model(request.getModel())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens());
        List<ChatMessage> messages = new ArrayList<>();
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            // 多轮历史消息（前端已组装好 system + 历史）
            messages.addAll(request.getMessages());
        } else {
            // 兼容简单调用：system + 单条 user
            messages.add(ChatMessage.system(request.getSystemPrompt() == null
                    ? "You are a helpful assistant." : request.getSystemPrompt()));
            messages.add(ChatMessage.user(request.getPrompt()));
        }
        return builder.messages(messages).build();
    }

    @Data
    public static class ChatReq {
        private Long modelId;
        private String model;              // 可选，覆盖默认模型
        private String systemPrompt;
        private String prompt;
        private List<ChatMessage> messages; // 可选，多轮历史
        private Double temperature;
        private Integer maxTokens;
    }

    @Data
    public static class EmbedReq {
        private Long modelId;
        private List<String> texts;
    }
}
