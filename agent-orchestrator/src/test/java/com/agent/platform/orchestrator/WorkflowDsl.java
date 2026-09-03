package com.agent.platform.orchestrator;

import com.agent.platform.orchestrator.WorkflowGraph.WorkflowEdge;
import com.agent.platform.orchestrator.WorkflowGraph.WorkflowNode;

import java.util.Map;
import java.util.UUID;

/** 测试用 DSL 构建工具 */
public final class WorkflowDsl {

    private WorkflowDsl() {
    }

    public static WorkflowNode node(String id, NodeType type) {
        return node(id, type, Map.of());
    }

    public static WorkflowNode node(String id, NodeType type, Map<String, Object> config) {
        WorkflowNode n = new WorkflowNode();
        n.setId(id);
        n.setType(type.getCode());
        n.setLabel(id);
        n.setConfig(config);
        return n;
    }

    public static WorkflowEdge edge(String from, String to) {
        return edge(from, to, null);
    }

    public static WorkflowEdge edge(String from, String to, String sourceHandle) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(UUID.randomUUID().toString());
        e.setSource(from);
        e.setTarget(to);
        e.setSourceHandle(sourceHandle);
        return e;
    }

    public static WorkflowGraph graph(WorkflowNode[] nodes, WorkflowEdge[] edges) {
        WorkflowGraph g = new WorkflowGraph();
        g.setNodes(java.util.List.of(nodes));
        g.setEdges(edges == null ? java.util.List.of() : java.util.List.of(edges));
        return g;
    }
}
