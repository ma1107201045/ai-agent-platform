package com.agent.platform.workflow;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.workflow.node.*;
import com.agent.platform.workflow.spi.AgentRunner;
import com.agent.platform.workflow.spi.KnowledgeProvider;
import com.agent.platform.workflow.spi.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 工作流执行引擎（v4：DAG 拓扑序 + 并行 fork/join + 节点处理器 SPI + 节点级执行策略）
 * <p>
 * 引擎只负责调度（依赖解析、并行控制、分支释放、执行策略、轨迹与结果归集），
 * 具体节点语义由 {@link NodeHandler} 实现，通过 {@link NodeHandlerRegistry} 按类型查找。
 * <b>新增节点类型无需改动本类</b>：实现 {@link NodeHandler} 并交给 Spring 管理即可。
 * <p>
 * 内置节点：start / end / llm / agent / condition / code / http / template / knowledge
 * 变量：{{input}} 用户输入；{{节点id}} 取该节点输出文本；{{别名}} 取节点的输出变量别名
 * <p>
 * 执行模型：
 * <ul>
 *   <li>节点按依赖拓扑序执行：全部上游完成后才执行（join 等齐）；普通节点多条出边并行 fork</li>
 *   <li>排他分支（condition 等）：仅释放选中的出边，未选中分支整链跳过（skipped）</li>
 *   <li>节点级执行策略：重试（retries）、超时（timeoutSeconds）、
 *       错误处理（onError = fail / continue / fallback）、输出变量别名（outputVar）</li>
 *   <li>节点出错时按 onError 决定下游是否连锁跳过，最终回答返回第一条错误</li>
 *   <li>执行前做可达性与环路检测，环形/不可达结构直接报错</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    /** DAG 并行执行线程数 */
    private static final int PARALLELISM = 4;
    /** 兜底超时（环检测已防死等，此值仅作保险） */
    private static final long RUN_TIMEOUT_SECONDS = 300;
    /** 单节点默认超时（秒）；0 表示不限制 */
    private static final int DEFAULT_NODE_TIMEOUT = 0;
    /** 轨迹中输出/输入的截断长度 */
    private static final int TRACE_LIMIT = 300;

    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
    /** 配置了节点超时的任务使用弹性池，避免超时等待占满固定池导致调度饥饿 */
    private final ExecutorService elasticExecutor = Executors.newCachedThreadPool();

    private final NodeHandlerRegistry registry;
    private final VariableRenderer renderer;
    private final ModelProvider modelProvider;
    private final KnowledgeProvider knowledgeProvider;
    private final AgentRunner agentRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 一次 run 的图索引与执行状态（线程安全） */
    private static class RunState {
        final Map<String, WorkflowGraph.WorkflowNode> nodeById = new ConcurrentHashMap<>();
        final Map<String, List<WorkflowGraph.WorkflowEdge>> outEdges = new HashMap<>();
        final Map<String, List<WorkflowGraph.WorkflowEdge>> inEdges = new HashMap<>();
        /** 从 start 可达的节点集合（BFS，condition 两条分支均算可达） */
        final Set<String> reachable = new HashSet<>();
        /** 剩余未释放的入边数（所有边计入，含 condition 两条分支） */
        final Map<String, Integer> indegree = new ConcurrentHashMap<>();
        /** 各节点收到「正常释放」的次数，>0 表示至少一个上游正常完成 */
        final Map<String, AtomicInteger> normalHits = new ConcurrentHashMap<>();
        final List<RunResult.TraceItem> trace = Collections.synchronizedList(new ArrayList<>());
        final Map<String, String> outputs = new ConcurrentHashMap<>();
        final AtomicInteger pending = new AtomicInteger();
        final CompletableFuture<Void> done = new CompletableFuture<>();
        String userInput = "";
        Long appId;
    }

    /**
     * 执行工作流（DAG 拓扑序 + 并行 fork/join）
     *
     * @param graph      DSL 图
     * @param userInput  用户最新输入
     */
    public RunResult run(WorkflowGraph graph, String userInput) {
        return run(graph, userInput, null);
    }

    /**
     * 执行工作流
     *
     * @param graph     DSL 图
     * @param userInput 用户最新输入
     * @param appId     所属应用 ID（Agent 节点回退应用绑定配置时使用，可为 null）
     */
    public RunResult run(WorkflowGraph graph, String userInput, Long appId) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return RunResult.builder().answer("").trace(new ArrayList<>()).build();
        }
        RunState st = new RunState();
        st.userInput = userInput == null ? "" : userInput;
        st.appId = appId;

        // 1) 建索引
        for (WorkflowGraph.WorkflowNode n : graph.getNodes()) {
            st.nodeById.put(n.getId(), n);
        }
        if (graph.getEdges() != null) {
            for (WorkflowGraph.WorkflowEdge e : graph.getEdges()) {
                st.outEdges.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(e);
                st.inEdges.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e);
            }
        }

        // 2) 起点：第一个 start 节点，否则第一个节点
        WorkflowGraph.WorkflowNode start = graph.getNodes().stream()
                .filter(n -> n.nodeType() == NodeType.START)
                .findFirst()
                .orElse(graph.getNodes().getFirst());

        // 3) 可达性 BFS（排他分支的所有出边都算可达）
        bfsReachable(st, start.getId());

        // 4) 环路检测（Kahn，reachable 子图）
        List<String> cyclic = detectCycle(st);
        if (!cyclic.isEmpty()) {
            return RunResult.builder()
                    .answer("流程存在循环依赖或不可达节点：" + String.join("、", cyclic))
                    .trace(new ArrayList<>())
                    .build();
        }

        // 5) 入度：reachable 内节点，统计来自 reachable 的入边（指向 start 的边不计数）
        for (String id : st.reachable) {
            if (id.equals(start.getId())) {
                continue;
            }
            int deg = 0;
            for (WorkflowGraph.WorkflowEdge e : st.inEdges.getOrDefault(id, List.of())) {
                if (st.reachable.contains(e.getSource())) {
                    deg++;
                }
            }
            st.indegree.put(id, deg);
        }

        // 6) 执行（并行）
        processNode(st, start.getId());
        try {
            st.done.get(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            return RunResult.builder()
                    .answer("工作流执行超时或异常：" + e.getMessage())
                    .trace(new ArrayList<>(st.trace))
                    .build();
        }

        return RunResult.builder()
                .answer(computeAnswer(st))
                .trace(new ArrayList<>(st.trace))
                .build();
    }

    /** 提交节点异步执行。每个真实执行节点占用一个 pending 计数。 */
    private void processNode(RunState st, String nodeId) {
        st.pending.incrementAndGet();
        CompletableFuture.runAsync(() -> {
            try {
                executeWithTrace(st, nodeId);
            } finally {
                if (st.pending.decrementAndGet() == 0) {
                    st.done.complete(null);
                }
            }
        }, executor);
    }

    /** 跳过节点：不执行，记录 skipped，出边连锁跳过 */
    private void skipNode(RunState st, String nodeId) {
        WorkflowGraph.WorkflowNode node = st.nodeById.get(nodeId);
        st.trace.add(RunResult.TraceItem.builder()
                .nodeId(nodeId)
                .nodeType(node.nodeType())
                .label(labelOf(node))
                .status(NodeStatus.SKIPPED)
                .build());
        releaseEdges(st, node, null, true);
    }

    /**
     * 在线程池中执行节点并记录轨迹，随后释放出边。
     * <p>
     * 执行策略（节点级，全部可选）：
     * <ul>
     *   <li>{@code retries}：失败重试次数，退避 300ms * (n+1)</li>
     *   <li>{@code timeoutSeconds}：单节点超时，超时按失败处理</li>
     *   <li>{@code onError}：fail（下游跳过，默认）/ continue（忽略错误继续）/ fallback（用兜底文本继续）</li>
     *   <li>{@code outputVar}：输出变量别名，下游可用 {{别名}} 引用本节点输出</li>
     * </ul>
     */
    private void executeWithTrace(RunState st, String nodeId) {
        WorkflowGraph.WorkflowNode node = st.nodeById.get(nodeId);
        NodeHandler handler = registry.required(node.nodeType());
        NodeContext ctx = newContext(st, node);

        int retries = Math.max(0, ctx.cfgInt("retries", 0));
        int timeoutSeconds = ctx.cfgInt("timeoutSeconds", DEFAULT_NODE_TIMEOUT);
        String onError = ctx.cfgStr("onError", "fail");
        String fallbackText = ctx.cfgStr("errorFallback", "");
        String outputVar = ctx.cfgStr("outputVar");

        long start = System.currentTimeMillis();
        NodeStatus status = NodeStatus.SUCCESS;
        String error = null;
        String output = null;
        String selected = null;

        for (int attempt = 0; ; attempt++) {
            try {
                // 执行前配置校验（扩展点，未实现时无开销）
                String invalid = handler.validate(ctx);
                if (invalid != null && !invalid.isBlank()) {
                    throw new ConfigException(invalid);
                }
                NodeResult result = invoke(handler, ctx, timeoutSeconds);
                if (result != null) {
                    output = result.getOutput();
                    selected = result.getSelectedHandle();
                }
                status = NodeStatus.SUCCESS;
                error = null;
                break;
            } catch (ConfigException e) {
                // 配置问题重试无意义，直接失败
                status = NodeStatus.ERROR;
                error = e.getMessage();
                break;
            } catch (Exception e) {
                status = NodeStatus.ERROR;
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (attempt >= retries || Thread.currentThread().isInterrupted()) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }
        long cost = System.currentTimeMillis() - start;

        // 错误处理策略
        boolean propagateSkip = false;
        if (status.isError()) {
            if ("continue".equalsIgnoreCase(onError)) {
                propagateSkip = false;
            } else if ("fallback".equalsIgnoreCase(onError)) {
                output = ctx.render(fallbackText);
                ctx.emit(output);
                propagateSkip = false;
            } else {
                propagateSkip = true;
            }
        }

        // 输出变量别名
        if (outputVar != null && !outputVar.isBlank() && status.isSuccess()) {
            st.outputs.put(outputVar.trim(), output == null ? "" : output);
        }

        // 输入摘要：由处理器自行描述（模板内容 / 检索词 / 节点名等）
        String input = null;
        if (status.isSuccess()) {
            try {
                input = handler.describeInput(ctx);
            } catch (Exception ignore) {
                // 摘要仅用于展示，失败不影响流程
            }
            if (input != null && input.length() > TRACE_LIMIT) {
                input = input.substring(0, TRACE_LIMIT) + "...";
            }
        }
        if (output != null && output.length() > TRACE_LIMIT) {
            output = output.substring(0, TRACE_LIMIT) + "...";
        }
        st.trace.add(RunResult.TraceItem.builder()
                .nodeId(node.getId())
                .nodeType(node.nodeType())
                .label(labelOf(node))
                .status(status)
                .input(input)
                .output(output)
                .error(error)
                .costMs(cost)
                .build());

        if (propagateSkip) {
            // 出错且策略为中断：下游连锁跳过
            releaseEdges(st, node, null, true);
        } else {
            // selected 非空 = 排他分支（由处理器通过 NodeResult 指定）
            releaseEdges(st, node, selected, false);
        }
    }

    /** 调用处理器，必要时施加超时控制 */
    private NodeResult invoke(NodeHandler handler, NodeContext ctx, int timeoutSeconds) throws Exception {
        if (timeoutSeconds <= 0) {
            return handler.execute(ctx);
        }
        Future<NodeResult> future = elasticExecutor.submit(() -> handler.execute(ctx));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BizException("节点「" + ctx.label() + "」执行超时（" + timeoutSeconds + "s）");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new BizException(String.valueOf(cause == null ? "未知错误" : cause.getMessage()));
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(300L * (attempt + 1));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 构造节点执行上下文 */
    private NodeContext newContext(RunState st, WorkflowGraph.WorkflowNode node) {
        return new NodeContext(node, st.appId, st.userInput, st.outputs, renderer,
                modelProvider, knowledgeProvider, agentRunner, objectMapper);
    }

    /** 节点展示名：优先 DSL 中的 label，其次节点类型默认名，最后回退为原始 type 字符串 */
    private String labelOf(WorkflowGraph.WorkflowNode node) {
        if (node.getLabel() != null && !node.getLabel().isBlank()) {
            return node.getLabel();
        }
        NodeType type = node.nodeType();
        return type != null ? type.getLabel() : node.getType();
    }

    /**
     * 释放节点出边（决定下游何时可执行）：
     * <ul>
     *   <li>propagateSkip=true（本节点出错/被跳过）：所有出边以 skip 释放，下游整链跳过</li>
     *   <li>selectedHandle 非空（排他分支节点）：仅选中边正常释放，未选中边以 skip 释放</li>
     *   <li>其余：全部正常释放（并行 fork）</li>
     * </ul>
     * 下游入度归零时：收到过至少一次正常释放则执行，否则跳过（join 遇全 skip 分支才跳过）。
     */
    private void releaseEdges(RunState st, WorkflowGraph.WorkflowNode node,
                              String selectedHandle, boolean propagateSkip) {
        for (WorkflowGraph.WorkflowEdge e : st.outEdges.getOrDefault(node.getId(), List.of())) {
            String target = e.getTarget();
            if (!st.reachable.contains(target)) {
                continue;
            }
            boolean skip = propagateSkip
                    || (selectedHandle != null && !selectedHandle.equals(e.getSourceHandle()));
            if (!skip) {
                st.normalHits.computeIfAbsent(target, k -> new AtomicInteger()).incrementAndGet();
            }
            int remain = st.indegree.compute(target, (k, v) -> (v == null ? 0 : v) - 1);
            if (remain == 0) {
                // 入度归零：收到过正常释放则执行（join 至少一个上游成功），否则整链来自未选中/出错分支，跳过
                AtomicInteger hits = st.normalHits.get(target);
                if (hits != null && hits.get() > 0) {
                    processNode(st, target);
                } else {
                    skipNode(st, target);
                }
            }
        }
    }

    /** 从 start 出发 BFS 标记可达节点（排他分支的所有出边都走） */
    private void bfsReachable(RunState st, String startId) {
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        st.reachable.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            for (WorkflowGraph.WorkflowEdge e : st.outEdges.getOrDefault(id, List.of())) {
                if (st.reachable.add(e.getTarget())) {
                    queue.add(e.getTarget());
                }
            }
        }
    }

    /** Kahn 拓扑排序检测环，返回环上/被环阻塞的节点 id */
    private List<String> detectCycle(RunState st) {
        Map<String, Integer> deg = new HashMap<>();
        for (String id : st.reachable) {
            int d = 0;
            for (WorkflowGraph.WorkflowEdge e : st.inEdges.getOrDefault(id, List.of())) {
                if (st.reachable.contains(e.getSource())) {
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
            for (WorkflowGraph.WorkflowEdge e : st.outEdges.getOrDefault(id, List.of())) {
                String t = e.getTarget();
                if (st.reachable.contains(t)) {
                    int nd = deg.get(t) - 1;
                    deg.put(t, nd);
                    if (nd == 0) {
                        queue.add(t);
                    }
                }
            }
        }
        if (processed == st.reachable.size()) {
            return List.of();
        }
        return deg.entrySet().stream()
                .filter(en -> en.getValue() > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 计算最终回答，优先级：
     * 1. 第一条错误节点的错误信息；
     * 2. end 节点配置的回答模板输出；
     * 3. end 节点直接上游中「最后完成且有输出」的节点输出（并行多分支取最后完成的）；
     * 4. 最后一个成功执行的 LLM / Agent 输出；
     * 5. 用户原始输入。
     */
    private String computeAnswer(RunState st) {
        // 1) 第一条错误节点的错误信息
        for (RunResult.TraceItem t : st.trace) {
            if (t.getStatus() == NodeStatus.ERROR) {
                return "节点执行失败：" + t.getLabel() + " - " + t.getError();
            }
        }
        // 2) end 节点输出（配置了回答模板时为渲染结果）
        WorkflowGraph.WorkflowNode end = st.nodeById.values().stream()
                .filter(n -> n.nodeType() == NodeType.END)
                .findFirst()
                .orElse(null);
        if (end != null) {
            String endOutput = st.outputs.get(end.getId());
            if (endOutput != null && !endOutput.isBlank()) {
                return endOutput;
            }
            // 3) end 节点直接上游中「最后完成且有输出」的节点输出
            Set<String> sources = st.inEdges.getOrDefault(end.getId(), List.of()).stream()
                    .map(WorkflowGraph.WorkflowEdge::getSource)
                    .collect(Collectors.toSet());
            for (int i = st.trace.size() - 1; i >= 0; i--) {
                RunResult.TraceItem t = st.trace.get(i);
                if (t.getStatus() == NodeStatus.SUCCESS
                        && sources.contains(t.getNodeId())
                        && t.getOutput() != null && !t.getOutput().isBlank()) {
                    return t.getOutput();
                }
            }
        }
        // 4) 最后一个成功执行的 LLM / Agent 输出
        for (int i = st.trace.size() - 1; i >= 0; i--) {
            RunResult.TraceItem t = st.trace.get(i);
            if (t.getStatus() == NodeStatus.SUCCESS
                    && (t.getNodeType() == NodeType.LLM || t.getNodeType() == NodeType.AGENT)
                    && t.getOutput() != null) {
                return t.getOutput();
            }
        }
        // 5) 兜底：用户原始输入
        return st.userInput;
    }

    /** 配置类错误：与运行时错误区分，不参与重试 */
    private static class ConfigException extends RuntimeException {
        ConfigException(String message) {
            super(message);
        }
    }
}
