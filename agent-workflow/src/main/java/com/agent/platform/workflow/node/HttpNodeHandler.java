package com.agent.platform.workflow.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.workflow.NodeType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * HTTP 请求节点：GET / POST / PUT / PATCH / DELETE 调用外部 API。
 * <p>
 * 支持配置项：
 * <ul>
 *   <li>{@code url / method} 请求地址与方式（支持变量替换）</li>
 *   <li>{@code headers} 自定义请求头（JSON 对象，值支持变量替换）</li>
 *   <li>{@code authType / authToken / authUsername / authPassword} 鉴权</li>
 *   <li>{@code bodyType} none（默认）/ json / form / raw；{@code bodyTemplate} 请求体模板</li>
 *   <li>{@code responseType} text（默认）/ json；{@code jsonPath} 从 JSON 响应中抽取字段</li>
 *   <li>{@code timeoutSeconds} 请求超时（默认 30s）；{@code ignoreStatus} 是否忽略非 2xx 状态码</li>
 * </ul>
 * 失败重试、超时兜底、错误处理等通用策略由引擎统一处理（retries / timeoutSeconds / onError）。
 */
@Component
@RequiredArgsConstructor
public class HttpNodeHandler implements NodeHandler {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    /** 默认请求体：全部上游输出 + 用户输入，兼容早期版本行为 */
    private static final boolean LEGACY_DEFAULT_BODY = false;

    @Override
    public NodeType type() {
        return NodeType.HTTP;
    }

