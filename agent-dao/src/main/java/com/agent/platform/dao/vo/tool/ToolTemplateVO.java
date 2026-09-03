package com.agent.platform.dao.vo.tool;

import lombok.Data;

/**
 * 插件市场卡片视图：模板字段 + 是否已安装
 */
@Data
public class ToolTemplateVO {
    private String key;
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
    private boolean installed;
}
