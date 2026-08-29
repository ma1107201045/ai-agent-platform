package com.agent.platform.controller.chat;

import com.agent.platform.common.result.Result;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.chat.ChatConversation;
import com.agent.platform.dao.entity.chat.ChatMessage;
import com.agent.platform.service.chat.ChatConversationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天会话接口：会话 CRUD + 消息持久化 + 发送消息（非流式 / SSE 流式）
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ChatConversationController {

    private final ChatConversationService conversationService;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    /** 当前用户的会话列表（可按应用过滤） */
    @GetMapping
    public Result<Page<ChatConversation>> page(@RequestParam(required = false) Long appId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return Result.ok(conversationService.page(appId, page, size, UserContext.getUserId()));
    }

    /** 创建会话 */
    @PostMapping
    public Result<ChatConversation> create(@RequestBody CreateReq req) {
        return Result.ok(conversationService.create(
                req.getAppId(),
                UserContext.getUserId(),
                UserContext.getTenantId(),
                req.getTitle(),
                req.getMode(),
                req.getModelId()));
    }

    /** 会话详情 */
    @GetMapping("/{id}")
    public Result<ChatConversation> getById(@PathVariable Long id) {
        return Result.ok(conversationService.getById(id, UserContext.getUserId()));
    }

    /** 重命名会话 */
    @PutMapping("/{id}")
    public Result<Void> rename(@PathVariable Long id, @RequestBody RenameReq req) {
        conversationService.rename(id, UserContext.getUserId(), req.getTitle());
        return Result.ok();
    }

    /** 删除会话（连带删除消息） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id, UserContext.getUserId());
        return Result.ok();
    }

    /** 会话消息列表 */
    @GetMapping("/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id) {
        return Result.ok(conversationService.messages(id, UserContext.getUserId()));
    }

    /** 发送消息（非流式；直连模型 / 工作流通用） */
    @PostMapping("/{id}/messages")
    public Result<ChatMessage> send(@PathVariable Long id, @RequestBody SendReq req) {
        return Result.ok(conversationService.send(id, UserContext.getUserId(), req.getContent(), req.getModelId()));
    }

    /**
     * 发送消息（SSE 流式，仅直连模型）。
     * 事件：message 携带 ChatChunk JSON 增量，结束事件 done 携带 [DONE]。
     * 流结束后后端自动持久化完整回答。
     */
    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id, @RequestBody SendReq req) {
        SseEmitter emitter = new SseEmitter(180_000L);
        Long userId = UserContext.getUserId(); // 线程池中拿不到 ThreadLocal，提前取出
        streamExecutor.execute(() -> {
            try {
                conversationService.streamSend(id, userId, req.getContent(), req.getModelId(), chunk -> {
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

    @Data
    public static class CreateReq {
        private Long appId;
        private String title;
        private String mode;
        private Long modelId;
    }

    @Data
    public static class RenameReq {
        private String title;
    }

    @Data
    public static class SendReq {
        private String content;
        private Long modelId;
    }
}
