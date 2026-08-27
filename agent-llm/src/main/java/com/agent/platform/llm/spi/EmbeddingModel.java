package com.agent.platform.llm.spi;

import com.agent.platform.llm.model.EmbeddingResult;

import java.util.List;

/**
 * 向量模型 SPI
 */
public interface EmbeddingModel {

    String name();

    EmbeddingResult embed(List<String> texts);
}
