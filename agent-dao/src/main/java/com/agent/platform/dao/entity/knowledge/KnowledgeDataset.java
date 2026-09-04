package com.agent.platform.dao.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库数据集
 */
@Data
@TableName("knowledge_dataset")
public class KnowledgeDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    /** 向量化模型 ID */
    private Long embeddingModel;

    /** 分块大小（字符） */
    private Integer chunkSize;

    /** 分块重叠（字符） */
    private Integer chunkOverlap;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0正常 1回收站 */
    @TableLogic
    private Integer deleted;

    /** 删除时间 */
    private LocalDateTime deletedTime;
}
