package com.agent.platform.orchestrator.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.orchestrator.NodeType;
import com.agent.platform.orchestrator.spi.KnowledgeHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索节点：按检索词模板渲染查询，调用知识库语义检索，
 * 输出拼接的命中片段文本，供下游 LLM / 模板节点使用。
 * <p>
 * 支持配置项：
 * <ul>
 *   <li>{@code datasetId} 数据集（必填）</li>
 *   <li>{@code queryTemplate} 检索词模板，默认 {{input}}</li>
 *   <li>{@code topK} 召回数量；{@code rerankModelId} 重排模型</li>
 *   <li>{@code scoreThreshold} 相似度阈值（0~1），低于阈值的片段丢弃</li>
 *   <li>{@code outputFormat} text（默认，按模板拼接）/ json（结构化数组）</li>
 *   <li>{@code itemTemplate} 单条片段模板，支持 {{index}} {{content}} {{score}} {{documentId}}</li>
 *   <li>{@code separator} 片段分隔符，默认两个换行</li>
 * </ul>
 */
@Component
public class KnowledgeNodeHandler implements NodeHandler {

    private static final String DEFAULT_ITEM_TEMPLATE = "【片段 {{index}}】{{content}}";

    @Override
    public NodeType type() {
        return NodeType.KNOWLEDGE;
    }

    @Override
    public List<NodeField> fields() {
        return List.of(
                NodeField.builder().key("datasetId").label("数据集").type("knowledge").required(true).build(),
                NodeField.text("queryTemplate", "检索词模板").description("按模板渲染后作为检索词").defaultValue("{{input}}"),
                NodeField.number("topK", "召回数量").defaultValue(3),
                NodeField.builder().key("rerankModelId").label("重排模型").type("model").description("可选").build(),
                NodeField.number("scoreThreshold", "相似度阈值").description("0~1，低于阈值片段丢弃").defaultValue(0),
                NodeField.builder().key("outputFormat").label("输出格式").type("select")
                        .options(List.of(NodeField.option("文本（按模板拼接）", "text"), NodeField.option("JSON 数组", "json")))
                        .defaultValue("text").build(),
                NodeField.textarea("itemTemplate", "单条片段模板")
                        .description("支持 {{index}} {{content}} {{score}} {{documentId}}")
                        .defaultValue(DEFAULT_ITEM_TEMPLATE),
                NodeField.text("separator", "片段分隔符").defaultValue("\n\n"));
    }

    @Override
    public String validate(NodeContext ctx) {
        if (ctx.cfgLong("datasetId") == null) {
            return "知识库检索节点「" + ctx.label() + "」未选择数据集";
        }
        return null;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        Long datasetId = ctx.cfgLong("datasetId");
        if (datasetId == null) {
            throw new BizException("知识库检索节点「" + ctx.label() + "」未选择数据集");
        }
        String query = ctx.render(ctx.cfgStr("queryTemplate", "{{input}}"));
        int topK = ctx.cfgInt("topK", 3);
        Long rerankModelId = ctx.cfgLong("rerankModelId");
        double threshold = ctx.cfgDouble("scoreThreshold") == null ? 0.0 : ctx.cfgDouble("scoreThreshold");

        List<KnowledgeHit> hits = ctx.knowledgeProvider().search(datasetId, query, topK, rerankModelId);
        List<KnowledgeHit> kept = new ArrayList<>();
        if (hits != null) {
            for (KnowledgeHit h : hits) {
                if (h.getScore() >= threshold) {
                    kept.add(h);
                }
            }
        }

        String text = "json".equalsIgnoreCase(ctx.cfgStr("outputFormat", "text"))
                ? toJson(ctx, kept)
                : toText(ctx, kept);
        ctx.emit(text);
        return NodeResult.of(text);
    }

    /** 按片段模板拼接文本 */
    private String toText(NodeContext ctx, List<KnowledgeHit> hits) {
        if (hits.isEmpty()) {
            return "";
        }
        String itemTemplate = ctx.cfgStr("itemTemplate", DEFAULT_ITEM_TEMPLATE);
        String separator = ctx.cfgStr("separator", "\n\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            KnowledgeHit h = hits.get(i);
            sb.append(itemTemplate
                    .replace("{{index}}", String.valueOf(i + 1))
                    .replace("{{content}}", h.getContent() == null ? "" : h.getContent())
                    .replace("{{score}}", String.format("%.4f", h.getScore()))
                    .replace("{{documentId}}", String.valueOf(h.getDocumentId())));
        }
        return sb.toString();
    }

    /** 结构化输出：[{index, content, score, documentId, chunkIndex}] */
    private String toJson(NodeContext ctx, List<KnowledgeHit> hits) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeHit h = hits.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i + 1);
            m.put("content", h.getContent());
            m.put("score", h.getScore());
            m.put("documentId", h.getDocumentId());
            m.put("chunkIndex", h.getChunkIndex());
            list.add(m);
        }
        try {
            return ctx.objectMapper().writeValueAsString(list);
        } catch (Exception e) {
            throw new BizException("知识库检索节点「" + ctx.label() + "」序列化结果失败：" + e.getMessage());
        }
    }

    @Override
    public String describeInput(NodeContext ctx) {
        return ctx.render(ctx.cfgStr("queryTemplate", "{{input}}")).trim();
    }
}
