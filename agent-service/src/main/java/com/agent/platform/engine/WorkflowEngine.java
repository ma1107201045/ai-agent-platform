package com.agent.platform.engine;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.service.KnowledgeService;
import com.agent.platform.service.ModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.mvel2.MVEL;

/**
 * 工作流执行引擎（v2：DAG 拓扑序 + 并行 fork/join）
 * <p>
 * 支持节点：start / end / llm / condition / code / http / template / knowledge
 * 变量：{{input}} 用户输入；{{节点id}} 取该节点输出文本
 * <p>
 * 执行模型：
 * <ul>
 *   <li>节点按依赖拓扑序执行：全部上游完成后才执行（join 等齐）；普通节点多条出边并行 fork</li>
 *   <li>condition 为排他分支：仅释放选中的出边，未选中分支整链跳过（skipped）</li>
 *   <li>节点出错时其下游连锁跳过，无关分支继续执行，最终回答返回第一条错误</li>
 *   <li>执行前做可达性与环路检测，环形/不可达结构直接报错</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    /** DAG 并行执行线程数 */
    private static final int PARALLELISM = 4;
    /** 兜底超时（环检测已防死等，此值仅作保险） */
    private static final long RUN_TIMEOUT_SECONDS = 60;

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");

    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);

    private final ModelService modelService;
    private final KnowledgeService knowledgeService;
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
    }

    /**
     * 执行工作流（DAG 拓扑序 + 并行 fork/join）
     *
     * @param graph      DSL 图
     * @param userInput  用户最新输入
     */
    public RunResult run(WorkflowGraph graph, String userInput) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return RunResult.builder().answer("").trace(new ArrayList<>()).build();
        }
        RunState st = new RunState();
        st.userInput = userInput == null ? "" : userInput;

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
                .filter(n -> "start".equals(n.getType()))
                .findFirst()
                .orElse(graph.getNodes().get(0));

        // 3) 可达性 BFS（condition 两条分支都算可达）
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
                .nodeType(node.getType())
                .label(node.getLabel() == null ? node.getType() : node.getLabel())
                .status("skipped")
                .build());
        releaseEdges(st, node, null, true);
    }

    /** 在线程池中执行节点并记录轨迹，随后释放出边 */
    private void executeWithTrace(RunState st, String nodeId) {
        WorkflowGraph.WorkflowNode node = st.nodeById.get(nodeId);
        long start = System.currentTimeMillis();
        String status = "success";
        String error = null;
        String output = null;
        String input = null;
        try {
            output = executeNode(node, st.userInput, st.outputs);
        } catch (BizException e) {
            status = "error";
            error = e.getMessage();
        } catch (Exception e) {
            status = "error";
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        long cost = System.currentTimeMillis() - start;

        if ("template".equals(node.getType())) {
            Object tpl = node.getConfig() == null ? null : node.getConfig().get("template");
            input = render(tpl == null ? "" : String.valueOf(tpl), st.userInput, st.outputs).trim();
        } else if ("knowledge".equals(node.getType())) {
            Object q = node.getConfig() == null ? null : node.getConfig().get("queryTemplate");
            input = render(q == null ? "{{input}}" : String.valueOf(q), st.userInput, st.outputs).trim();
        } else if ("llm".equals(node.getType()) || "http".equals(node.getType()) || "code".equals(node.getType())) {
            input = render(System.lineSeparator() + node.getLabel(), st.userInput, st.outputs).trim();
        }
        if (output != null && output.length() > 300) {
            output = output.substring(0, 300) + "...";
        }
        st.trace.add(RunResult.TraceItem.builder()
                .nodeId(node.getId())
                .nodeType(node.getType())
                .label(node.getLabel() == null ? node.getType() : node.getLabel())
                .status(status)
                .input(input)
                .output(output)
                .error(error)
                .costMs(cost)
                .build());

        if ("error".equals(status)) {
            // 出错：下游连锁跳过，无关分支继续执行
            releaseEdges(st, node, null, true);
        } else {
            String selected = null;
            if ("condition".equals(node.getType())) {
                boolean cond = evalCondition(node, st.outputs);
                selected = cond ? "true" : "false";
            }
            releaseEdges(st, node, selected, false);
        }
    }

    /**
     * 释放节点出边（决定下游何时可执行）：
     * <ul>
     *   <li>propagateSkip=true（本节点出错/被跳过）：所有出边以 skip 释放，下游整链跳过</li>
     *   <li>selectedHandle 非空（condition）：仅选中边正常释放，未选中边以 skip 释放（排他分支）</li>
     *   <li>其余：全部正常释放（并行 fork）</li>
     * </ul>
     * 下游入度归零时：收到过至少一次正常释放则执行，否则跳过（join 遇全 skip 分支才跳过）。
     */
    private void releaseEdges(RunState st, WorkflowGraph.WorkflowNode node, String selectedHandle, boolean propagateSkip) {
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

    /** 从 start 出发 BFS 标记可达节点（condition 两条分支都走） */
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
     * 2. end 节点直接上游中「最后完成且有输出」的节点输出（并行多分支取最后完成的）；
     * 3. 最后一个成功执行的 LLM 输出；
     * 4. 用户原始输入。
     */
    private String computeAnswer(RunState st) {
        for (RunResult.TraceItem t : st.trace) {
            if ("error".equals(t.getStatus())) {
                return "节点执行失败：" + t.getLabel() + " - " + t.getError();
            }
        }
        WorkflowGraph.WorkflowNode end = st.nodeById.values().stream()
                .filter(n -> "end".equals(n.getType()))
                .findFirst()
                .orElse(null);
        if (end != null) {
            Set<String> sources = st.inEdges.getOrDefault(end.getId(), List.of()).stream()
                    .map(WorkflowGraph.WorkflowEdge::getSource)
                    .collect(Collectors.toSet());
            for (int i = st.trace.size() - 1; i >= 0; i--) {
                RunResult.TraceItem t = st.trace.get(i);
                if ("success".equals(t.getStatus())
                        && sources.contains(t.getNodeId())
                        && t.getOutput() != null && !t.getOutput().isBlank()) {
                    return t.getOutput();
                }
            }
        }
        for (int i = st.trace.size() - 1; i >= 0; i--) {
            RunResult.TraceItem t = st.trace.get(i);
            if ("success".equals(t.getStatus()) && "llm".equals(t.getNodeType())
                    && t.getOutput() != null) {
                return t.getOutput();
            }
        }
        return st.userInput;
    }

    /** 执行单个节点，返回节点输出文本（LLM 回答 / HTTP 响应体 / 脚本输出） */
    private String executeNode(WorkflowGraph.WorkflowNode node, String userInput, Map<String, String> outputs) {
        switch (node.getType()) {
            case "start":
                return null;
            case "end":
                return null;
            case "llm":
                return executeLlm(node, userInput, outputs);
            case "http":
                return executeHttp(node, userInput, outputs);
            case "condition":
                return null;
            case "code":
                return executeCode(node, userInput, outputs);
            case "template":
                return executeTemplate(node, userInput, outputs);
            case "knowledge":
                return executeKnowledge(node, userInput, outputs);
            default:
                return null;
        }
    }

    /** LLM 节点：系统提示词 + 用户输入（支持 {{input}} / {{节点id}} 变量替换），可选知识库检索增强 */
    private String executeLlm(WorkflowGraph.WorkflowNode node, String userInput, Map<String, String> outputs) {
        Map<String, Object> cfg = node.getConfig();
        Object modelIdObj = cfg == null ? null : cfg.get("modelId");
        if (modelIdObj == null) {
            throw new BizException("LLM 节点「" + (node.getLabel() == null ? node.getId() : node.getLabel())
                    + "」未配置模型");
        }
        Long modelId = ((Number) modelIdObj).longValue();

        String systemPrompt = cfg.get("systemPrompt") == null
                ? "You are a helpful assistant."
                : String.valueOf(cfg.get("systemPrompt"));
        Object temp = cfg.get("temperature");
        Double temperature = temp instanceof Number ? ((Number) temp).doubleValue() : null;

        // 知识库检索增强：配置 datasetId 时，按用户输入检索 topK 片段拼入 system 提示词
        String knowledgeContext = "";
        Object datasetIdObj = cfg.get("datasetId");
        if (datasetIdObj != null && !"null".equals(String.valueOf(datasetIdObj))) {
            Long datasetId = ((Number) datasetIdObj).longValue();
            Object topKObj = cfg.get("topK");
            int topK = topKObj instanceof Number ? ((Number) topKObj).intValue() : 3;
            Object rerankObj = cfg.get("rerankModelId");
            Long rerankModelId = rerankObj instanceof Number ? ((Number) rerankObj).longValue() : null;
            try {
                List<KnowledgeService.SearchHit> hits = knowledgeService.search(datasetId, userInput, topK, rerankModelId);
                if (!hits.isEmpty()) {
                    StringBuilder sb = new StringBuilder("\n\n以下是与用户问题相关的知识库参考资料，请据此回答：\n\n");
                    for (int i = 0; i < hits.size(); i++) {
                        sb.append("[").append(i + 1).append("] ")
                                .append(hits.get(i).getContent().strip())
                                .append("\n\n");
                    }
                    knowledgeContext = sb.toString();
                }
            } catch (Exception e) {
                // 检索失败不中断流程，仅记录
                knowledgeContext = "\n\n[知识库检索失败: " + e.getMessage() + "]\n";
            }
        }

        ChatModel chatModel = modelService.chatModelOf(modelId);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(render(systemPrompt, userInput, outputs) + knowledgeContext),
                        ChatMessage.user(render(userInput, userInput, outputs))))
                .temperature(temperature)
                .build();
        ChatResponse response = chatModel.call(request);
        String content = response == null ? null : response.getContent();
        outputs.put(node.getId(), content == null ? "" : content);
        return content;
    }

    /**
     * HTTP 节点：GET / POST / PUT / DELETE 调用外部 API。
     * 支持变量替换（{{input}} / {{节点id}}）、自定义 headers、鉴权（Bearer / Basic）、失败重试。
     */
    private String executeHttp(WorkflowGraph.WorkflowNode node, String userInput, Map<String, String> outputs) {
        Map<String, Object> cfg = node.getConfig();
        String url = cfg == null ? "" : String.valueOf(cfg.getOrDefault("url", ""));
        if (url.isBlank() || "null".equals(url)) {
            throw new BizException("HTTP 节点「" + node.getLabel() + "」未配置请求地址");
        }
        url = render(url, userInput, outputs);
        String method = cfg.get("method") == null ? "GET" : String.valueOf(cfg.get("method")).toUpperCase();
        int retries = cfg.get("retries") instanceof Number ? ((Number) cfg.get("retries")).intValue() : 0;

        Exception lastError = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30));

                // 自定义 Headers（JSON 对象，支持变量替换）
                Object headersObj = cfg.get("headers");
                if (headersObj instanceof Map<?, ?>) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) headersObj).entrySet()) {
                        builder.header(String.valueOf(e.getKey()),
                                render(String.valueOf(e.getValue()), userInput, outputs));
                    }
                }
                // 鉴权
                String authType = cfg.get("authType") == null ? "none" : String.valueOf(cfg.get("authType"));
                if ("bearer".equalsIgnoreCase(authType)) {
                    String token = cfg.get("authToken") == null ? "" : String.valueOf(cfg.get("authToken"));
                    builder.header("Authorization", "Bearer " + render(token, userInput, outputs));
                } else if ("basic".equalsIgnoreCase(authType)) {
                    String username = cfg.get("authUsername") == null ? "" : String.valueOf(cfg.get("authUsername"));
                    String password = cfg.get("authPassword") == null ? "" : String.valueOf(cfg.get("authPassword"));
                    String encoded = Base64.getEncoder().encodeToString(
                            (render(username, userInput, outputs) + ":" + render(password, userInput, outputs))
                                    .getBytes(StandardCharsets.UTF_8));
                    builder.header("Authorization", "Basic " + encoded);
                }

                if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                    if (!"DELETE".equals(method)) {
                        builder.header("Content-Type", "application/json");
                    }
                    String body = objectMapper.writeValueAsString(outputs);
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                } else {
                    builder.method("GET", HttpRequest.BodyPublishers.noBody());
                }
                HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                outputs.put(node.getId(), body == null ? "" : body);
                return body;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                lastError = e;
                // 重试前短暂等待
                if (attempt < retries) {
                    try {
                        Thread.sleep(300L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new BizException("HTTP 节点「" + node.getLabel() + "」请求失败: "
                + (lastError == null ? "未知错误" : lastError.getMessage()));
    }

    /**
     * 代码节点：执行 MVEL 表达式脚本。
     * 脚本内可使用变量：input（用户输入）、以及 {{节点id}} 对应的各节点输出（变量名为节点 id）。
     * 通过 return 返回结果文本。
     */
    private String executeCode(WorkflowGraph.WorkflowNode node, String userInput, Map<String, String> outputs) {
        Map<String, Object> cfg = node.getConfig();
        String code = cfg == null ? null : String.valueOf(cfg.getOrDefault("code", ""));
        if (code == null || code.isBlank() || "null".equals(code)) {
            throw new BizException("代码节点「" + node.getLabel() + "」未配置代码");
        }
        try {
            Map<String, Object> vars = new HashMap<>(outputs);
            vars.put("input", userInput == null ? "" : userInput);
            vars.put("outputs", outputs);
            Object result = MVEL.executeExpression(MVEL.compileExpression(code), vars);
            String text = result == null ? "" : String.valueOf(result);
            outputs.put(node.getId(), text);
            return text;
        } catch (Exception e) {
            throw new BizException("代码节点「" + node.getLabel() + "」执行失败: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * 模板节点：对模板内容做 {{input}} / {{节点id}} 变量插值后输出。
     * 常用于拼装 prompt、格式化文本，输出可被下游节点引用。
     */
    private String executeTemplate(WorkflowGraph.WorkflowNode node, String userInput, Map<String, String> outputs) {
        Map<String, Object> cfg = node.getConfig();
        String template = cfg == null ? null : String.valueOf(cfg.getOrDefault("template", ""));
        if (template == null || template.isBlank() || "null".equals(template)) {
            throw new BizException("模板节点「" + node.getLabel() + "」未配置模板内容");
        }
        String text = render(template, userInput, outputs);
        outputs.put(node.getId(), text);
        return text;
    }

    /**
     * 知识库检索节点：按检索词模板渲染查询，调用知识库语义检索，
     * 输出拼接的命中片段文本，供下游 LLM / 模板节点使用。
     */
    private String executeKnowledge(WorkflowGraph.WorkflowNode node, String userInput, Map<String, String> outputs) {
        Map<String, Object> cfg = node.getConfig();
        Object dsIdObj = cfg == null ? null : cfg.get("datasetId");
        if (dsIdObj == null) {
            throw new BizException("知识库检索节点「" + node.getLabel() + "」未选择数据集");
        }
        Long datasetId = Long.valueOf(String.valueOf(dsIdObj));
        String queryTemplate = cfg.get("queryTemplate") == null
                ? "{{input}}"
                : String.valueOf(cfg.get("queryTemplate"));
        String query = render(queryTemplate, userInput, outputs);
        int topK = cfg.get("topK") == null ? 3 : Integer.parseInt(String.valueOf(cfg.get("topK")));
        Long rerankModelId = cfg.get("rerankModelId") == null
                ? null
                : Long.valueOf(String.valueOf(cfg.get("rerankModelId")));
        List<KnowledgeService.SearchHit> hits = knowledgeService.search(datasetId, query, topK, rerankModelId);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeService.SearchHit h = hits.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append("【片段 ").append(i + 1).append("】").append(h.getContent());
        }
        String text = sb.toString();
        outputs.put(node.getId(), text);
        return text;
    }

    /**
     * 条件表达式求值（升级版）：
     * 1. true/false 字面量直接判定；
     * 2. 其余交给 MVEL 求值，支持 ==、!=、&gt;、&lt;、&gt;=、&lt;=、contains、&amp;&amp;、||、! 与括号
     *    （{{变量}} 渲染为实际值；字符串比较需加引号，如 '{{node1}}' == '成功'）；
     * 3. MVEL 求值失败时回退为「非空即真」。
     */
    private boolean evalCondition(WorkflowGraph.WorkflowNode node, Map<String, String> outputs) {
        Object exprObj = node.getConfig() == null ? null : node.getConfig().get("expression");
        String expr = exprObj == null ? null : String.valueOf(exprObj);
        if (expr == null || expr.isBlank()) {
            return true;
        }
        String rendered = render(expr, "", outputs).trim();
        if ("true".equalsIgnoreCase(rendered)) {
            return true;
        }
        if ("false".equalsIgnoreCase(rendered)) {
            return false;
        }
        try {
            Map<String, Object> vars = new HashMap<>(outputs);
            vars.put("input", outputs.getOrDefault("input", ""));
            Object result = MVEL.eval(rendered, vars);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return result != null && !String.valueOf(result).isBlank();
        } catch (Exception e) {
            // 表达式语法不支持或变量未渲染为可比较值：按非空即真处理
            return !rendered.isEmpty();
        }
    }

    /** 模板变量替换：{{input}} 用户输入；{{节点id}} 节点输出 */
    private String render(String template, String userInput, Map<String, String> outputs) {
        if (template == null || template.isBlank()) {
            return template;
        }
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val;
            if ("input".equals(key)) {
                val = userInput == null ? "" : userInput;
            } else {
                val = outputs.getOrDefault(key, "");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
