package com.agent.platform.orchestrator;

import com.agent.platform.orchestrator.WorkflowGraph.WorkflowEdge;
import com.agent.platform.orchestrator.WorkflowGraph.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnswerAssemblerTest {

    private List<RunResult.TraceItem> trace(Object... items) {
        List<RunResult.TraceItem> list = new ArrayList<>();
        for (Object o : items) {
            if (o instanceof RunResult.TraceItem t) {
                list.add(t);
            }
        }
        return list;
    }

    private RunResult.TraceItem ok(String nodeId, NodeType type, String output) {
        return RunResult.TraceItem.builder().nodeId(nodeId).nodeType(type)
                .status(NodeStatus.SUCCESS).output(output).build();
    }

    @Test
    void endTemplateOutput_wins() {
        WorkflowNode end = WorkflowDsl.node("end", NodeType.END);
        Map<String, WorkflowNode> nodeById = Map.of("end", end);
        Map<String, String> outputs = new HashMap<>();
        outputs.put("end", "来自结束模板");
        String answer = AnswerAssembler.assemble(trace(), nodeById, Map.of(), outputs, "u");
        assertEquals("来自结束模板", answer);
    }

    @Test
    void endUpstream_lastCompletedSuccess_isChosen() {
        WorkflowNode end = WorkflowDsl.node("end", NodeType.END);
        WorkflowNode a = WorkflowDsl.node("a", NodeType.LLM);
        WorkflowNode b = WorkflowDsl.node("b", NodeType.LLM);
        Map<String, WorkflowNode> nodeById = Map.of("end", end, "a", a, "b", b);
        Map<String, List<WorkflowEdge>> inEdges = Map.of("end", List.of(
                WorkflowDsl.edge("a", "end"), WorkflowDsl.edge("b", "end")));
        // b 在轨迹中最后完成 → 取 b
        String answer = AnswerAssembler.assemble(
                trace(ok("end", NodeType.END, null), ok("a", NodeType.LLM, "A"), ok("b", NodeType.LLM, "B")),
                nodeById, inEdges, new HashMap<>(), "u");
        assertEquals("B", answer);
    }

    @Test
    void noEnd_fallsBackToLastLlmOrAgent() {
        WorkflowNode llm = WorkflowDsl.node("llm", NodeType.LLM);
        String answer = AnswerAssembler.assemble(
                trace(ok("llm", NodeType.LLM, "最终生成")),
                Map.of("llm", llm), Map.of(), new HashMap<>(), "u");
        assertEquals("最终生成", answer);
    }

    @Test
    void emptyEverything_fallsBackToUserInput() {
        WorkflowNode llm = WorkflowDsl.node("llm", NodeType.LLM);
        String answer = AnswerAssembler.assemble(trace(), Map.of("llm", llm), Map.of(), new HashMap<>(), "原始问题");
        assertEquals("原始问题", answer);
    }
}
