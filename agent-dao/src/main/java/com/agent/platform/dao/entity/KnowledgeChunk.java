package com.agent.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分块（含向量）
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private Long documentId;

    /** 块序号（文档内自增） */
    private Integer chunkIndex;

    private String content;

    /** 向量（JSON float 数组） */
    private String vector;

    private Integer charCount;

    private LocalDateTime createTime;
}
