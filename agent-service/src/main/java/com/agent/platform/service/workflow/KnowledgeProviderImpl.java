package com.agent.platform.service.workflow;

import com.agent.platform.workflow.spi.KnowledgeHit;
import com.agent.platform.workflow.spi.KnowledgeProvider;
import com.agent.platform.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link KnowledgeProvider} 的业务实现：委托给 {@link KnowledgeService}，并转换为引擎侧模型。
 * <p>
 * 本类是 agent-service 与 agent-workflow 之间的适配层，
 * 使图引擎无需依赖知识库实现即可执行语义检索。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeProviderImpl implements KnowledgeProvider {

    private final KnowledgeService knowledgeService;

    @Override
    public List<KnowledgeHit> search(Long datasetId, String query, int topK, Long rerankModelId) {
        return knowledgeService.search(datasetId, query, topK, rerankModelId).stream()
                .map(h -> new KnowledgeHit(h.getId(), h.getDocumentId(), h.getChunkIndex(), h.getContent(), h.getScore()))
                .toList();
    }
}
