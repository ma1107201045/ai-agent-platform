package com.agent.platform.workflow.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.workflow.NodeType;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.workflow.spi.KnowledgeHit;
import com.agent.platform.llm.spi.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 节点：系统提示词 + 用户提示词（支持 {{input}} / {{节点id}} 变量替换），可选知识库检索增强。
 * <p>
 * 支持配置项：
 * <ul>
 *   <li>{@code modelId} 对话模型（必填）</li>
 *   <li>{@code systemPrompt} 系统提示词</li>
 *   <li>{@code userPrompt} 用户提示词模板，默认 {{input}}</li>
 *   <li>{@code temperature / topP / maxTokens} 采样参数</li>
 *   <li>{@code outputFormat} text（默认）/ json（要求模型返回 JSON 对象）</li>
 *   <li>{@code datasetId / topK / rerankModelId / scoreThreshold / knowledgeTemplate} 知识库增强</li>
 * </ul>
 */
@Component
public class LlmNodeHandler implements NodeHandler {

    /** 默认知识库上下文拼装模板，{{context}} 替换为命中片段 */
    private static final String DEFAULT_KNOWLEDGE_TEMPLATE =
            "\n\n以下是与用户问题相关的知识库参考资料，请据此回答：\n\n{{context}}";

    @Override
    public NodeType type() {
        return NodeType.LLM;
    }

    @Override
    public String validate(NodeContext ctx) {
        if (ctx.cfgLong("modelId") == null) {
            return "LLM 节点「" + ctx.label() + "」未配置模型";
        }
        return null;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        Long modelId = ctx.cfgLong("modelId");
        if (modelId == null) {
            throw new BizException("LLM 节点「" + ctx.label() + "」未配置模型");
        }
        String systemPrompt = ctx.cfgStr("systemPrompt", "You are a helpful assistant.");
        String userPrompt = ctx.cfgStr("userPrompt", "{{input}}");
        Double temperature = ctx.cfgDouble("temperature");
        Double topP = ctx.cfgDouble("topP");
        Integer maxTokens = ctx.cfgInt("maxTokens", 0) > 0 ? ctx.cfgInt("maxTokens", 0) : null;

        // 知识库检索增强：配置 datasetId 时，按检索词检索 topK 片段拼入 system 提示词
        String knowledgeContext = buildKnowledgeContext(ctx);

        ChatModel chatModel = ctx.modelProvider().chatModelOf(modelId);
        ChatRequest.ChatRequestBuilder builder = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(ctx.render(systemPrompt) + knowledgeContext),
                        ChatMessage.user(ctx.render(userPrompt))))
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens);
        if ("json".equalsIgnoreCase(ctx.cfgStr("outputFormat", "text"))) {
            builder.responseFormat(Map.of("type", "json_object"));
        }
        ChatResponse response = chatModel.call(builder.build());
        String content = response == null ? null : response.getContent();
        ctx.emit(content);
        return NodeResult.of(content);
    }

    /**
     * 知识库检索增强上下文；检索失败不中断流程，仅把提示写入上下文。
     * 命中片段按 knowledgeTemplate 拼装（默认模板见 {@link #DEFAULT_KNOWLEDGE_TEMPLATE}）。
     */
    private String buildKnowledgeContext(NodeContext ctx) {
        Long datasetId = ctx.cfgLong("datasetId");
        if (datasetId == null) {
            return "";
        }
        int topK = ctx.cfgInt("topK", 3);
        Long rerankModelId = ctx.cfgLong("rerankModelId");
        double threshold = ctx.cfgDouble("scoreThreshold") == null ? 0.0 : ctx.cfgDouble("scoreThreshold");
        String query = ctx.render(ctx.cfgStr("queryTemplate", "{{input}}"));
        try {
            List<KnowledgeHit> hits = ctx.knowledgeProvider().search(datasetId, query, topK, rerankModelId);
            List<KnowledgeHit> kept = new ArrayList<>();
            for (KnowledgeHit h : hits) {
                if (h.getScore() >= threshold) {
                    kept.add(h);
                }
            }
            if (kept.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < kept.size(); i++) {
                sb.append("[").append(i + 1).append("] ")
                        .append(kept.get(i).getContent().strip())
                        .append("\n\n");
            }
            String template = ctx.cfgStr("knowledgeTemplate", DEFAULT_KNOWLEDGE_TEMPLATE);
            return template.replace("{{context}}", sb.toString());
        } catch (Exception e) {
            return "\n\n[知识库检索失败: " + e.getMessage() + "]\n";
        }
    }

    @Override
    public String describeInput(NodeContext ctx) {
        return ctx.render(ctx.cfgStr("userPrompt", "{{input}}")).trim();
    }
}
