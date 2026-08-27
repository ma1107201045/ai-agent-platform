package com.agent.platform.llm.model;

/**
 * 重排序结果
 */
public record RerankResult(int index, double score, String text) {
}
