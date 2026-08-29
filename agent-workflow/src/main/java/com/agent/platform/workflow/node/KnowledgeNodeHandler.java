package com.agent.platform.workflow.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.workflow.NodeType;
import com.agent.platform.workflow.spi.KnowledgeHit;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库检索节点：按检索词模板渲染查询，调用知识库语义检索，
 * 输出拼接的命中片段文本，供下游 LLM / 模板节点使用。
 */
@Component
public class KnowledgeNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.KNOWLEDGE;
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

        List<KnowledgeHit> hits =
                ctx.knowledgeProvider().search(datasetId, query, topK, rerankModelId);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append("【片段 ").append(i + 1).append("】").append(hits.get(i).getContent());
        }
        String text = sb.toString();
        ctx.emit(text);
        return NodeResult.of(text);
    }

    @Override
    public String describeInput(NodeContext ctx) {
        return ctx.render(ctx.cfgStr("queryTemplate", "{{input}}")).trim();
    }
}
