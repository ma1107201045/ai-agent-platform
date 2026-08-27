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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.mvel2.MVEL;

/**
 * 工作流执行引擎（v1：链式顺序执行）
 * <p>
 * 支持节点：start / end / llm / http / condition（简化）/ code（暂跳过）
 * 变量：{{input}} 用户输入；{{节点id}} 取该节点输出文本
 */
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");

    private final ModelService modelService;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行工作流
     *
     * @param graph      DSL 图
     * @param userInput  用户最新输入
     */
    public RunResult run(WorkflowGraph graph, String userInput) {
        List<RunResult.TraceItem> trace = new ArrayList<>();
        Map<String, String> outputs = new HashMap<>();
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return RunResult.builder().answer("").trace(trace).build();
        }

        WorkflowGraph.WorkflowNode startNode = graph.getNodes().stream()
                .filter(n -> "start".equals(n.getType()))
                .findFirst()
                .orElse(graph.getNodes().get(0));

        String currentId = startNode.getId();
        Set<String> visited = new HashSet<>();
        List<WorkflowGraph.WorkflowNode> executed = new ArrayList<>();
        String lastAnswer = userInput == null ? "" : userInput;

        while (currentId != null && visited.add(currentId)) {
            String targetId = currentId;
            Optional<WorkflowGraph.WorkflowNode> opt = graph.getNodes().stream()
                    .filter(n -> n.getId().equals(targetId))
                    .findFirst();
            if (opt.isEmpty()) {
                break;
            }
            WorkflowGraph.WorkflowNode node = opt.get();
            long start = System.currentTimeMillis();
            String status = "success";
            String error = null;
            String output = null;
            String input = null;
            try {
                output = executeNode(node, userInput, outputs);
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
                input = render(tpl == null ? "" : String.valueOf(tpl), userInput, outputs).trim();
            } else if ("knowledge".equals(node.getType())) {
                Object q = node.getConfig() == null ? null : node.getConfig().get("queryTemplate");
                input = render(q == null ? "{{input}}" : String.valueOf(q), userInput, outputs).trim();
            } else if ("llm".equals(node.getType()) || "http".equals(node.getType()) || "code".equals(node.getType())) {
                input = render(System.lineSeparator() + node.getLabel(), userInput, outputs).trim();
            }
            if (output != null && output.length() > 300) {
                output = output.substring(0, 300) + "...";
            }
            trace.add(RunResult.TraceItem.builder()
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
                // 出错时中断，用错误信息作为回答
                lastAnswer = "节点执行失败：" + node.getLabel() + " - " + error;
                break;
            }
            if ("end".equals(node.getType())) {
                break;
            }
            if ("llm".equals(node.getType()) && output != null) {
                lastAnswer = output;
            }
            executed.add(node);
            currentId = nextNodeId(graph, node, outputs);
        }

        return RunResult.builder().answer(lastAnswer).trace(trace).build();
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

    /** 计算下一节点：条件节点按表达式选边（true→第一条 true 边，false→第一条 false 边） */
    private String nextNodeId(WorkflowGraph graph, WorkflowGraph.WorkflowNode node, Map<String, String> outputs) {
        List<WorkflowGraph.WorkflowEdge> outs = graph.getEdges() == null ? List.of()
                : graph.getEdges().stream()
                        .filter(e -> e.getSource().equals(node.getId()))
                        .collect(Collectors.toList());
        if (outs.isEmpty()) {
            return null;
        }
        if ("condition".equals(node.getType())) {
            boolean cond = evalCondition(node, outputs);
            String handle = cond ? "true" : "false";
            Optional<WorkflowGraph.WorkflowEdge> byHandle = outs.stream()
                    .filter(e -> handle.equalsIgnoreCase(e.getSourceHandle()))
                    .findFirst();
            if (byHandle.isPresent()) {
                return byHandle.get().getTarget();
            }
            return outs.get(0).getTarget();
        }
        return outs.get(0).getTarget();
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
