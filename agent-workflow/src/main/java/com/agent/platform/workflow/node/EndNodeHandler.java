package com.agent.platform.workflow.node;

import com.agent.platform.workflow.NodeType;
import org.springframework.stereotype.Component;

/**
 * 结束节点：流程出口，无实际执行逻辑。
 * 最终回答由引擎从 end 节点的上游输出中选取。
 */
@Component
public class EndNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.END;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        return NodeResult.empty();
    }
}
