package com.agent.platform.service.tool;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.tool.ToolInfo;
import com.agent.platform.dao.entity.tool.ToolConnector;
import com.agent.platform.dao.mapper.tool.ToolInfoMapper;
import com.agent.platform.dao.mapper.tool.ToolConnectorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * 数据集成 - 外部连接器服务：CRUD + 连通性测试 + 一键生成 HTTP 工具。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service」对齐规则：
 * 表 tool_connector → 实体 ToolConnector → Mapper ToolConnectorMapper → 本类 ToolConnectorService。
 */
@Service
@RequiredArgsConstructor
public class ToolConnectorService {

    private static final String DEFAULT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private final ToolConnectorMapper connectorMapper;
    private final ToolInfoMapper toolMapper;
    private final ObjectMapper objectMapper;

    // ---------- CRUD ----------

    public Page<ToolConnector> page(long page, long size, String keyword, String type, Integer status) {
        LambdaQueryWrapper<ToolConnector> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(ToolConnector::getName, keyword).or().like(ToolConnector::getDescription, keyword));
        }
        if (type != null && !type.isBlank()) {
            qw.eq(ToolConnector::getType, type);
        }
        if (status != null) {
            qw.eq(ToolConnector::getStatus, status);
        }
        qw.orderByDesc(ToolConnector::getId);
        return connectorMapper.selectPage(new Page<>(page, size), qw);
    }

    public ToolConnector getById(Long id) {
        ToolConnector connector = connectorMapper.selectById(id);
        if (connector == null) {
            throw new BizException("连接器不存在: " + id);
        }
        return connector;
    }

    public ToolConnector create(ToolConnector connector) {
        validate(connector);
        connector.setId(null);
        if (connector.getTenantId() == null) {
            connector.setTenantId(1L);
        }
        if (connector.getStatus() == null) {
            connector.setStatus(1);
        }
        if (connector.getType() == null) {
            connector.setType("http");
        }
        if (connector.getMethod() == null) {
            connector.setMethod("GET");
        }
        if (connector.getAuthType() == null) {
            connector.setAuthType("none");
        }
        connectorMapper.insert(connector);
        return connector;
    }

    public void update(Long id, ToolConnector connector) {
        getById(id);
        connector.setId(id);
        validate(connector);
        connectorMapper.updateById(connector);
    }

    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态值不合法（0/1）");
        }
        ToolConnector connector = getById(id);
        connector.setStatus(status);
        connectorMapper.updateById(connector);
    }

    public void delete(Long id) {
        getById(id);
        connectorMapper.deleteById(id);
    }

    private void validate(ToolConnector connector) {
        if (connector.getName() == null || connector.getName().isBlank()) {
            throw new BizException("连接器名称不能为空");
        }
        if (!connector.getName().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new BizException("连接器名称须为英文标识符（字母/数字/下划线，首字符非数字）");
        }
        String type = connector.getType() == null ? "http" : connector.getType();
        if ("http".equals(type)) {
            if (connector.getUrl() == null || connector.getUrl().isBlank()) {
                throw new BizException("HTTP 连接器必须配置 API 地址");
            }
            if (!connector.getUrl().startsWith("http://") && !connector.getUrl().startsWith("https://")) {
                throw new BizException("HTTP 连接器地址须以 http:// 或 https:// 开头");
            }
            if (connector.getHeaders() != null && !connector.getHeaders().isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(connector.getHeaders());
                    if (!node.isObject()) {
                        throw new BizException("Headers 必须是合法 JSON 对象");
                    }
                } catch (BizException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BizException("Headers 必须是合法 JSON 对象");
                }
            }
        } else if ("mysql".equals(type)) {
            if (connector.getUrl() == null || connector.getUrl().isBlank()) {
                throw new BizException("MySQL 连接器必须配置 JDBC URL（如 jdbc:mysql://127.0.0.1:3306/agent_platform）");
            }
            if (!connector.getUrl().startsWith("jdbc:mysql://")) {
                throw new BizException("MySQL 连接器地址须以 jdbc:mysql:// 开头");
            }
            if (connector.getAuthUsername() == null || connector.getAuthUsername().isBlank()) {
                throw new BizException("MySQL 连接器必须配置用户名");
            }
        } else {
            throw new BizException("暂不支持的连接器类型: " + type);
        }
    }

    // ---------- 连通性测试 ----------

    /**
     * 连通性测试：不抛异常，以可读文本返回成功或失败原因（便于前端直接展示）。
     */
    public String test(Long id) {
        ToolConnector connector = getById(id);
        if ("mysql".equals(connector.getType())) {
            return testMysql(connector);
        }
        return testHttp(connector);
    }

    private String testHttp(ToolConnector connector) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(connector.getUrl()))
                    .timeout(Duration.ofSeconds(15));
            // 自定义请求头
            if (connector.getHeaders() != null && !connector.getHeaders().isBlank()) {
                JsonNode headers = objectMapper.readTree(connector.getHeaders());
                if (headers.isObject()) {
                    headers.fields().forEachRemaining(en ->
                            builder.header(en.getKey(), en.getValue().asText()));
                }
            }
            // 鉴权
            String authType = connector.getAuthType() == null ? "none" : connector.getAuthType();
            if ("bearer".equalsIgnoreCase(authType) && connector.getAuthToken() != null) {
                builder.header("Authorization", "Bearer " + connector.getAuthToken());
            } else if ("basic".equalsIgnoreCase(authType)) {
                String raw = (connector.getAuthUsername() == null ? "" : connector.getAuthUsername())
                        + ":" + (connector.getAuthPassword() == null ? "" : connector.getAuthPassword());
                builder.header("Authorization", "Basic "
                        + java.util.Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                return "已连通（HTTP " + resp.statusCode() + "）但服务返回错误：" + preview(resp.body());
            }
            return "连接成功（HTTP " + resp.statusCode() + "）· " + preview(resp.body());
        } catch (Exception e) {
            return "连接失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private String testMysql(ToolConnector connector) {
        String username = connector.getAuthUsername() == null ? "" : connector.getAuthUsername();
        String password = connector.getAuthPassword() == null ? "" : connector.getAuthPassword();
        try (Connection connection = DriverManager.getConnection(connector.getUrl(), username, password)) {
            String product = connection.getMetaData().getDatabaseProductName();
            String version = connection.getMetaData().getDatabaseProductVersion();
            try (PreparedStatement ps = connection.prepareStatement("SELECT 1");
                 var rs = ps.executeQuery()) {
                rs.next();
            }
            return "连接成功 · " + product + " " + version + " · SELECT 1 执行正常";
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return "连接失败（错误码 " + e.getErrorCode() + "）：" + msg;
        }
    }

    private String preview(String body) {
        if (body == null || body.isBlank()) {
            return "响应为空";
        }
        String compact = body.replaceAll("\\s+", " ");
        return compact.length() > 200 ? compact.substring(0, 200) + "…" : compact;
    }

    // ---------- 集成调用：连接器 → HTTP 工具 ----------

    /**
     * 将 HTTP 连接器一键生成为可被智能体调用的 HTTP 工具（app_agent_tool），打通「数据集成 → 工具 → 模型调用」。
     */
    public ToolInfo createHttpTool(Long connectorId) {
        ToolConnector connector = getById(connectorId);
        if (!"http".equals(connector.getType())) {
            throw new BizException("仅 HTTP 类型连接器可直接生成工具；MySQL 连接器可在「工具管理」中编写数据库查询脚本使用");
        }
        Long exists = toolMapper.selectCount(new LambdaQueryWrapper<ToolInfo>()
                .eq(ToolInfo::getName, connector.getName()));
        if (exists != null && exists > 0) {
            throw new BizException("已存在同名工具「" + connector.getName() + "」，可在工具管理中直接使用或重命名连接器后再生成");
        }
        ToolInfo tool = new ToolInfo();
        tool.setTenantId(1L);
        tool.setName(connector.getName());
        tool.setDescription(connector.getDescription() == null || connector.getDescription().isBlank()
                ? "由数据集成连接器「" + connector.getName() + "」生成"
                : connector.getDescription());
        tool.setType("http");
        tool.setUrl(connector.getUrl());
        tool.setMethod(connector.getMethod() == null ? "GET" : connector.getMethod());
        tool.setHeaders(connector.getHeaders());
        tool.setAuthType(connector.getAuthType() == null ? "none" : connector.getAuthType());
        tool.setAuthToken(connector.getAuthToken());
        tool.setParameters(DEFAULT_SCHEMA);
        tool.setStatus(1);
        toolMapper.insert(tool);
        return tool;
    }
}
