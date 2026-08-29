package com.agent.platform.graph.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.graph.NodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * HTTP 请求节点：GET / POST / PUT / DELETE 调用外部 API。
 * <p>
 * 支持变量替换（{{input}} / {{节点id}}）、自定义 headers、鉴权（Bearer / Basic）、失败重试。
 */
@Component
@RequiredArgsConstructor
public class HttpNodeHandler implements NodeHandler {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

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
        int retries = ctx.cfgInt("retries", 0);

        Exception lastError = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return call(ctx, url, method);
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                lastError = e;
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
        throw new BizException("HTTP 节点「" + ctx.label() + "」请求失败: "
                + (lastError == null ? "未知错误" : lastError.getMessage()));
    }

    /** 发起单次请求 */
    private NodeResult call(NodeContext ctx, String url, String method) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT);

        // 自定义 Headers（JSON 对象，支持变量替换）
        Object headersObj = ctx.config().get("headers");
        if (headersObj instanceof Map<?, ?> headers) {
            for (Map.Entry<?, ?> e : headers.entrySet()) {
                builder.header(String.valueOf(e.getKey()), ctx.render(String.valueOf(e.getValue())));
            }
        }

        applyAuth(ctx, builder);

        if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
            if (!"DELETE".equals(method)) {
                builder.header("Content-Type", "application/json");
            }
            String body = ctx.objectMapper().writeValueAsString(ctx.outputs());
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method("GET", HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String body = resp.body();
        ctx.emit(body);
        return NodeResult.of(body);
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

    @Override
    public String describeInput(NodeContext ctx) {
        return ctx.render(ctx.label()).trim();
    }
}
