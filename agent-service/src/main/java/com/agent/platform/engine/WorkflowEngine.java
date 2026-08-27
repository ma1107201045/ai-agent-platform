package com.agent.platform.engine;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.service.ModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

            if ("llm".equals(node.getType()) || "http".equals(node.getType())) {
                input = render(System.lineSeparator() + node.getLabel(), userInput, outputs).trim();
                if (output != null && output.length() > 300) {
                    output = output.substring(0, 300) + "...";
                }
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

    /** 执行单个节点，返回节点输出文本（LLM 回答 / HTTP 响应体） */
    private String executeNode(WorkflowGraph.WorkflowNode node, String userInput, Map<String, String> outputs) {
        switch (node.getType()) {
            case "start":
                return null;
            case "end":
                return null;
            case "llm":
                return executeLlm(node, userInput, outputs);
            case "http":
                return executeHttp(node, outputs);
            case "condition":
                return null;
            case "code":
                // v1 暂不支持代码节点，跳过
                return null;
            default:
                return null;
        }
    }

    /** LLM 节点：系统提示词 + 用户输入（支持 {{input}} / {{节点id}} 变量替换） */
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

        ChatModel chatModel = modelService.chatModelOf(modelId);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(render(systemPrompt, userInput, outputs)),
                        ChatMessage.user(render(userInput, userInput, outputs))))
                .temperature(temperature)
                .build();
        ChatResponse response = chatModel.call(request);
        String content = response == null ? null : response.getContent();
        outputs.put(node.getId(), content == null ? "" : content);
        return content;
    }

    /** HTTP 节点：GET / POST 调用外部 API */
    private String executeHttp(WorkflowGraph.WorkflowNode node, Map<String, String> outputs) {
        Map<String, Object> cfg = node.getConfig();
        String url = cfg == null ? "" : String.valueOf(cfg.getOrDefault("url", ""));
        if (url.isBlank() || "null".equals(url)) {
            throw new BizException("HTTP 节点「" + node.getLabel() + "」未配置请求地址");
        }
        String method = cfg.get("method") == null ? "GET" : String.valueOf(cfg.get("method")).toUpperCase();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if ("POST".equals(method) || "PUT".equals(method)) {
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
            throw new BizException("HTTP 节点「" + node.getLabel() + "」请求失败: " + e.getMessage());
        }
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

    /** 条件表达式简化求值：true/false 字面量、{{变量}} 非空判断、其余按布尔字符串解析 */
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
        return !rendered.isEmpty();
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
