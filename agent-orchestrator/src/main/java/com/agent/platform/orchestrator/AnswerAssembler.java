package com.agent.platform.orchestrator;

import com.agent.platform.orchestrator.WorkflowGraph.WorkflowEdge;
import com.agent.platform.orchestrator.WorkflowGraph.WorkflowNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 最终回答组装器（纯函数，无状态，可单测）
 * <p>
 * 引擎把执行轨迹与节点输出交给本类，按优先级组装面向用户的最终回答。
 * <b>注意：</b>当一次运行存在错误节点时由引擎走失败分支，本类只负责成功路径的答案选择。
 *
 * <p>优先级：
 * <ol>
 *   <li>end 节点配置的回答模板输出；</li>
 *   <li>end 节点直接上游中「最后完成且有输出」的节点输出（并行多分支取最后完成的）；</li>
 *   <li>最后一个成功执行的 LLM / Agent 输出；</li>
 *   <li>用户原始输入（兜底）。</li>
 * </ol>
 */
public final class AnswerAssembler {

    private AnswerAssembler() {
    }

    public static String assemble(List<RunResult.TraceItem> trace,
                                  Map<String, WorkflowNode> nodeById,
                                  Map<String, List<WorkflowEdge>> inEdges,
                                  Map<String, String> outputs,
                                  String userInput) {
        // 1) end 节点输出（配置了回答模板时为渲染结果）
        WorkflowNode end = nodeById.values().stream()
                .filter(n -> n.nodeType() == NodeType.END)
                .findFirst()
                .orElse(null);
        if (end != null) {
            String endOutput = outputs.get(end.getId());
            if (endOutput != null && !endOutput.isBlank()) {
                return endOutput;
            }
            // 2) end 节点直接上游中「最后完成且有输出」的节点输出
            Set<String> sources = inEdges.getOrDefault(end.getId(), List.of()).stream()
                    .map(WorkflowEdge::getSource)
                    .collect(Collectors.toSet());
            for (int i = trace.size() - 1; i >= 0; i--) {
                RunResult.TraceItem t = trace.get(i);
                if (t.getStatus() == NodeStatus.SUCCESS
                        && sources.contains(t.getNodeId())
                        && t.getOutput() != null && !t.getOutput().isBlank()) {
                    return t.getOutput();
                }
            }
        }
        // 3) 最后一个成功执行的 LLM / Agent 输出
        for (int i = trace.size() - 1; i >= 0; i--) {
            RunResult.TraceItem t = trace.get(i);
            if (t.getStatus() == NodeStatus.SUCCESS
                    && (t.getNodeType() == NodeType.LLM || t.getNodeType() == NodeType.AGENT)
                    && t.getOutput() != null) {
                return t.getOutput();
            }
        }
        // 4) 兜底：用户原始输入
        return userInput;
    }
}
