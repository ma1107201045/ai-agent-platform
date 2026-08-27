package com.agent.platform.llm.model;

import java.util.List;

/**
 * 聊天消息。
 * <p>
 * 工具调用场景下：
 * - assistant 消息可携带 {@link #toolCalls}（模型发起工具调用）
 * - tool 消息携带 {@link #toolCallId}（工具执行结果回填）
 */
public record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ROLE_SYSTEM, content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ROLE_USER, content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ROLE_ASSISTANT, content, null, null);
    }

    /** 模型发起工具调用的 assistant 消息 */
    public static ChatMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(ROLE_ASSISTANT, content, toolCalls, null);
    }

    /** 工具执行结果回填消息 */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage(ROLE_TOOL, content, null, toolCallId);
    }
}
