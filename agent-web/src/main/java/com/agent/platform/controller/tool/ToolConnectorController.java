package com.agent.platform.controller.tool;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.tool.ToolInfo;
import com.agent.platform.dao.entity.tool.ToolConnector;
import com.agent.platform.service.tool.ToolConnectorService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 数据集成 - 连接器管理接口。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service → Controller → URL」对齐规则：
 * <pre>
 *   表 tool_connector → 实体 ToolConnector → Mapper ToolConnectorMapper
 *                     → Service ToolConnectorService → 本类 ToolConnectorController
 *                     → URL /api/tool/connectors（kebab-case 复数）
 * </pre>
 */
@RestController
@RequestMapping("/api/tool/connectors")
@RequiredArgsConstructor
public class ToolConnectorController {

    private final ToolConnectorService connectorService;

    @GetMapping
    public Result<Page<ToolConnector>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(connectorService.page(page, size, keyword, type, status));
    }

    @GetMapping("/{id}")
    public Result<ToolConnector> getById(@PathVariable Long id) {
        return Result.ok(connectorService.getById(id));
    }

    @PostMapping
    public Result<ToolConnector> create(@RequestBody ToolConnector connector) {
        return Result.ok(connectorService.create(connector));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ToolConnector connector) {
        connectorService.update(id, connector);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        connectorService.delete(id);
        return Result.ok();
    }

    /** 启用 / 禁用 */
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        connectorService.updateStatus(id, req.getStatus());
        return Result.ok();
    }

    /** 连通性测试（HTTP 发请求、MySQL 建立 JDBC 连接），返回可读结果文本 */
    @PostMapping("/{id}/test")
    public Result<String> test(@PathVariable Long id) {
        return Result.ok(connectorService.test(id));
    }

    /** 一键将 HTTP 连接器生成为可被智能体调用的 HTTP 工具 */
    @PostMapping("/{id}/as-tool")
    public Result<ToolInfo> asTool(@PathVariable Long id) {
        return Result.ok(connectorService.createHttpTool(id));
    }

    @Data
    public static class StatusReq {
        private Integer status;
    }
}
