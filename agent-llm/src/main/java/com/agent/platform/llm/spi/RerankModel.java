package com.agent.platform.llm.spi;

import com.agent.platform.llm.model.RerankResult;

import java.util.List;

/**
 * 重排序模型 SPI
 */
public interface RerankModel {

    String name();

    /**
     * 对文档列表按与 query 的相关性重排
     *
     * @param topN 返回前 N 条
     */
    List<RerankResult> rerank(String query, List<String> documents, int topN);
}
