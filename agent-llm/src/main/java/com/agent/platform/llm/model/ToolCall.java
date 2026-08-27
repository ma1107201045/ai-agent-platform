package com.agent.platform.llm.model;

/**
 * 工具调用（Function Calling）
 */
public record ToolCall(String id, String name, String arguments) {
}
