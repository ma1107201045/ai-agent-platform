package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppTool;
import com.agent.platform.dao.entity.app.AppToolConnector;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

/**
 * 数据集成 - 外部连接器服务：CRUD + 连通性测试 + 一键生成 HTTP 工具。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service」对齐规则：
 * 表 tool_connector → 实体 ToolConnector → Mapper ToolConnectorMapper → 本类 ToolConnectorService。
 */
public interface AppToolConnectorService {

    Page<AppToolConnector> page(long page, long size, String keyword, String type, Integer status);

    AppToolConnector getById(Long id);

    AppToolConnector create(AppToolConnector connector);

    void update(Long id, AppToolConnector connector);

    void updateStatus(Long id, Integer status);

    void delete(Long id);

    /**
     * 连通性测试：不抛异常，以可读文本返回成功或失败原因（便于前端直接展示）。
     */
    String test(Long id);

    /**
     * 将 HTTP 连接器一键生成为可被智能体调用的 HTTP 工具（app_agent_tool），打通「数据集成 → 工具 → 模型调用」。
     */
    AppTool createHttpTool(Long connectorId);
}
