package com.agent.platform.dao.mapper.chat;

import com.agent.platform.dao.entity.chat.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 按应用聚合统计（时间窗口内 assistant 消息 = 一次模型调用）。
     * 会话数通过 COUNT(DISTINCT conversation) 统计。
     */
    @Select("""
            SELECT c.app_id                                     AS app_id,
                   COALESCE(a.name, '未命名应用')               AS app_name,
                   COUNT(DISTINCT c.id)                         AS conv_cnt,
                   COUNT(m.id)                                  AS call_cnt,
                   COALESCE(SUM(m.tokens), 0)                   AS token_cnt
            FROM chat_message m
            INNER JOIN chat_conversation c ON c.id = m.conversation_id AND c.status = 1
            LEFT JOIN app_agent a ON a.id = c.app_id
            WHERE m.role = 'assistant'
              AND m.create_time >= #{start}
            GROUP BY c.app_id, a.name
            ORDER BY token_cnt DESC
            """)
    List<Map<String, Object>> usageByApp(@Param("start") LocalDateTime start);

    /**
     * 按日聚合统计（自然日粒度，asc 排列）
     */
    @Select("""
            SELECT DATE_FORMAT(m.create_time, '%Y-%m-%d')       AS day_date,
                   COUNT(m.id)                                  AS call_cnt,
                   COALESCE(SUM(m.tokens), 0)                   AS token_cnt
            FROM chat_message m
            INNER JOIN chat_conversation c ON c.id = m.conversation_id AND c.status = 1
            WHERE m.role = 'assistant'
              AND m.create_time >= #{start}
            GROUP BY DATE_FORMAT(m.create_time, '%Y-%m-%d')
            ORDER BY day_date ASC
            """)
    List<Map<String, Object>> usageDaily(@Param("start") LocalDateTime start);
}
