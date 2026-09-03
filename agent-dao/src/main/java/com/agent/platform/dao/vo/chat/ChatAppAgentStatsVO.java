package com.agent.platform.dao.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 应用会话统计结果（对外访问/运营数据展示）
 */
@Data
@AllArgsConstructor
public class ChatAppAgentStatsVO {
    private Long conversationCount;
    private Long messageCount;
}
