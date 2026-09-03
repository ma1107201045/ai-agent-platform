package com.agent.platform.dao.dto.app;

import lombok.Data;

import java.util.Map;

/**
 * 渲染模板正文（{{var}} 占位替换）入参
 */
@Data
public class AppPromptRenderDTO {
    private String content;
    private Map<String, Object> variables;
}
