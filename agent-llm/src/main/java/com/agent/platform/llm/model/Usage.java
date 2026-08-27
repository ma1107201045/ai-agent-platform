package com.agent.platform.llm.model;

/**
 * Token 用量统计
 */
public record Usage(long promptTokens, long completionTokens, long totalTokens) {

    public static Usage empty() {
        return new Usage(0, 0, 0);
    }
}
