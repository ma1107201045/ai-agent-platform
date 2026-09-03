package com.agent.platform.orchestrator;

import com.agent.platform.orchestrator.WorkflowGraph.WorkflowEdge;
import com.agent.platform.orchestrator.WorkflowGraph.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.agent.platform.orchestrator.WorkflowDsl.edge;
import static com.agent.platform.orchestrator.WorkflowDsl.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphValidatorTest {

    private final Map<String, WorkflowNode> nodeById = new HashMap<>();
    private final Map<String, List<WorkflowEdge>> outEdges = new HashMap<>();
    private final Map<String, List<WorkflowEdge>> inEdges = new HashMap<>();

    private void index(WorkflowNode[] nodes, WorkflowEdge[] edges) {
        for (WorkflowNode n : nodes) {
            nodeById.put(n.getId(), n);
        }
        for (WorkflowEdge e : edges) {
            outEdges.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(e);
            inEdges.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e);
        }
    }

    @Test
    void reachable_linearChain_visitsAll() {
        index(new WorkflowNode[]{node("start", NodeType.START), node("a", NodeType.TEMPLATE), node("end", NodeType.END)},
                new WorkflowEdge[]{edge("start", "a"), edge("a", "end")});
        Set<String> r = GraphValidator.bfsReachable(nodeById, outEdges, "start");
        assertEquals(Set.of("start", "a", "end"), r);
    }

    @Test
    void reachable_excludesIsolatedNode() {
        index(new WorkflowNode[]{node("start", NodeType.START), node("orphan", NodeType.LLM)},
                new WorkflowEdge[]{});
        Set<String> r = GraphValidator.bfsReachable(nodeById, outEdges, "start");
        assertEquals(Set.of("start"), r);
        // 孤立点不在 reachable 子图，不应触发环报错
        assertTrue(GraphValidator.detectCycle(r, inEdges, outEdges).isEmpty());
    }

    @Test
    void detectCycle_selfLoop_findsNode() {
        index(new WorkflowNode[]{node("start", NodeType.START), node("loop", NodeType.CODE)},
                new WorkflowEdge[]{edge("start", "loop"), edge("loop", "loop")});
        Set<String> r = GraphValidator.bfsReachable(nodeById, outEdges, "start");
        List<String> cycle = GraphValidator.detectCycle(r, inEdges, outEdges);
        assertEquals(List.of("loop"), cycle);
    }

    @Test
    void detectCycle_twoNodeCycle_findsBoth() {
        index(new WorkflowNode[]{node("start", NodeType.START), node("a", NodeType.CODE), node("b", NodeType.CODE)},
                new WorkflowEdge[]{edge("start", "a"), edge("a", "b"), edge("b", "a")});
        Set<String> r = GraphValidator.bfsReachable(nodeById, outEdges, "start");
        List<String> cycle = GraphValidator.detectCycle(r, inEdges, outEdges);
        assertEquals(List.of("a", "b"), cycle);
    }

    @Test
    void detectCycle_diamond_isAcyclic() {
        index(new WorkflowNode[]{node("start", NodeType.START), node("a", NodeType.CODE),
                        node("b", NodeType.CODE), node("join", NodeType.END)},
                new WorkflowEdge[]{edge("start", "a"), edge("start", "b"),
                        edge("a", "join"), edge("b", "join")});
        Set<String> r = GraphValidator.bfsReachable(nodeById, outEdges, "start");
        assertTrue(GraphValidator.detectCycle(r, inEdges, outEdges).isEmpty());
    }
}
