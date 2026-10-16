package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppTool;
import com.agent.platform.dao.vo.app.AppToolTemplateVO;
import lombok.Data;
import java.util.List;

/**
 * 插件市场服务：内置工具模板目录 + 一键安装到当前工作空间。
 *
 * <p>模板定义由服务端内置维护（随版本更新补充），安装即创建一条真实的
 * {@link AppTool} 记录，复用既有 HTTP / 代码工具执行能力，模型可立即调用。
 *
 * <p>约定：Template.name 即安装后的工具名（英文标识符）；目录中与现有工具同名的模板视为「已安装」。
 */
public interface AppToolMarketplaceService {

    // ---------- 模板定义 ----------

    @Data
    public static class Template {
        /** 模板唯一标识（安装接口使用） */
        private String key;
        /** 工具名称（英文标识符，安装后即 app_agent_tool.name） */
        private String name;
        private String description;
        private String category;
        /** http / code */
        private String type;
        private String method;
        private String url;
        private String code;
        /** 参数 JSON Schema */
        private String parameters;
    }

    /**
     * 模板目录（含已安装标记）。可按分类 / 关键字过滤。
     */
    public List<AppToolTemplateVO> templates(String category, String keyword);

    /**
     * 按模板一键安装：以模板内容创建一条真实工具记录。
     */
    public AppTool install(String key);
}