    @Override
    public String validate(NodeContext ctx) {
        if (ctx.cfgStr("url") == null) {
            return "HTTP 节点「" + ctx.label() + "」未配置请求地址";
        }
        return null;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        String url = ctx.cfgStr("url");
        if (url == null) {
            throw new BizException("HTTP 节点「" + ctx.label() + "」未配置请求地址");
        }
        url = ctx.render(url);
        String method = ctx.cfgStr("method", "GET").toUpperCase();
        int timeout = Math.clamp(ctx.cfgInt("timeoutSeconds", DEFAULT_TIMEOUT_SECONDS), 1, MAX_TIMEOUT_SECONDS);

        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout));

        // 自定义 Headers（JSON 对象，值支持变量替换）
        Object headersObj = ctx.config().get("headers");
        if (headersObj instanceof Map<?, ?> headers) {
            for (Map.Entry<?, ?> e : headers.entrySet()) {
                builder.header(String.valueOf(e.getKey()), ctx.render(String.valueOf(e.getValue())));
            }
        }

        applyAuth(ctx, builder);
        applyBody(ctx, builder, method);

        HttpResponse<String> resp;
        try {
            resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("HTTP 节点「" + ctx.label() + "」请求被中断");
        } catch (Exception e) {
            throw new BizException("HTTP 节点「" + ctx.label() + "」请求失败：" + e.getMessage());
        }
        int status = resp.statusCode();
        if (status >= 400 && !ctx.cfgBool("ignoreStatus", false)) {
            throw new BizException("HTTP 节点「" + ctx.label() + "」请求失败，状态码 " + status
                    + "：" + truncate(resp.body()));
        }
        String body = extract(ctx, resp.body());
        ctx.emit(body);
        return NodeResult.of(body);
    }

    /** 构造请求体：none / json / form / raw */
    private void applyBody(NodeContext ctx, HttpRequest.Builder builder, String method) {
        String bodyType = ctx.cfgStr("bodyType", LEGACY_DEFAULT_BODY ? "json" : "none");
        String raw = ctx.render(ctx.cfgStr("bodyTemplate", ""));
        switch (bodyType == null ? "none" : bodyType.toLowerCase()) {
            case "json" -> {
                builder.header("Content-Type", "application/json");
                String json = raw == null || raw.isBlank() ? "{}" : raw;
                builder.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            }
            case "form" -> {
                builder.header("Content-Type", "application/x-www-form-urlencoded");
                builder.method(method, HttpRequest.BodyPublishers.ofString(toFormBody(ctx, raw), StandardCharsets.UTF_8));
            }
            case "raw" -> {
                builder.header("Content-Type", "text/plain; charset=UTF-8");
                builder.method(method, HttpRequest.BodyPublishers.ofString(raw == null ? "" : raw, StandardCharsets.UTF_8));
            }
            default -> {
                if ("GET".equals(method) || "DELETE".equals(method)) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    // 未显式配置请求体：POST/PUT 沿用输出集合 JSON，保证旧流程行为不变
                    builder.header("Content-Type", "application/json");
                    String body;
                    try {
                        body = ctx.objectMapper().writeValueAsString(ctx.outputs());
                    } catch (Exception e) {
                        throw new BizException("HTTP 节点「" + ctx.label() + "」序列化请求体失败：" + e.getMessage());
                    }
                    builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                }
            }
        }
    }

    /**
     * 表单体：模板为 JSON 对象时按 key=value& 拼接（值做 URL 编码），
     * 否则视为已编码的表单串原样发送。
     */
    private String toFormBody(NodeContext ctx, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (!text.startsWith("{")) {
            return text;
        }
        try {
            JsonNode node = ctx.objectMapper().readTree(text);
            StringBuilder sb = new StringBuilder();
            node.fieldNames().forEachRemaining(name -> {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(node.get(name).asText(), StandardCharsets.UTF_8));
            });
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("HTTP 节点「" + ctx.label() + "」表单请求体不是合法 JSON：" + e.getMessage());
        }
    }

    /** 鉴权：none / bearer / basic */
    private void applyAuth(NodeContext ctx, HttpRequest.Builder builder) {
        String authType = ctx.cfgStr("authType", "none");
        if ("bearer".equalsIgnoreCase(authType)) {
            String token = ctx.render(ctx.cfgStr("authToken", ""));
            builder.header("Authorization", "Bearer " + token);
        } else if ("basic".equalsIgnoreCase(authType)) {
            String username = ctx.render(ctx.cfgStr("authUsername", ""));
            String password = ctx.render(ctx.cfgStr("authPassword", ""));
            String encoded = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }
    }

    /** 响应处理：text 原样；json 按 jsonPath 抽取字段（支持 a.b[0].c） */
    private String extract(NodeContext ctx, String body) {
        if (body == null) {
            return "";
        }
        if (!"json".equalsIgnoreCase(ctx.cfgStr("responseType", "text"))) {
            return body;
        }
        String path = ctx.cfgStr("jsonPath");
        try {
            JsonNode root = ctx.objectMapper().readTree(body);
            if (path == null || path.isBlank()) {
                return root.toString();
            }
            JsonNode cur = root;
            for (String part : path.replace("$", "").split("\\.")) {
                if (part.isBlank()) {
                    continue;
                }
                if (cur == null) {
                    return "";
                }
                String name = part;
                int idx = -1;
                int lb = part.indexOf('[');
                if (lb >= 0 && part.endsWith("]")) {
                    name = part.substring(0, lb);
                    idx = Integer.parseInt(part.substring(lb + 1, part.length() - 1));
                }
                if (!name.isBlank()) {
                    cur = cur.get(name);
                }
                if (idx >= 0 && cur != null) {
                    cur = cur.get(idx);
                }
            }
            if (cur == null || cur.isNull()) {
                return "";
            }
            return cur.isValueNode() ? cur.asText() : cur.toString();
        } catch (Exception e) {
            throw new BizException("HTTP 节点「" + ctx.label() + "」解析 JSON 响应失败：" + e.getMessage());
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    @Override
    public String describeInput(NodeContext ctx) {
        String method = ctx.cfgStr("method", "GET");
        return method + " " + ctx.render(ctx.cfgStr("url", "")).trim();
    }
}
