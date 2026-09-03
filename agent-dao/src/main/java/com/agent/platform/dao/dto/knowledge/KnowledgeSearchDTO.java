package com.agent.platform.dao.dto.knowledge;

import lombok.Data;

/**
 * 知识库检索入参
 */
@Data
public class KnowledgeSearchDTO {
    private String query;
    private Integer topK;
    private Long rerankModelId;
}
