package com.agent.platform.orchestrator;

import com.agent.platform.orchestrator.node.NodeContext;
import com.agent.platform.orchestrator.node.NodeHandler;
import com.agent.platform.orchestrator.node.NodeHandlerRegistry;
import com.agent.platform.orchestrator.spi.AgentRunner;
import com.agent.platform.orchestrator.spi.KnowledgeProvider;
import com.agent.platform.orchestrator.spi.ModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工作流执行引擎（v5：DAG 拓扑序 + 并行 fork/join + 节点处理器 SPI + 节点级执行策略）
 * <p>
 * 引擎只负责调度（依赖解析、并行控制、分支释放、执行策略、轨迹与结果归集），
 * 具体节点语义由 {@link NodeHandler} 实现，通过 {@link NodeHandlerRegistry} 按类型查找。
 * <b>新增节点类型无需改动本类</b>：实现 {@link NodeHandler} 并交给 Spring 管理即可。
 * <p>
 * 内置节点：start / end / llm / agent / condition / code / http / template / knowledge
 * 变量：{{input}} 用户输入；{{节点id}} 取该节点输出文本；{{别名}} 取节点的输出变量别名
 * <p>
 * <b>职责拆分（v5）</b>：
 * <ul>
 *   <li>图校验（可达性 / 环路）→ {@link GraphValidator}</li>
 *   <li>单节点调用策略（重试 / 超时 / 退避）→ {@link NodeExecutionPolicy}</li>
 *   <li>最终回答组装 → {@link AnswerAssembler}</li>
 *   <li>运行参数 → {@link WorkflowSettings}（由装配层注入，不再硬编码）</li>
 * </ul>
 * 本类不再持有 Spring 注解；由 {@code WorkflowEngineConfiguration} 装配为 Bean，
 * 线程池生命周期由容器 {@code destroyMethod="shutdown"} 统一管理。
 * <p>
 * 执行模型：
 * <ul>
 *   <li>节点按依赖拓扑序执行：全部上游完成后才执行（join 等齐）；普通节点多条出边并行 fork</li>
 *   <li>排他分支（condition 等）：仅释放选中的出边，未选中分支整链跳过（skipped）</li>
 *   <li>节点级执行策略：重试（retries）、超时（timeoutSeconds）、
 *       错误处理（onError = fail / continue / fallback）、输出变量别名（outputVar）</li>
 *   <li>节点出错时按 onError 决定下游是否连锁跳过</li>
 *   <li>执行前做可达性与环路检测，环形/不可达结构直接失败返回</li>
 *   <li>回答与错误分离：answer 面向用户；技术性错误进入 error / trace</li>
 * </ul>
 */
public class WorkflowEngine {

    /** 面向用户的失败兜底话术（不暴露内部细节） */
    private static final String USER_FACING_FAILED = "抱歉，工作流执行未完成，请稍后重试。";
    /** 面向用户的 DSL 非法话术 */
    private static final String USER_FACING_INVALID = "工作流配置有误，无法执行。";

    private final WorkflowSettings settings;
    private final NodeHandlerRegistry registry;
    private final VariableRenderer renderer;
    private final ModelProvider modelProvider;
    private final KnowledgeProvider knowledgeProvider;
    private final AgentRunner agentRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** DAG 并行执行线程池 */
    private final ExecutorService executor;
    /** 配置了节点超时的任务使用弹性池，避免超时等待占满固定池导致调度饥饿 */
    private final ExecutorService elasticExecutor;
    private final NodeExecutionPolicy executionPolicy;

    /** 生命周期监听器（运行记录 / SSE 进度 / 计量等） */
    private final List<WorkflowEventListener> eventListeners;

    /** 运行中的执行状态（runId → RunState），供取消 / 运行记录 / 监控使用 */
    private final Map<String, RunState> activeRuns = new ConcurrentHashMap<>();

    public WorkflowEngine(WorkflowSettings settings,
                          NodeHandlerRegistry registry,
                          VariableRenderer renderer,
                          ModelProvider modelProvider,
                          KnowledgeProvider knowledgeProvider,
                          AgentRunner agentRunner) {
        this(settings, registry, renderer, modelProvider, knowledgeProvider, agentRunner, List.of());
    }

    public WorkflowEngine(WorkflowSettings settings,
                          NodeHandlerRegistry registry,
                          VariableRenderer renderer,
                          ModelProvider modelProvider,
                          KnowledgeProvider knowledgeProvider,
                          AgentRunner agentRunner,
                          List<WorkflowEventListener> eventListeners) {
        this.settings = settings == null ? WorkflowSettings.defaults() : settings;
        this.registry = registry;
        this.renderer = renderer;
        this.modelProvider = modelProvider;
        this.knowledgeProvider = knowledgeProvider;
        this.agentRunner = agentRunner;
        this.executor = Executors.newFixedThreadPool(this.settings.parallelism());
        this.elasticExecutor = Executors.newCachedThreadPool();
        this.executionPolicy = new NodeExecutionPolicy(elasticExecutor, this.settings.retryBackoffBaseMs());
        this.eventListeners = eventListeners == null ? List.of() : List.copyOf(eventListeners);
    }

