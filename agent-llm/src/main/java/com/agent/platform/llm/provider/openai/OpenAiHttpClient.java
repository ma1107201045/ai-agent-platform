package com.agent.platform.llm.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.platform.llm.exception.LlmException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * OpenAI 兼容 HTTP 客户端封装（JDK HttpClient）。
 * <p>
 * 超时策略：连接超时固定 10s；非流式请求设置整体超时（默认 120s）；
 * 流式请求不设置整体超时（长生成可达数分钟，整体超时会误杀），
 * 依靠连接超时与调用方的取消/中断兜底。
 * 非流式请求在网络 IO 异常时自动重试一次。
 */
public class OpenAiHttpClient {

    public static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** 网络 IO 异常时的最大尝试次数（首次 + 1 次重试） */
    private static final int MAX_ATTEMPTS = 2;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;

    public OpenAiHttpClient(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /** 发送非流式 JSON 请求，返回解析后的 JSON（网络 IO 异常自动重试一次） */
    public JsonNode postJson(String path, Map<String, Object> body) {
        LlmException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String requestBody = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    // HTTP 层错误（4xx/5xx）不重试，直接抛出
                    throw new LlmException("LLM 调用失败 HTTP " + response.statusCode() + ": " + response.body());
                }
                return objectMapper.readTree(response.body());
            } catch (IOException e) {
                lastError = new LlmException("LLM 调用 IO 异常: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("LLM 调用被中断", e);
            }
        }
        throw lastError;
    }

    /**
     * 发送流式请求，返回响应体 InputStream（调用方负责关闭）。
     * 不设置请求整体超时，避免误杀长时间流式生成。
     */
    public HttpResponse<java.io.InputStream> postStream(String path, Map<String, Object> body) {
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                throw new LlmException("LLM 流式调用失败 HTTP " + response.statusCode() + ": " + errorBody);
            }
            return response;
        } catch (IOException e) {
            throw new LlmException("LLM 流式调用 IO 异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM 流式调用被中断", e);
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public String baseUrl() {
        return baseUrl;
    }

    private String trimTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
