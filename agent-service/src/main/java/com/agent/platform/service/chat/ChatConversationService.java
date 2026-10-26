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

    Page<ChatConversation> page(Long appId, String mode, long page, long size, Long userId);

    ChatConversation getById(Long id, Long userId);

    ChatConversation create(Long appId, Long userId, Long tenantId, String title, String mode, Long modelId);

    void rename(Long id, Long userId, String title);

    void delete(Long id, Long userId);

    /** 批量删除会话（逐个校验归属），返回实际删除数量 */
    int deleteBatch(List<Long> ids, Long userId);

    List<ChatMessage> messages(Long conversationId, Long userId);

    /** 应用会话统计：会话数 + 消息数（用于对外访问/运营数据展示） */
    ChatAppAgentStatsVO stats(Long appId);

    /** 批量统计多个应用的会话/消息数，避免逐个查询造成 N+1 */
    Map<Long, ChatAppAgentStatsVO> statsBatch(List<Long> appIds);

    /**
     * 非流式发送：保存用户消息 → 按会话模式执行（直连模型 / Agent / 运行工作流）→ 保存助手消息。
     * 调用成功且拿到 usage 时，以 console 渠道写入一条用量事件。
     *
     * @return 保存后的助手消息（含回答文本与工作流轨迹）
     */
    ChatMessage send(Long conversationId, Long userId, String content, Long modelId);

    /**
     * 流式发送（仅直连模式）：先保存用户消息，增量推送 SSE 块，结束后持久化完整回答并记录用量事件
     */
    void streamSend(Long conversationId, Long userId, String content, Long modelId, Consumer<ChatChunk> onChunk);
}
