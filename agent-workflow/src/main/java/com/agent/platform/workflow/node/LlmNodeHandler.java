package com.agent.platform.workflow.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.workflow.NodeType;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.workflow.spi.KnowledgeHit;
import com.agent.platform.llm.spi.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 节点：系统提示词 + 用户输入（支持 {{input}} / {{节点id}} 变量替换），可选知识库检索增强。
 */
@Component
public class LlmNodeHandler implements NodeHandler {

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
        Double temperature = ctx.cfgDouble("temperature");

        // 知识库检索增强：配置 datasetId 时，按用户输入检索 topK 片段拼入 system 提示词
        String knowledgeContext = buildKnowledgeContext(ctx);

        ChatModel chatModel = ctx.modelProvider().chatModelOf(modelId);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(ctx.render(systemPrompt) + knowledgeContext),
                        ChatMessage.user(ctx.render(ctx.userInput()))))
                .temperature(temperature)
                .build();
        ChatResponse response = chatModel.call(request);
        String content = response == null ? null : response.getContent();
        ctx.emit(content);
        return NodeResult.of(content);
    }

    /** 知识库检索增强上下文；检索失败不中断流程，仅把提示写入上下文 */
    private String buildKnowledgeContext(NodeContext ctx) {
        Long datasetId = ctx.cfgLong("datasetId");
        if (datasetId == null) {
            return "";
        }
        int topK = ctx.cfgInt("topK", 3);
        Long rerankModelId = ctx.cfgLong("rerankModelId");
        try {
            List<KnowledgeHit> hits =
                    ctx.knowledgeProvider().search(datasetId, ctx.userInput(), topK, rerankModelId);
            if (hits.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("\n\n以下是与用户问题相关的知识库参考资料，请据此回答：\n\n");
            for (int i = 0; i < hits.size(); i++) {
                sb.append("[").append(i + 1).append("] ")
                        .append(hits.get(i).getContent().strip())
                        .append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "\n\n[知识库检索失败: " + e.getMessage() + "]\n";
        }
    }

    @Override
    public String describeInput(NodeContext ctx) {
        return ctx.render(ctx.label()).trim();
    }
}
