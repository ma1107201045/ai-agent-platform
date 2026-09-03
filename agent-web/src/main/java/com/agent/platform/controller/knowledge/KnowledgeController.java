package com.agent.platform.controller.knowledge;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.knowledge.KnowledgeCreateDocDTO;
import com.agent.platform.dao.dto.knowledge.KnowledgeSearchDTO;
import com.agent.platform.dao.entity.knowledge.KnowledgeChunk;
import com.agent.platform.dao.entity.knowledge.KnowledgeDataset;
import com.agent.platform.dao.entity.knowledge.KnowledgeDocument;
import com.agent.platform.dao.vo.knowledge.SearchHitVO;
import com.agent.platform.service.knowledge.KnowledgeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
                                                    @RequestBody KnowledgeCreateDocDTO req) {
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
    public Result<List<SearchHitVO>> search(@PathVariable Long datasetId,
                                            @RequestBody KnowledgeSearchDTO req) {
        int topK = req.getTopK() == null ? 3 : req.getTopK();
        return Result.ok(knowledgeService.search(datasetId, req.getQuery(), topK, req.getRerankModelId()));
    }
}
