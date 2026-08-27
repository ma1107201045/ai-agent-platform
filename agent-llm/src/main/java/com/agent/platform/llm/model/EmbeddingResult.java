package com.agent.platform.llm.model;

import java.util.List;

/**
 * 向量化结果
 */
public record EmbeddingResult(List<float[]> vectors, Usage usage) {
}
