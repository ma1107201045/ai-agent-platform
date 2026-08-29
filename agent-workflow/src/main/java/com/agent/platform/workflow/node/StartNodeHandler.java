package com.agent.platform.workflow.node;

import com.agent.platform.workflow.NodeType;
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