    /** 关闭线程池（由装配层 {@code destroyMethod} 调用） */
    public void shutdown() {
        executor.shutdownNow();
        elasticExecutor.shutdownNow();
    }

    // ==================== 对外入口 ====================

    /** 执行工作流（不指定 runId，自动生成） */
    public RunResult run(WorkflowGraph graph, String userInput) {
        return run(graph, userInput, null, null);
    }

    /** 执行工作流（不指定 runId，自动生成） */
    public RunResult run(WorkflowGraph graph, String userInput, Long appId) {
        return run(graph, userInput, appId, null);
    }

    /**
     * 执行工作流
     *
     * @param graph     DSL 图
     * @param userInput 用户最新输入
     * @param appId     所属应用 ID（Agent 节点回退应用绑定配置时使用，可为 null）
     * @param runId     运行标识（可为 null，自动生成；显式传入便于与运行记录 / 取消接口关联）
     */
    public RunResult run(WorkflowGraph graph, String userInput, Long appId, String runId) {
        String rid = runId == null || runId.isBlank() ? newRunId() : runId;
        LocalDateTime startedAt = LocalDateTime.now();
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            RunResult empty = RunResult.builder()
                    .runId(rid)
                    .appId(appId)
                    .status(RunStatus.SUCCESS)
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .costMs(0L)
                    .answer("")
                    .trace(new ArrayList<>())
                    .build();
            dispatchFlowFinished(empty);
            return empty;
        }
        RunState st = new RunState();
        st.runId = rid;
        st.startedAt = startedAt;
        st.userInput = userInput == null ? "" : userInput;
        st.appId = appId;
        activeRuns.put(rid, st);
        dispatch(listener -> listener.onFlowStarted(
                new WorkflowEventListener.FlowStarted(rid, appId, st.userInput, startedAt)));

