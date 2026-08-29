package com.agent.platform.workflow.spi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库检索命中结果（引擎侧模型）
 * <p>
 * 由 {@link KnowledgeProvider} 返回，引擎不感知具体检索实现。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeHit {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private double score;
}
