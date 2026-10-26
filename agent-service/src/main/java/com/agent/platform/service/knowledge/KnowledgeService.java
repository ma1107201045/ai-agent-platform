package com.agent.platform.service.knowledge;

import com.agent.platform.dao.entity.knowledge.KnowledgeChunk;
import com.agent.platform.dao.entity.knowledge.KnowledgeDataset;
import com.agent.platform.dao.entity.knowledge.KnowledgeDocument;
import com.agent.platform.dao.vo.knowledge.KnowledgeSearchHitVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * 知识库服务：数据集/文档/分块 CRUD + 文本分块 + 向量化入库 + 向量检索（内存余弦相似度）
 */
public interface KnowledgeService {

    Page<KnowledgeDataset> datasetPage(long page, long size);

    KnowledgeDataset getDataset(Long id);

    KnowledgeDataset createDataset(KnowledgeDataset ds);

    void updateDataset(KnowledgeDataset ds);

    void deleteDataset(Long id);

    Page<KnowledgeDocument> documentPage(Long datasetId, long page, long size);

    KnowledgeDocument getDocument(Long id);

    /**
     * 创建文档并立即向量化入库（同步）。
     * 文本过长或向量化失败时文档标记为 failed，可重试。
     */
    KnowledgeDocument createDocument(Long datasetId, String name, String content);

    /**
     * 从上传文件解析文本并创建文档（同步向量化）。
     * 支持格式：txt / md / pdf / docx
     */
    KnowledgeDocument createDocumentFromFile(Long datasetId, MultipartFile file);

    /**
     * 重新向量化（用于失败后重试或更换模型后重建）。
     * 在原文档行上重建分块，不新建文档行。
     */
    KnowledgeDocument reindexDocument(Long documentId);

    void deleteDocument(Long id);

    List<KnowledgeChunk> chunks(Long documentId);

    /**
     * 向量检索：对 query 向量化 → pgvector 在库内按余弦距离排序取 topK。
     * 可选 rerankModelId 时，对 topK 候选再用 rerank 模型精排。
     */
    List<KnowledgeSearchHitVO> search(Long datasetId, String query, int topK, Long rerankModelId);
}
