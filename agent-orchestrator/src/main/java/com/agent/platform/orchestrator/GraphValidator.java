package com.agent.platform.orchestrator;

import com.agent.platform.orchestrator.WorkflowGraph.WorkflowEdge;
import com.agent.platform.orchestrator.WorkflowGraph.WorkflowNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图静态校验器（纯函数，无状态，可单测）
 * <p>
 * 负责执行前对 DSL 图的结构性检查：可达性标记（BFS）与环路检测（Kahn 拓扑排序）。
 * 原实现内聚于 {@link WorkflowEngine}，拆分后引擎只负责调度，本类专注图合法性。
 */
public final class GraphValidator {

    private GraphValidator() {
    }

    /**
     * 从 start 出发 BFS 标记可达节点。
     * 注意：排他分支（condition 等）的所有出边都算可达——是否真正执行由运行时分支决定，
     * 此处只排除「结构性不可达」（没有连线通向的孤立节点）。
     */
    public static Set<String> bfsReachable(Map<String, WorkflowNode> nodeById,
                                           Map<String, List<WorkflowEdge>> outEdges,
                                           String startId) {
        Set<String> reachable = new HashSet<>();
        if (startId == null || !nodeById.containsKey(startId)) {
            return reachable;
        }
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        reachable.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            for (WorkflowEdge e : outEdges.getOrDefault(id, List.of())) {
                if (reachable.add(e.getTarget())) {
                    queue.add(e.getTarget());
                }
            }
        }
        return reachable;
    }

    /**
     * 在 reachable 子图上做 Kahn 拓扑排序检测环。
     * 每次弹出零入度节点后，遍历其<b>出边</b>把目标节点入度减一。
     *
     * @return 环上（或被环阻塞）的节点 id 列表（升序）；无环时返回空列表
     */
    public static List<String> detectCycle(Set<String> reachable,
                                           Map<String, List<WorkflowEdge>> inEdges,
                                           Map<String, List<WorkflowEdge>> outEdges) {
        if (reachable == null || reachable.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> deg = new HashMap<>();
        for (String id : reachable) {
            int d = 0;
            for (WorkflowEdge e : inEdges.getOrDefault(id, List.of())) {
                if (reachable.contains(e.getSource())) {
                    d++;
                }
            }
            deg.put(id, d);
        }
        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> en : deg.entrySet()) {
            if (en.getValue() == 0) {
                queue.add(en.getKey());
            }
        }
        int processed = 0;
        while (!queue.isEmpty()) {
            String id = queue.poll();
            processed++;
            for (WorkflowEdge e : outEdges.getOrDefault(id, List.of())) {
                String t = e.getTarget();
                if (reachable.contains(t)) {
                    int nd = deg.get(t) - 1;
                    deg.put(t, nd);
                    if (nd == 0) {
                        queue.add(t);
                    }
                }
            }
        }
        if (processed == reachable.size()) {
            return List.of();
        }
        return deg.entrySet().stream()
                .filter(en -> en.getValue() > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }
}
