package com.agent.platform.llm.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 聊天响应（非流式）
 */
@Data
@Builder
public class ChatResponse {

    private String content;

    private List<ToolCall> toolCalls;

    /** stop / length / tool_calls / content_filter 等 */
    private String finishReason;

    private Usage usage;

    private String model;
}