        RunResult result;
        try {
            result = doRun(st, graph);
        } catch (Exception e) {
            result = finish(st, RunStatus.FAILED, "工作流执行异常：" + e.getMessage(), USER_FACING_FAILED);
        } finally {
            activeRuns.remove(rid);
        }
        dispatchFlowFinished(result);
        return result;
    }

    /** 统一派发运行结束事件（持久化 / 推送最终状态），实现异常不影响调用方 */
    private void dispatchFlowFinished(RunResult result) {
        dispatch(listener -> listener.onFlowFinished(new WorkflowEventListener.FlowFinished(result)));
    }

    /** 安全派发：任一监听器异常仅记录，不阻断流程 */
    private void dispatch(java.util.function.Consumer<WorkflowEventListener> callback) {
        for (WorkflowEventListener listener : eventListeners) {
            try {
                callback.accept(listener);
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(WorkflowEngine.class)
                        .warn("工作流事件监听器执行异常: {}", ex.getMessage());
            }
        }
    }

    private RunResult doRun(RunState st, WorkflowGraph graph) {
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
        st.reachable = GraphValidator.bfsReachable(st.nodeById, st.outEdges, start.getId());

        // 4) 环路检测（Kahn，reachable 子图）
        List<String> cyclic = GraphValidator.detectCycle(st.reachable, st.inEdges, st.outEdges);
        if (!cyclic.isEmpty()) {
            return finish(st, RunStatus.FAILED,
                    "流程存在循环依赖或不可达节点：" + String.join("、", cyclic), USER_FACING_INVALID);
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
            if (settings.runTimeoutSeconds() > 0) {
                st.done.get(settings.runTimeoutSeconds(), TimeUnit.SECONDS);
            } else {
                st.done.get();
            }
        } catch (java.util.concurrent.TimeoutException e) {
            return finish(st, RunStatus.TIMEOUT, "工作流执行超过整体兜底超时（"
                    + settings.runTimeoutSeconds() + "s）", USER_FACING_FAILED);
        } catch (Exception e) {
            return finish(st, RunStatus.FAILED,
                    "工作流执行异常：" + e.getMessage(), USER_FACING_FAILED);
        }

        // 7) 组装最终回答（存在致命错误时整场 FAILED）
        String fatal = st.fatalError.get();
        if (fatal != null) {
            return finish(st, RunStatus.FAILED, fatal, USER_FACING_FAILED);
        }
        String answer = AnswerAssembler.assemble(new ArrayList<>(st.trace), st.nodeById,
                st.inEdges, st.outputs, st.userInput);
        return finish(st, RunStatus.SUCCESS, null, answer);
    }

    /** 统一收尾：记录结束时间 / 耗时并构造结果 */
    private RunResult finish(RunState st, RunStatus status, String error, String answer) {
        LocalDateTime finishedAt = LocalDateTime.now();
        RunResult.RunResultBuilder builder = RunResult.builder()
                .runId(st.runId)
                .appId(st.appId)
                .status(status)
                .startedAt(st.startedAt)
                .finishedAt(finishedAt)
                .costMs(java.time.Duration.between(st.startedAt, finishedAt).toMillis())
                .error(error)
                .answer(answer == null ? "" : answer)
                .trace(new ArrayList<>(st.trace));
        return builder.build();
    }

    private String newRunId() {
        return "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== 调度 ====================

    /** 一次 run 的图索引与执行状态（线程安全） */
    private static class RunState {
        final Map<String, WorkflowGraph.WorkflowNode> nodeById = new ConcurrentHashMap<>();
        final Map<String, List<WorkflowGraph.WorkflowEdge>> outEdges = new HashMap<>();
        final Map<String, List<WorkflowGraph.WorkflowEdge>> inEdges = new HashMap<>();
        /** 从 start 可达的节点集合（BFS，condition 两条分支均算可达） */
        Set<String> reachable = new HashSet<>();
        /** 剩余未释放的入边数（所有边计入，含 condition 两条分支） */
        final Map<String, Integer> indegree = new ConcurrentHashMap<>();
        /** 各节点收到「正常释放」的次数，>0 表示至少一个上游正常完成 */
        final Map<String, AtomicInteger> normalHits = new ConcurrentHashMap<>();
        final List<RunResult.TraceItem> trace = Collections.synchronizedList(new ArrayList<>());
        final Map<String, String> outputs = new ConcurrentHashMap<>();
        final AtomicInteger pending = new AtomicInteger();
        final CompletableFuture<Void> done = new CompletableFuture<>();
        /** 第一个「致命」节点错误（onError=fail 触发下游连锁跳过）；被 continue/fallback 容忍的错误不算 */
        final java.util.concurrent.atomic.AtomicReference<String> fatalError = new java.util.concurrent.atomic.AtomicReference<>();
        String runId;
        LocalDateTime startedAt;
        String userInput = "";
        Long appId;
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
        RunResult.TraceItem item = RunResult.TraceItem.builder()
                .nodeId(nodeId)
                .nodeType(node.nodeType())
                .label(labelOf(node))
                .status(NodeStatus.SKIPPED)
                .build();
        st.trace.add(item);
        dispatch(listener -> listener.onNodeFinished(
                new WorkflowEventListener.NodeFinished(st.runId, st.appId, item)));
        releaseEdges(st, node, null, true);
    }

    /**
     * 在线程池中执行节点并记录轨迹，随后释放出边。
     * 调用语义交给 {@link NodeExecutionPolicy}，本方法只负责依据结果记录轨迹并决定下游释放策略。
     */
    private void executeWithTrace(RunState st, String nodeId) {
        WorkflowGraph.WorkflowNode node = st.nodeById.get(nodeId);
        NodeHandler handler = registry.required(node.nodeType());
        dispatch(listener -> listener.onNodeStarted(
                new WorkflowEventListener.NodeStarted(st.runId, st.appId,
                        node.getId(), labelOf(node), node.nodeType())));
        NodeContext ctx = newContext(st, node);

        int retries = Math.max(0, ctx.cfgInt("retries", 0));
        int timeoutSeconds = ctx.cfgInt("timeoutSeconds", settings.defaultNodeTimeoutSeconds());
        String onError = ctx.cfgStr("onError", "fail");
        String fallbackText = ctx.cfgStr("errorFallback", "");
        String outputVar = ctx.cfgStr("outputVar");

        NodeExecutionPolicy.NodeOutcome outcome = executionPolicy.run(handler, ctx, retries, timeoutSeconds);
        NodeStatus status = outcome.getStatus();
        String output = outcome.getOutput();
        String selected = outcome.getSelectedHandle();
        String error = outcome.getError();
        long cost = outcome.getCostMs();

        // 错误处理策略：决定下游是否连锁跳过
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
            input = truncate(input);
        }
        output = truncate(output);
        RunResult.TraceItem item = RunResult.TraceItem.builder()
                .nodeId(node.getId())
                .nodeType(node.nodeType())
                .label(labelOf(node))
                .status(status)
                .input(input)
                .output(output)
                .error(error)
                .costMs(cost)
                .build();
        st.trace.add(item);
        dispatch(listener -> listener.onNodeFinished(
                new WorkflowEventListener.NodeFinished(st.runId, st.appId, item)));

        if (propagateSkip) {
            // 出错且策略为中断：下游连锁跳过，整场记为首个致命错误
            if (status.isError()) {
                st.fatalError.compareAndSet(null,
                        error == null ? "节点「" + labelOf(node) + "」执行失败" : error);
            }
            releaseEdges(st, node, null, true);
        } else {
            // selected 非空 = 排他分支（由处理器通过 NodeResult 指定）
            releaseEdges(st, node, selected, false);
        }
    }

    /** 构造节点执行上下文（先应用 schema 默认值，保证新旧 DSL 行为一致） */
    private NodeContext newContext(RunState st, WorkflowGraph.WorkflowNode node) {
        registry.applyDefaults(node);
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

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() > settings.traceLimit()) {
            return s.substring(0, settings.traceLimit()) + "...";
        }
        return s;
    }
}
