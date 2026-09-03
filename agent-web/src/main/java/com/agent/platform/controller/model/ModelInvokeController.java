package com.agent.platform.controller.model;

import com.agent.platform.common.result.Result;
import com.agent.platform.llm.model.ChatChunk;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.model.EmbeddingResult;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.service.model.ModelRuntimeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 模型调用接口：非流式对话、流式对话（SSE）、向量化，直接面向配置好的 LLM 供应商。
 * 流式对话提供两种并行实现，协议一致（message 事件携带 ChatChunk JSON，done 事件携带 [DONE]）：
 * <ul>
 *     <li>{@code /chat-stream}：基于 SseEmitter + 线程池</li>
 *     <li>{@code /chat-stream/flux}：基于 Spring Flux（Reactor）响应式流式输出</li>
 * </ul>
 * <p>模型管理接口（供应商/模型 CRUD、可用列表）见 {@link ModelController}
 */
@RestController
@RequestMapping("/api/model/models")
@RequiredArgsConstructor
public class ModelInvokeController {

    private final ModelRuntimeService modelRuntimeService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    // ---------- 模型调用 ----------

    /** 非流式对话 */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatReq request) {
        ChatModel model = modelRuntimeService.chatModelOf(request.getModelId());
        return Result.ok(model.call(buildReq(request)));
    }

    /** 流式对话（SSE），SseEmitter 实现 */
    @PostMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatReq request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        streamExecutor.execute(() -> {
            try {
                ChatModel model = modelRuntimeService.chatModelOf(request.getModelId());
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

    /**
     * 流式对话（SSE），基于 Spring Flux（Reactor）实现流式输出。
     * <p>与 {@code /chat-stream} 事件协议一致；模型流在 boundedElastic 调度器上执行，不占用容器线程。
     */
    @PostMapping(value = "/chat-stream/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> chatStreamFlux(@RequestBody ChatReq request) {
        ChatRequest chatRequest = buildReq(request);
        Flux<ChatChunk> chunks = Flux.<ChatChunk>create(sink -> {
                    try {
                        ChatModel model = modelRuntimeService.chatModelOf(request.getModelId());
                        model.stream(chatRequest, chunk -> {
                            if (!sink.isCancelled()) {
                                sink.next(chunk);
                            }
                        });
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());

        Flux<ServerSentEvent<?>> messageEvents = chunks.map(chunk -> ServerSentEvent
                .builder(chunk)
                .event("message")
                .build());
        Flux<ServerSentEvent<?>> doneEvent = Flux.just(ServerSentEvent.builder("[DONE]")
                .event("done")
                .build());
        return messageEvents.concatWith(doneEvent);
    }

    /** 向量化 */
    @PostMapping("/embed")
    public Result<EmbeddingResult> embed(@RequestBody EmbedReq request) {
        EmbeddingModel model = modelRuntimeService.embeddingModelOf(request.getModelId());
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
