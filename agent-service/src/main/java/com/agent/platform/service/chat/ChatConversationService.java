package com.agent.platform.service.chat;

import com.agent.platform.dao.entity.chat.ChatConversation;
import com.agent.platform.dao.entity.chat.ChatMessage;
import com.agent.platform.dao.vo.chat.ChatAppAgentStatsVO;
import com.agent.platform.llm.model.ChatChunk;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 聊天会话服务：会话 CRUD + 消息持久化 + 对话执行（直连模型 / 运行工作流）
 */
public interface ChatConversationService {

    public Page<ChatConversation> page(Long appId, String mode, long page, long size, Long userId);

    public ChatConversation getById(Long id, Long userId);

    public ChatConversation create(Long appId, Long userId, Long tenantId, String title, String mode, Long modelId);

    public void rename(Long id, Long userId, String title);

    public void delete(Long id, Long userId);

    public List<ChatMessage> messages(Long conversationId, Long userId);

    /** 应用会话统计：会话数 + 消息数（用于对外访问/运营数据展示） */
    public ChatAppAgentStatsVO stats(Long appId);

    /** 批量统计多个应用的会话/消息数，避免逐个查询造成 N+1 */
    public Map<Long, ChatAppAgentStatsVO> statsBatch(List<Long> appIds);

    /**
     * 非流式发送：保存用户消息 → 按会话模式执行（直连模型 / Agent / 运行工作流）→ 保存助手消息。
     * 调用成功且拿到 usage 时，以 console 渠道写入一条用量事件。
     *
     * @return 保存后的助手消息（含回答文本与工作流轨迹）
     */
    public ChatMessage send(Long conversationId, Long userId, String content, Long modelId);

    /**
     * 流式发送（仅直连模式）：先保存用户消息，增量推送 SSE 块，结束后持久化完整回答并记录用量事件
     */
    public void streamSend(Long conversationId, Long userId, String content, Long modelId, Consumer<ChatChunk> onChunk);
}
