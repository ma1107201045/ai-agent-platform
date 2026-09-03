package com.agent.platform.dao.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用量按日趋势点
 */
@Data
@AllArgsConstructor
public class ChatTrendPointVO {
    private String date;
    private long calls;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
}
