package com.agent.platform.dao.mapper.knowledge;

import com.agent.platform.dao.entity.knowledge.KnowledgeDataset;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库数据集 Mapper
 */
@Mapper
public interface KnowledgeDatasetMapper extends BaseMapper<KnowledgeDataset> {

    /** 软删除：移入回收站并记录删除时间 */
    @Update("UPDATE knowledge_dataset SET deleted = 1, deleted_time = NOW() WHERE id = #{id} AND deleted = 0")
    int markDeleted(@Param("id") Long id);
}
