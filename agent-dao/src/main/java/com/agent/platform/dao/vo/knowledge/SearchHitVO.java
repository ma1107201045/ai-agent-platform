package com.agent.platform.dao.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库检索命中结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHitVO {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private double score;
}
