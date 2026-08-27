package com.agent.platform.service;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.ChatConversation;
import com.agent.platform.dao.entity.ChatMessage;
import com.agent.platform.dao.mapper.ChatConversationMapper;
import com.agent.platform.dao.mapper.ChatMessageMapper;
import com.agent.platform.engine.RunResult;
import com.agent.platform.service.AgentService;
import com.agent.platform.engine.WorkflowEngine;
import com.agent.platform.engine.WorkflowGraph;
import com.agent.platform.llm.model.ChatChunk;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.spi.ChatModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 聊天会话服务：会话 CRUD + 消息持久化 + 对话执行（直连模型 / 运行工作流）
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final AppService appService;
    private final ModelService modelService;
    private final AgentService agentService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    // ---------- 会话 CRUD ----------

    public Page<ChatConversation> page(Long appId, long page, long size, Long userId) {
        LambdaQueryWrapper<ChatConversation> qw = new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getStatus, 1)
                .orderByDesc(ChatConversation::getUpdateTime);
        if (appId != null) {
            qw.eq(ChatConversation::getAppId, appId);
        }
        return conversationMapper.selectPage(new Page<>(page, size), qw);
    }

    public ChatConversation getById(Long id, Long userId) {
        ChatConversation conv = conversationMapper.selectById(id);
        if (conv == null || conv.getUserId() == null || !conv.getUserId().equals(userId)
                || conv.getStatus() == null || conv.getStatus() != 1) {
            throw new BizException("会话不存在: " + id);
        }
        return conv;
    }

    public ChatConversation create(Long appId, Long userId, Long tenantId, String title, String mode, Long modelId) {
        appService.getById(appId);
        ChatConversation conv = new ChatConversation();
        conv.setTenantId(tenantId == null ? 1L : tenantId);
        conv.setUserId(userId);
        conv.setAppId(appId);
        conv.setTitle(title);
        conv.setMode(mode == null || mode.isBlank() ? "direct" : mode);
        conv.setModelId(modelId);
        conv.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        conv.setCreateTime(now);
        conv.setUpdateTime(now);
        conversationMapper.insert(conv);
        return conv;
    }

    public void rename(Long id, Long userId, String title) {
        ChatConversation conv = getById(id, userId);
        conv.setTitle(title);
        conv.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        ChatConversation conv = getById(id, userId);
        conv.setStatus(0);
        conv.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conv);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, id));
    }

    public List<ChatMessage> messages(Long conversationId, Long userId) {
        getById(conversationId, userId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getStatus, 1)
                .orderByAsc(ChatMessage::getId));
    }

    // ---------- 统计 ----------

    /** 应用会话统计：会话数 + 消息数（用于对外访问/运营数据展示） */
    public AppStats stats(Long appId) {
        appService.getById(appId);
        List<Long> convIds = conversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                        .select(ChatConversation::getId)
                        .eq(ChatConversation::getAppId, appId)
                        .eq(ChatConversation::getStatus, 1))
                .stream()
                .map(ChatConversation::getId)
                .toList();
        Long conversationCount = (long) convIds.size();
        Long messageCount = 0L;
        if (!convIds.isEmpty()) {
            messageCount = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getStatus, 1)
                    .in(ChatMessage::getConversationId, convIds));
        }
        return new AppStats(conversationCount, messageCount);
    }

    /** 批量统计多个应用的会话/消息数，避免逐个查询造成 N+1 */
    public Map<Long, AppStats> statsBatch(List<Long> appIds) {
        Map<Long, AppStats> result = new HashMap<>();
        if (appIds == null || appIds.isEmpty()) {
            return result;
        }
        List<ChatConversation> convs = conversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                .select(ChatConversation::getId, ChatConversation::getAppId)
                .in(ChatConversation::getAppId, appIds)
                .eq(ChatConversation::getStatus, 1));
        Map<Long, Long> convCount = new HashMap<>();
        List<Long> convIds = new ArrayList<>();
        for (ChatConversation c : convs) {
            convCount.merge(c.getAppId(), 1L, Long::sum);
            convIds.add(c.getId());
        }
        Map<Long, Long> msgCount = new HashMap<>();
        if (!convIds.isEmpty()) {
            List<ChatMessage> msgs = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .select(ChatMessage::getConversationId)
                    .in(ChatMessage::getConversationId, convIds)
                    .eq(ChatMessage::getStatus, 1));
            Map<Long, Long> msgByConv = new HashMap<>();
            for (ChatMessage m : msgs) {
                msgByConv.merge(m.getConversationId(), 1L, Long::sum);
            }
            for (ChatConversation c : convs) {
                Long cnt = msgByConv.get(c.getId());
                if (cnt != null) {
                    msgCount.merge(c.getAppId(), cnt, Long::sum);
                }
            }
        }
        for (Long appId : appIds) {
            result.put(appId, new AppStats(
                    convCount.getOrDefault(appId, 0L),
                    msgCount.getOrDefault(appId, 0L)));
        }
        return result;
    }

    // ---------- 发送消息 ----------

    /**
     * 非流式发送：保存用户消息 → 按会话模式执行（直连模型 / 运行工作流）→ 保存助手消息
     *
     * @return 保存后的助手消息（含回答文本与工作流轨迹）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage send(Long conversationId, Long userId, String content, Long modelId) {
        ChatConversation conv = getById(conversationId, userId);
        String answer;
        String traceJson = null;
        long tokens = 0;
        if ("workflow".equals(conv.getMode())) {
            RunResult result = runWorkflow(conv, content);
            answer = result.getAnswer();
            traceJson = toJson(result.getTrace());
        } else if ("agent".equals(conv.getMode())) {
            Long mid = modelId != null ? modelId : conv.getModelId();
            if (mid == null) {
                throw new BizException("请选择对话模型");
            }
            List<ChatMessage> history = messages(conversationId, userId);
            List<com.agent.platform.llm.model.ChatMessage> llmHistory = toLlmMessages(history);
            llmHistory.add(com.agent.platform.llm.model.ChatMessage.user(content));
            AgentService.AgentResult result = agentService.chat(conv.getAppId(), mid, null, llmHistory, null);
            answer = result.getAnswer();
            traceJson = toJson(result.getSteps());
        } else {
            Long mid = modelId != null ? modelId : conv.getModelId();
            if (mid == null) {
                throw new BizException("请选择对话模型");
            }
            List<ChatMessage> history = messages(conversationId, userId);
            ChatModel model = modelService.chatModelOf(mid);
            ChatResponse response = model.call(buildChatRequest(conv, history, content, mid));
            answer = response == null ? "" : response.getContent();
            tokens = response != null && response.getUsage() != null ? response.getUsage().totalTokens() : 0;
        }
        saveMessage(conversationId, "user", content, null, 0);
        return saveMessage(conversationId, "assistant", answer, traceJson, tokens);
    }

    /**
     * 流式发送（仅直连模式）：先保存用户消息，增量推送 SSE 块，结束后持久化完整回答
     */
    @Transactional(rollbackFor = Exception.class)
    public void streamSend(Long conversationId, Long userId, String content, Long modelId, Consumer<ChatChunk> onChunk) {
        ChatConversation conv = getById(conversationId, userId);
        if ("workflow".equals(conv.getMode())) {
            throw new BizException("工作流模式不支持流式，请使用非流式发送");
        }
        Long mid = modelId != null ? modelId : conv.getModelId();
        if (mid == null) {
            throw new BizException("请选择对话模型");
        }
        List<ChatMessage> history = messages(conversationId, userId);
        saveMessage(conversationId, "user", content, null, 0);
        ChatModel model = modelService.chatModelOf(mid);
        StringBuilder sb = new StringBuilder();
        long[] tokens = {0};
        model.stream(buildChatRequest(conv, history, content, mid), chunk -> {
            if (chunk.getDelta() != null) {
                sb.append(chunk.getDelta());
            }
            if (chunk.getUsage() != null) {
                tokens[0] = chunk.getUsage().totalTokens();
            }
            onChunk.accept(chunk);
        });
        saveMessage(conversationId, "assistant", sb.toString(), null, tokens[0]);
    }

    // ---------- 内部方法 ----------

    /** 运行应用工作流，返回执行结果与轨迹 */
    private RunResult runWorkflow(ChatConversation conv, String content) {
        String dsl = appService.getRunWorkflow(conv.getAppId());
        if (dsl == null || dsl.isBlank()) {
            throw new BizException("应用尚未编排工作流，请先在画布中保存草稿或发布");
        }
        try {
            WorkflowGraph graph = objectMapper.readValue(dsl, WorkflowGraph.class);
            return workflowEngine.run(graph, content);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("工作流 DSL 解析失败: " + e.getMessage());
        }
    }

    /** 组装多轮对话消息：历史(不含失败) + 本次用户输入 */
    private ChatRequest buildChatRequest(ChatConversation conv, List<ChatMessage> history, String content, Long modelId) {
        List<com.agent.platform.llm.model.ChatMessage> messages = toLlmMessages(history);
        messages.add(com.agent.platform.llm.model.ChatMessage.user(content));
        return ChatRequest.builder().messages(messages).build();
    }

    /** 会话历史实体 → LLM 消息列表（过滤空内容） */
    private List<com.agent.platform.llm.model.ChatMessage> toLlmMessages(List<ChatMessage> history) {
        List<com.agent.platform.llm.model.ChatMessage> messages = new ArrayList<>();
        if (history != null) {
            for (ChatMessage h : history) {
                if (h.getContent() == null || h.getContent().isBlank()) {
                    continue;
                }
                messages.add(new com.agent.platform.llm.model.ChatMessage(h.getRole(), h.getContent()));
            }
        }
        return messages;
    }

    /** 持久化一条消息并刷新会话更新时间 */
    private ChatMessage saveMessage(Long conversationId, String role, String content, String traceJson, long tokens) {
        ChatMessage msg = new ChatMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTraceJson(traceJson);
        msg.setTokens(tokens);
        msg.setStatus(1);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);

        ChatConversation touch = new ChatConversation();
        touch.setId(conversationId);
        touch.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(touch);
        return msg;
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 应用会话统计结果 */
    @Data
    @AllArgsConstructor
    public static class AppStats {
        private Long conversationCount;
        private Long messageCount;
    }
}
