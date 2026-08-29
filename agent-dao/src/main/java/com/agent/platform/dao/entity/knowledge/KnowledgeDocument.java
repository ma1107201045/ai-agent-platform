package com.agent.platform.dao.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String name;

    /** 原文内容 */
    private String content;

    private Integer charCount;

    private Integer chunkCount;

    /** 状态：pending/indexing/ready/failed */
    private String status;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
