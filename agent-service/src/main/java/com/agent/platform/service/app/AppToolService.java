package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppTool;
import com.agent.platform.llm.model.FunctionTool;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Agent 工具（AppAgentTool）服务：注册表 CRUD + 工具执行（HTTP / 代码）。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service」对齐规则：
 * 表 app_agent_tool → 实体 AppAgentTool → Mapper AppAgentToolMapper → 本类 AppAgentToolService。
 */
public interface AppToolService {

    public Page<AppTool> page(long page, long size);

    public List<AppTool> listEnabled();

    public AppTool getById(Long id);

    public AppTool create(AppTool tool);

    public void update(AppTool tool);

    public void delete(Long id);

    /** 转换为 LLM FunctionTool（供 ChatRequest.tools 使用） */
    public FunctionTool toFunctionTool(AppTool tool);

    public List<FunctionTool> toFunctionTools(List<AppTool> tools);

    /**
     * 执行工具。
     *
     * @param tool      工具
     * @param arguments 模型生成的参数（JSON 对象字符串，可为 null）
     * @return 执行结果文本
     */
    public String execute(AppTool tool, String arguments);
}
