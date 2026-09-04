package com.agent.platform.service.knowledge;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.knowledge.KnowledgeChunk;
import com.agent.platform.dao.entity.knowledge.KnowledgeDataset;
import com.agent.platform.dao.entity.knowledge.KnowledgeDocument;
import com.agent.platform.dao.mapper.knowledge.KnowledgeChunkMapper;
import com.agent.platform.dao.mapper.knowledge.KnowledgeDatasetMapper;
import com.agent.platform.dao.mapper.knowledge.KnowledgeDocumentMapper;
import com.agent.platform.dao.vo.knowledge.KnowledgeSearchHitVO;
import com.agent.platform.llm.model.EmbeddingResult;
import com.agent.platform.llm.model.RerankResult;
import com.agent.platform.llm.spi.EmbeddingModel;
import com.agent.platform.llm.spi.RerankModel;
import com.agent.platform.service.model.ModelRuntimeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 知识库服务：数据集/文档/分块 CRUD + 文本分块 + 向量化入库 + 向量检索（内存余弦相似度）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDatasetMapper datasetMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final ModelRuntimeService modelRuntimeService;
    private final ObjectMapper objectMapper;

    // ---------- 数据集 ----------

    public Page<KnowledgeDataset> datasetPage(long page, long size) {
        return datasetMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<KnowledgeDataset>().orderByDesc(KnowledgeDataset::getId));
    }

    public KnowledgeDataset getDataset(Long id) {
        KnowledgeDataset ds = datasetMapper.selectById(id);
        if (ds == null) {
            throw new BizException("数据集不存在: " + id);
        }
        return ds;
    }

    public KnowledgeDataset createDataset(KnowledgeDataset ds) {
        LocalDateTime now = LocalDateTime.now();
        ds.setId(null);
        if (ds.getTenantId() == null) {
            ds.setTenantId(1L);
        }
        if (ds.getChunkSize() == null || ds.getChunkSize() <= 0) {
            ds.setChunkSize(500);
        }
        if (ds.getChunkOverlap() == null || ds.getChunkOverlap() < 0) {
            ds.setChunkOverlap(50);
        }
        if (ds.getStatus() == null) {
            ds.setStatus(1);
        }
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        datasetMapper.insert(ds);
        return ds;
    }

    public void updateDataset(KnowledgeDataset ds) {
        getDataset(ds.getId());
        ds.setUpdateTime(LocalDateTime.now());
        datasetMapper.updateById(ds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long id) {
        getDataset(id);
        // 回收站软删：保留文档与分段以便完整恢复，彻底删除在回收站执行
        datasetMapper.markDeleted(id);
    }

    // ---------- 文档 ----------

    public Page<KnowledgeDocument> documentPage(Long datasetId, long page, long size) {
        return documentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getDatasetId, datasetId)
                        .orderByDesc(KnowledgeDocument::getId));
    }

    public KnowledgeDocument getDocument(Long id) {
        KnowledgeDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在: " + id);
        }
        return doc;
    }

    /**
     * 创建文档并立即向量化入库（同步）。
     * 文本过长或向量化失败时文档标记为 failed，可重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createDocument(Long datasetId, String name, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("文档内容不能为空");
        }
        KnowledgeDataset ds = getDataset(datasetId);
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setDatasetId(datasetId);
        doc.setName(name == null || name.isBlank() ? "未命名文档" : name);
        doc.setContent(content);
        doc.setCharCount(content.length());
        doc.setStatus("indexing");
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(doc);

        try {
            List<String> chunks = splitText(content, ds.getChunkSize(), ds.getChunkOverlap());
            EmbeddingModel embedModel = modelRuntimeService.embeddingModelOf(ds.getEmbeddingModel());
            // 分批向量化，避免单次请求过大
            int batchSize = 16;
            int index = 0;
            for (int i = 0; i < chunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunks.size());
                List<String> batch = chunks.subList(i, end);
                EmbeddingResult result = embedModel.embed(batch);
                for (int j = 0; j < batch.size(); j++) {
                    KnowledgeChunk chunk = new KnowledgeChunk();
                    chunk.setDatasetId(datasetId);
                    chunk.setDocumentId(doc.getId());
                    chunk.setChunkIndex(index++);
                    chunk.setContent(batch.get(j));
                    chunk.setCharCount(batch.get(j).length());
                    chunk.setVector(toJson(result.vectors().get(j)));
                    chunk.setCreateTime(LocalDateTime.now());
                    chunkMapper.insert(chunk);
                }
            }
            doc.setChunkCount(index);
            doc.setStatus("ready");
            doc.setErrorMsg(null);
        } catch (Exception e) {
            log.error("文档向量化失败 docId={}", doc.getId(), e);
            doc.setStatus("failed");
            doc.setErrorMsg(e.getMessage());
        }
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(doc);
        return doc;
    }

    /**
     * 从上传文件解析文本并创建文档（同步向量化）。
     * 支持格式：txt / md / pdf / docx
     */
    public KnowledgeDocument createDocumentFromFile(Long datasetId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "未命名文档";
        }
        String lower = filename.toLowerCase();
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot + 1) : "";
        String content;
        try {
            switch (ext) {
                case "txt":
                case "md":
                case "markdown":
                    content = readPlainText(file);
                    break;
                case "pdf":
                    content = readPdf(file);
                    break;
                case "docx":
                    content = readDocx(file);
                    break;
                default:
                    throw new BizException("暂不支持的文件格式: ." + ext + "（支持 txt / md / pdf / docx）");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析文件失败 file={}", filename, e);
            throw new BizException("解析文件失败: " + e.getMessage());
        }
        if (content == null || content.isBlank()) {
            throw new BizException("未能从文件中提取到文本内容");
        }
        return createDocument(datasetId, filename, content);
    }

    /** 读取纯文本：优先 UTF-8 严格解码，失败回退 GBK */
    private String readPlainText(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return utf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    /** PDF 文本提取（PDFBox） */
    private String readPdf(MultipartFile file) throws IOException {
        // 替换掉旧的 PDDocument.load(file.getBytes())
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /** docx 文本提取（POI：段落 + 表格） */
    private String readDocx(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append('\n');
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(cell.getText().trim());
                    }
                    sb.append(String.join(" | ", cells)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** 重新向量化（用于失败后重试或更换模型后重建） */
    public KnowledgeDocument reindexDocument(Long documentId) {
        KnowledgeDocument doc = getDocument(documentId);
        // 删除旧分块
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocumentId, documentId));
        return createDocument(doc.getDatasetId(), doc.getName(), doc.getContent());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id) {
        KnowledgeDocument doc = getDocument(id);
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocumentId, id));
        documentMapper.deleteById(id);
    }

    // ---------- 分块 ----------

    public List<KnowledgeChunk> chunks(Long documentId) {
        getDocument(documentId);
        return chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    // ---------- 检索 ----------

    /**
     * 向量检索：对 query 向量化 → 与数据集所有分块计算余弦相似度 → 取 topK。
     * 可选 rerankModelId 时，对 topK 候选再用 rerank 模型精排。
     */
    public List<KnowledgeSearchHitVO> search(Long datasetId, String query, int topK, Long rerankModelId) {
        KnowledgeDataset ds = getDataset(datasetId);
        if (query == null || query.isBlank()) {
            return List.of();
        }
        EmbeddingModel embedModel = modelRuntimeService.embeddingModelOf(ds.getEmbeddingModel());
        float[] queryVec = embedModel.embed(List.of(query)).vectors().get(0);

        List<KnowledgeChunk> all = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDatasetId, datasetId));
        List<KnowledgeSearchHitVO> hits = new ArrayList<>();
        for (KnowledgeChunk c : all) {
            float[] vec = fromJson(c.getVector());
            if (vec == null) {
                continue;
            }
            double score = cosine(queryVec, vec);
            hits.add(new KnowledgeSearchHitVO(c.getId(), c.getDocumentId(), c.getChunkIndex(), c.getContent(), score));
        }
        hits.sort(Comparator.comparingDouble(KnowledgeSearchHitVO::getScore).reversed());
        List<KnowledgeSearchHitVO> top = hits.size() > topK ? hits.subList(0, topK) : hits;

        // 可选 rerank 精排
        if (rerankModelId != null && !top.isEmpty()) {
            try {
                RerankModel rerank = modelRuntimeService.rerankModelOf(rerankModelId);
                List<String> docs = new ArrayList<>();
                for (KnowledgeSearchHitVO h : top) {
                    docs.add(h.getContent());
                }
                List<RerankResult> reranked = rerank.rerank(query, docs, top.size());
                List<KnowledgeSearchHitVO> rerankedHits = new ArrayList<>();
                for (RerankResult r : reranked) {
                    if (r.index() >= 0 && r.index() < top.size()) {
                        KnowledgeSearchHitVO origin = top.get(r.index());
                        rerankedHits.add(new KnowledgeSearchHitVO(origin.getId(), origin.getDocumentId(),
                                origin.getChunkIndex(), origin.getContent(), r.score()));
                    }
                }
                return rerankedHits;
            } catch (Exception e) {
                log.warn("rerank 失败，使用向量检索结果: {}", e.getMessage());
            }
        }
        return top;
    }

    // ---------- 工具方法 ----------

    /** 文本分块：按字符长度滑动窗口，简单按段落/句号优化切分点 */
    static List<String> splitText(String text, int chunkSize, int overlap) {
        if (chunkSize <= 0) chunkSize = 500;
        if (overlap < 0) overlap = 0;
        List<String> result = new ArrayList<>();
        int len = text.length();
        if (len <= chunkSize) {
            result.add(text);
            return result;
        }
        int step = chunkSize - overlap;
        if (step <= 0) step = chunkSize;
        int i = 0;
        while (i < len) {
            int end = Math.min(i + chunkSize, len);
            // 尝试在句号/换行处切分
            int cut = end;
            if (end < len) {
                for (int k = end; k > i + overlap && k > i; k--) {
                    char ch = text.charAt(k - 1);
                    if (ch == '\n' || ch == '。' || ch == '.' || ch == '！' || ch == '？' || ch == '!' || ch == '?') {
                        cut = k;
                        break;
                    }
                }
            }
            result.add(text.substring(i, cut).trim());
            i = cut;
            if (cut >= len) break;
            if (cut - i < 0) i = end; // 兜底防死循环
        }
        return result;
    }

    /** 余弦相似度 */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private float[] fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            return null;
        }
    }

}
