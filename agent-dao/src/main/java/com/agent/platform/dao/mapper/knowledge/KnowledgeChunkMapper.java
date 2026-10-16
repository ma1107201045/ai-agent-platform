package com.agent.platform.dao.mapper.knowledge;

import com.agent.platform.dao.entity.knowledge.KnowledgeChunk;
import com.agent.platform.dao.vo.knowledge.KnowledgeSearchHitVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库分块 Mapper
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * pgvector 余弦检索：embedding <=> 查询向量为余弦距离，score = 1 - 距离（即余弦相似度）。
     * 在数据库内完成排序与 topK，不再全量加载到内存。
     */
    @Select("SELECT id, document_id, chunk_index, content, " +
            "(1 - (embedding <=> #{queryVector}::vector)) AS score " +
            "FROM knowledge_chunk " +
            "WHERE dataset_id = #{datasetId} AND embedding IS NOT NULL " +
            "ORDER BY embedding <=> #{queryVector}::vector " +
            "LIMIT #{topK}")
    List<KnowledgeSearchHitVO> searchNearest(@Param("datasetId") Long datasetId,
                                             @Param("queryVector") String queryVector,
                                             @Param("topK") int topK);
}
