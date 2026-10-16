package com.agent.platform.service.knowledge;

import com.agent.platform.dao.entity.knowledge.KnowledgeChunk;
import com.agent.platform.dao.entity.knowledge.KnowledgeDataset;
import com.agent.platform.dao.entity.knowledge.KnowledgeDocument;
import com.agent.platform.dao.mapper.knowledge.KnowledgeChunkMapper;
import com.agent.platform.dao.mapper.knowledge.KnowledgeDatasetMapper;
import com.agent.platform.dao.mapper.knowledge.KnowledgeDocumentMapper;
import com.agent.platform.llm.model.EmbeddingResult;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.service.model.ModelRuntimeService;
import com.agent.platform.service.knowledge.impl.KnowledgeServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * reindexDocument 回归测试：重建索引必须复用原文档行，不得新建文档。
 */
class KnowledgeServiceReindexTest {

    private KnowledgeDatasetMapper datasetMapper;
    private KnowledgeDocumentMapper documentMapper;
    private KnowledgeChunkMapper chunkMapper;
    private ModelRuntimeService modelRuntimeService;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        datasetMapper = mock(KnowledgeDatasetMapper.class);
        documentMapper = mock(KnowledgeDocumentMapper.class);
        chunkMapper = mock(KnowledgeChunkMapper.class);
        modelRuntimeService = mock(ModelRuntimeService.class);
        service = new KnowledgeServiceImpl(datasetMapper, documentMapper, chunkMapper,
                modelRuntimeService, new ObjectMapper());
    }

    private KnowledgeDocument existingDoc() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setDatasetId(10L);
        doc.setName("原文档");
        doc.setContent("这是一段用于重建索引的短文本。");
        doc.setStatus("failed");
        doc.setChunkCount(3);
        return doc;
    }

    private KnowledgeDataset dataset() {
        KnowledgeDataset ds = new KnowledgeDataset();
        ds.setId(10L);
        ds.setChunkSize(500);
        ds.setChunkOverlap(50);
        ds.setEmbeddingModel(99L);
        return ds;
    }

    @Test
    void reindexReusesSameDocumentRow() {
        when(documentMapper.selectById(1L)).thenReturn(existingDoc());
        when(datasetMapper.selectById(10L)).thenReturn(dataset());
        EmbeddingModel embedModel = mock(EmbeddingModel.class);
        when(embedModel.embed(anyList())).thenReturn(new EmbeddingResult(List.of(new float[]{1f, 0f}), null));
        when(modelRuntimeService.embeddingModelOf(99L)).thenReturn(embedModel);

        KnowledgeDocument result = service.reindexDocument(1L);

        // 不新建文档行
        verify(documentMapper, never()).insert(any());
        // 删除旧分块
        verify(chunkMapper).delete(argThat(w -> w != null));
        // 复用原行 id
        assertEquals(1L, result.getId());
        assertEquals("ready", result.getStatus());
        // 新分块仍挂在原文档上
        ArgumentCaptor<KnowledgeChunk> chunkCaptor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper, atLeastOnce()).insert(chunkCaptor.capture());
        assertEquals(1L, chunkCaptor.getValue().getDocumentId());
        assertEquals(10L, chunkCaptor.getValue().getDatasetId());
    }

    @Test
    void reindexFailureMarksDocFailedOnSameRow() {
        when(documentMapper.selectById(1L)).thenReturn(existingDoc());
        when(datasetMapper.selectById(10L)).thenReturn(dataset());
        when(modelRuntimeService.embeddingModelOf(99L)).thenThrow(new RuntimeException("模型不可用"));

        KnowledgeDocument result = service.reindexDocument(1L);

        verify(documentMapper, never()).insert(any());
        assertEquals(1L, result.getId());
        assertEquals("failed", result.getStatus());
        assertNotNull(result.getErrorMsg());
    }
}
