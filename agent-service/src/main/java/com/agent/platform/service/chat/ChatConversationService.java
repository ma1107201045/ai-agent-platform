package com.agent.platform.service.chat;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.chat.ChatConversation;
import com.agent.platform.dao.entity.chat.ChatMessage;
import com.agent.platform.dao.entity.chat.ChatUsage;
import com.agent.platform.dao.mapper.chat.ChatConversationMapper;
import com.agent.platform.dao.mapper.chat.ChatMessageMapper;
import com.agent.platform.dao.mapper.chat.ChatUsageMapper;
import com.agent.platform.dao.vo.app.AgentChatVO;
import com.agent.platform.dao.vo.chat.AppAgentStatsVO;
import com.agent.platform.llm.model.ChatChunk;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.model.Usage;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.service.app.AppAgentService;
import com.agent.platform.service.model.ModelRuntimeService;
import com.agent.platform.orchestrator.RunResult;
import com.agent.platform.orchestrator.WorkflowEngine;
import com.agent.platform.orchestrator.WorkflowGraph;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatConversationService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final AppAgentService appAgentService;
    private final ModelRuntimeService modelRuntimeService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private final ChatUsageStatsService usageStatsService;
    private final ChatUsageMapper usageMapper;

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
        appAgentService.getById(appId);
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
        usageMapper.delete(new LambdaQueryWrapper<ChatUsage>()
                .eq(ChatUsage::getConversationId, id));
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
    public AppAgentStatsVO stats(Long appId) {
        appAgentService.getById(appId);
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
        return new AppAgentStatsVO(conversationCount, messageCount);
    }

    /** 批量统计多个应用的会话/消息数，避免逐个查询造成 N+1 */
    public Map<Long, AppAgentStatsVO> statsBatch(List<Long> appIds) {
        Map<Long, AppAgentStatsVO> result = new HashMap<>();
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
            result.put(appId, new AppAgentStatsVO(
                    convCount.getOrDefault(appId, 0L),
                    msgCount.getOrDefault(appId, 0L)));
        }
        return result;
    }

    // ---------- 发送消息 ----------

    /**
     * 非流式发送：保存用户消息 → 按会话模式执行（直连模型 / Agent / 运行工作流）→ 保存助手消息。
     * 调用成功且拿到 usage 时，以 console 渠道写入一条用量事件。
     *
     * @return 保存后的助手消息（含回答文本与工作流轨迹）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage send(Long conversationId, Long userId, String content, Long modelId) {
        ChatConversation conv = getById(conversationId, userId);
        String answer;
        String traceJson = null;
        long tokens = 0;
        Long usedModelId = null;
        Usage usage = null;
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
            AgentChatVO result = appAgentService.chat(conv.getAppId(), mid, null, llmHistory, null);
            answer = result.getAnswer();
            traceJson = toJson(result.getSteps());
            tokens = result.getTotalTokens();
            usedModelId = mid;
            usage = new Usage(result.getPromptTokens(), result.getCompletionTokens(), result.getTotalTokens());
        } else {
            Long mid = modelId != null ? modelId : conv.getModelId();
            if (mid == null) {
                throw new BizException("请选择对话模型");
            }
            List<ChatMessage> history = messages(conversationId, userId);
            ChatModel model = modelRuntimeService.chatModelOf(mid);
            ChatResponse response = model.call(buildChatRequest(conv, history, content, mid));
            answer = response == null ? "" : response.getContent();
            usage = response == null ? null : response.getUsage();
            tokens = usage == null ? 0 : usage.totalTokens();
            usedModelId = mid;
        }
        saveMessage(conversationId, "user", content, null, 0);
        ChatMessage assistant = saveMessage(conversationId, "assistant", answer, traceJson, tokens);
        recordUsageSafely(conv, usedModelId, usage);
        return assistant;
    }

    /**
     * 流式发送（仅直连模式）：先保存用户消息，增量推送 SSE 块，结束后持久化完整回答并记录用量事件
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
        ChatModel model = modelRuntimeService.chatModelOf(mid);
        StringBuilder sb = new StringBuilder();
        long[] tokens = {0};
        Usage[] usage = {null};
        model.stream(buildChatRequest(conv, history, content, mid), chunk -> {
            if (chunk.getDelta() != null) {
                sb.append(chunk.getDelta());
            }
            if (chunk.getUsage() != null) {
                tokens[0] = chunk.getUsage().totalTokens();
                usage[0] = chunk.getUsage();
            }
            onChunk.accept(chunk);
        });
        saveMessage(conversationId, "assistant", sb.toString(), null, tokens[0]);
        recordUsageSafely(conv, mid, usage[0]);
    }

    /** 记录一条控制台用量事件；失败仅告警日志，不影响对话主流程 */
    private void recordUsageSafely(ChatConversation conv, Long modelId, Usage usage) {
        if (usage == null) {
            return;
        }
        try {
            usageStatsService.recordUsage(conv.getTenantId(), conv.getAppId(), conv.getId(),
                    conv.getUserId(), modelId, "console", conv.getMode(), usage);
        } catch (Exception e) {
            log.warn("记录用量事件失败 conversationId={}: {}", conv.getId(), e.getMessage());
        }
    }

    // ---------- 内部方法 ----------

    /** 运行应用工作流，返回执行结果与轨迹 */
    private RunResult runWorkflow(ChatConversation conv, String content) {
        String dsl = appAgentService.getRunWorkflow(conv.getAppId());
        if (dsl == null || dsl.isBlank()) {
            throw new BizException("应用尚未编排工作流，请先在画布中保存草稿或发布");
        }
        try {
            WorkflowGraph graph = objectMapper.readValue(dsl, WorkflowGraph.class);
            return workflowEngine.run(graph, content, conv.getAppId());
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
}
