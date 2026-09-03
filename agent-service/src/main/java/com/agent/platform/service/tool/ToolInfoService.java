package com.agent.platform.service.tool;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.tool.ToolInfo;
import com.agent.platform.dao.mapper.tool.ToolInfoMapper;
import com.agent.platform.llm.model.FunctionTool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.mvel2.MVEL;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具（AppAgentTool）服务：注册表 CRUD + 工具执行（HTTP / 代码）。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service」对齐规则：
 * 表 app_agent_tool → 实体 AppAgentTool → Mapper AppAgentToolMapper → 本类 AppAgentToolService。
 */
@Service
@RequiredArgsConstructor
public class ToolInfoService {

    private final ToolInfoMapper toolMapper;
    private final ObjectMapper objectMapper;

    // ---------- CRUD ----------

    public Page<ToolInfo> page(long page, long size) {
        return toolMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ToolInfo>().orderByDesc(ToolInfo::getId));
    }

    public List<ToolInfo> listEnabled() {
        return toolMapper.selectList(new LambdaQueryWrapper<ToolInfo>()
                .eq(ToolInfo::getStatus, 1)
                .orderByAsc(ToolInfo::getId));
    }

    public ToolInfo getById(Long id) {
        ToolInfo tool = toolMapper.selectById(id);
        if (tool == null) {
            throw new BizException("工具不存在: " + id);
        }
        return tool;
    }

    public ToolInfo create(ToolInfo tool) {
        validate(tool);
        tool.setId(null);
        if (tool.getTenantId() == null) {
            tool.setTenantId(1L);
        }
        if (tool.getStatus() == null) {
            tool.setStatus(1);
        }
        if (tool.getType() == null) {
            tool.setType("http");
        }
        if (tool.getMethod() == null) {
            tool.setMethod("GET");
        }
        if (tool.getAuthType() == null) {
            tool.setAuthType("none");
        }
        toolMapper.insert(tool);
        return tool;
    }

    public void update(ToolInfo tool) {
        getById(tool.getId());
        validate(tool);
        toolMapper.updateById(tool);
    }

    public void delete(Long id) {
        getById(id);
        toolMapper.deleteById(id);
    }

    private void validate(ToolInfo tool) {
        if (tool.getName() == null || tool.getName().isBlank()) {
            throw new BizException("工具名称不能为空");
        }
        if (!tool.getName().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new BizException("工具名称须为英文标识符（字母/数字/下划线，首字符非数字）");
        }
        if (tool.getDescription() == null || tool.getDescription().isBlank()) {
            throw new BizException("工具描述不能为空（模型依赖描述决定何时调用）");
        }
        if ("http".equals(tool.getType())) {
            if (tool.getUrl() == null || tool.getUrl().isBlank()) {
                throw new BizException("HTTP 工具必须配置请求地址");
            }
        } else if ("code".equals(tool.getType())) {
            if (tool.getCode() == null || tool.getCode().isBlank()) {
                throw new BizException("代码工具必须配置脚本");
            }
        } else {
            throw new BizException("不支持的工具类型: " + tool.getType());
        }
    }

    // ---------- 转换与执行 ----------

    /** 转换为 LLM FunctionTool（供 ChatRequest.tools 使用） */
    public FunctionTool toFunctionTool(ToolInfo tool) {
        String parameters = tool.getParameters() == null || tool.getParameters().isBlank()
                ? "{\"type\":\"object\",\"properties\":{}}"
                : tool.getParameters();
        return new FunctionTool(tool.getName(), tool.getDescription(), parameters);
    }

    public List<FunctionTool> toFunctionTools(List<ToolInfo> tools) {
        List<FunctionTool> result = new ArrayList<>();
        for (ToolInfo t : tools) {
            result.add(toFunctionTool(t));
        }
        return result;
    }

    /**
     * 执行工具。
     *
     * @param tool      工具
     * @param arguments 模型生成的参数（JSON 对象字符串，可为 null）
     * @return 执行结果文本
     */
    public String execute(ToolInfo tool, String arguments) {
        Map<String, Object> args = parseArguments(arguments);
        if ("http".equals(tool.getType())) {
            return executeHttp(tool, args);
        }
        if ("code".equals(tool.getType())) {
            return executeCode(tool, args);
        }
        throw new BizException("不支持的工具类型: " + tool.getType());
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = objectMapper.readTree(arguments);
            if (node.isObject()) {
                return objectMapper.convertValue(node, objectMapper.getTypeFactory()
                        .constructMapType(LinkedHashMap.class, String.class, Object.class));
            }
        } catch (Exception ignored) {
        }
        return Map.of("value", arguments);
    }

    /** HTTP 工具：GET 参数拼 query，其他方法参数作为 JSON body */
    private String executeHttp(ToolInfo tool, Map<String, Object> args) {
        try {
            String url = tool.getUrl();
            String method = tool.getMethod() == null ? "GET" : tool.getMethod().toUpperCase();
            String query = "";
            if ("GET".equals(method) && !args.isEmpty()) {
                StringBuilder qs = new StringBuilder();
                for (Map.Entry<String, Object> e : args.entrySet()) {
                    if (!qs.isEmpty()) qs.append('&');
                    qs.append(e.getKey()).append('=')
                            .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
                }
                query = "?" + qs;
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url + query))
                    .timeout(Duration.ofSeconds(30));

            // 自定义 Headers
            if (tool.getHeaders() != null && !tool.getHeaders().isBlank()) {
                JsonNode headers = objectMapper.readTree(tool.getHeaders());
                if (headers.isObject()) {
                    headers.fields().forEachRemaining(en ->
                            builder.header(en.getKey(), en.getValue().asText()));
                }
            }
            // 鉴权
            String authType = tool.getAuthType() == null ? "none" : tool.getAuthType();
            if ("bearer".equalsIgnoreCase(authType) && tool.getAuthToken() != null) {
                builder.header("Authorization", "Bearer " + tool.getAuthToken());
            }

            HttpRequest request;
            if ("GET".equals(method)) {
                request = builder.GET().build();
            } else {
                builder.header("Content-Type", "application/json");
                String body = objectMapper.writeValueAsString(args);
                request = builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            }
            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                return "HTTP " + resp.statusCode() + ": " + resp.body();
            }
            return resp.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("工具「" + tool.getName() + "」执行失败: " + e.getMessage());
        }
    }

    /** 代码工具：MVEL 执行，变量含各参数与 input（参数整体 JSON 字符串） */
    private String executeCode(ToolInfo tool, Map<String, Object> args) {
        try {
            Map<String, Object> vars = new LinkedHashMap<>(args);
            try {
                vars.put("input", objectMapper.writeValueAsString(args));
            } catch (JsonProcessingException e) {
                vars.put("input", String.valueOf(args));
            }
            Object result = MVEL.executeExpression(MVEL.compileExpression(tool.getCode()), vars);
            return result == null ? "" : String.valueOf(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("工具「" + tool.getName() + "」执行失败: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
}
