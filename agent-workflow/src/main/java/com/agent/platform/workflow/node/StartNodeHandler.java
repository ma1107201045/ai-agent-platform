package com.agent.platform.graph.node;

import com.agent.platform.graph.NodeType;
import org.springframework.stereotype.Component;

/**
 * 开始节点：流程入口，无实际执行逻辑。
 */
@Component
public class StartNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.START;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        return NodeResult.empty();
    }
}
