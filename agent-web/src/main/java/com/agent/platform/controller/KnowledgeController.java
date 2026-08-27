package com.agent.platform.controller;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.KnowledgeChunk;
import com.agent.platform.dao.entity.KnowledgeDataset;
import com.agent.platform.dao.entity.KnowledgeDocument;
import com.agent.platform.service.KnowledgeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库接口：数据集/文档/分块管理 + 检索
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    // ---------- 数据集 ----------

    @GetMapping("/datasets")
    public Result<Page<KnowledgeDataset>> datasetPage(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size) {
        return Result.ok(knowledgeService.datasetPage(page, size));
    }

    @GetMapping("/datasets/{id}")
    public Result<KnowledgeDataset> getDataset(@PathVariable Long id) {
        return Result.ok(knowledgeService.getDataset(id));
    }

    @PostMapping("/datasets")
    public Result<KnowledgeDataset> createDataset(@RequestBody KnowledgeDataset ds) {
        return Result.ok(knowledgeService.createDataset(ds));
    }

    @PutMapping("/datasets/{id}")
    public Result<Void> updateDataset(@PathVariable Long id, @RequestBody KnowledgeDataset ds) {
        ds.setId(id);
        knowledgeService.updateDataset(ds);
        return Result.ok();
    }

    @DeleteMapping("/datasets/{id}")
    public Result<Void> deleteDataset(@PathVariable Long id) {
        knowledgeService.deleteDataset(id);
        return Result.ok();
    }

    // ---------- 文档 ----------

    @GetMapping("/datasets/{datasetId}/documents")
    public Result<Page<KnowledgeDocument>> documentPage(@PathVariable Long datasetId,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        return Result.ok(knowledgeService.documentPage(datasetId, page, size));
    }

    @PostMapping("/datasets/{datasetId}/documents")
    public Result<KnowledgeDocument> createDocument(@PathVariable Long datasetId,
                                                    @RequestBody CreateDocReq req) {
        return Result.ok(knowledgeService.createDocument(datasetId, req.getName(), req.getContent()));
    }

    /** 上传文件并解析（支持 txt / md / pdf / docx） */
    @PostMapping(value = "/datasets/{datasetId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocument> uploadDocument(@PathVariable Long datasetId,
                                                    @RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeService.createDocumentFromFile(datasetId, file));
    }

    @PostMapping("/documents/{id}/reindex")
    public Result<KnowledgeDocument> reindex(@PathVariable Long id) {
        return Result.ok(knowledgeService.reindexDocument(id));
    }

    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return Result.ok();
    }

    // ---------- 分块 ----------

    @GetMapping("/documents/{documentId}/chunks")
    public Result<List<KnowledgeChunk>> chunks(@PathVariable Long documentId) {
        return Result.ok(knowledgeService.chunks(documentId));
    }

    // ---------- 检索 ----------

    @PostMapping("/datasets/{datasetId}/search")
    public Result<List<KnowledgeService.SearchHit>> search(@PathVariable Long datasetId,
                                                           @RequestBody SearchReq req) {
        int topK = req.getTopK() == null ? 3 : req.getTopK();
        return Result.ok(knowledgeService.search(datasetId, req.getQuery(), topK, req.getRerankModelId()));
    }

    @Data
    public static class CreateDocReq {
        private String name;
        private String content;
    }

    @Data
    public static class SearchReq {
        private String query;
        private Integer topK;
        private Long rerankModelId;
    }
}
