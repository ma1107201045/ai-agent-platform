package com.agent.platform.controller.model;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.entity.model.ModelProvider;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.model.EmbeddingResult;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.service.model.ModelService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 模型管理 + 模型调用（供应商/模型 CRUD、可用模型列表、对话、流式对话、向量化）
 */
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    // ---------- 供应商 ----------

    @GetMapping("/providers")
    public Result<Page<ModelProvider>> providerPage(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return Result.ok(modelService.providerPage(page, size));
    }

    @PostMapping("/providers")
    public Result<ModelProvider> createProvider(@RequestBody ModelProvider provider) {
        return Result.ok(modelService.createProvider(provider));
    }

    @PutMapping("/providers/{id}")
    public Result<Void> updateProvider(@PathVariable Long id, @RequestBody ModelProvider provider) {
        provider.setId(id);
        modelService.updateProvider(provider);
        return Result.ok();
    }

    @DeleteMapping("/providers/{id}")
    public Result<Void> deleteProvider(@PathVariable Long id) {
        modelService.deleteProvider(id);
        return Result.ok();
    }

    // ---------- 模型 ----------

    @GetMapping("/providers/{providerId}/models")
    public Result<List<ModelInfo>> modelsOf(@PathVariable Long providerId) {
        return Result.ok(modelService.modelsOf(providerId));
    }

    @PostMapping("/providers/{providerId}/models")
    public Result<ModelInfo> createModel(@PathVariable Long providerId, @RequestBody ModelInfo model) {
        model.setProviderId(providerId);
        return Result.ok(modelService.createModel(model));
    }

    @DeleteMapping("/models/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelService.deleteModel(id);
        return Result.ok();
    }

    // ---------- 可用模型列表（供前端下拉） ----------

    /** 可用对话模型列表 */
    @GetMapping("/models/chat-models")
    public Result<List<ModelService.ChatModelInfo>> chatModels() {
        return Result.ok(modelService.chatModels());
    }

    /** 可用向量模型列表（供知识库配置下拉） */
    @GetMapping("/models/embedding-models")
    public Result<List<ModelService.ChatModelInfo>> embeddingModels() {
        return Result.ok(modelService.embeddingModels());
    }

    /** 可用重排序模型列表 */
    @GetMapping("/models/rerank-models")
    public Result<List<ModelService.ChatModelInfo>> rerankModels() {
        return Result.ok(modelService.rerankModels());
    }

    // ---------- 模型调用 ----------

    /** 非流式对话 */
    @PostMapping("/models/chat")
    public Result<ChatResponse> chat(@RequestBody ChatReq request) {
        ChatModel model = modelService.chatModelOf(request.getModelId());
        return Result.ok(model.call(buildReq(request)));
    }

    /**
     * 流式对话（SSE）。事件：message 携带 ChatChunk JSON，结束事件 done 携带 [DONE]
     */
    @PostMapping(value = "/models/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
    @PostMapping("/models/embed")
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
