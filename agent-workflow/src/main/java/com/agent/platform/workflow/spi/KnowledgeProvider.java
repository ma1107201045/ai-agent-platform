package com.agent.platform.workflow.spi;

import java.util.List;

/**
 * 知识库检索能力 SPI（由业务模块实现并注入）
 * <p>
 * 引擎只依赖本接口，不依赖具体的知识库实现，从而与业务模块解耦。
 */
public interface KnowledgeProvider {

    /**
     * 语义检索
     *
     * @param datasetId     数据集 ID
     * @param query         检索词（已渲染）
     * @param topK          返回条数
     * @param rerankModelId 重排模型 ID，可为 null
     */
    List<KnowledgeHit> search(Long datasetId, String query, int topK, Long rerankModelId);
}
