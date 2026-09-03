package com.agent.platform.orchestrator.node;

import com.agent.platform.orchestrator.NodeType;
import org.springframework.stereotype.Component;

/**
 * 结束节点：流程出口，可选配置回答模板。
 * <p>
 * 未配置 {@code answerTemplate} 时由引擎从上一个节点的输出中选取最终回答。
 */
@Component
public class EndNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.END;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        String template = ctx.cfgStr("answerTemplate");
        if (template == null || template.isBlank()) {
            return NodeResult.empty();
        }
        String text = ctx.render(template);
        ctx.emit(text);
        return NodeResult.of(text);
    }
}
