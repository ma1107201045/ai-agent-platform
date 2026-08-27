package com.agent.platform.llm.model;

/**
 * 函数工具定义（供模型调用）
 *
 * @param name            函数名
 * @param description     函数描述
 * @param parametersJson  JSON Schema 格式的参数定义（字符串）
 */
public record FunctionTool(String name, String description, String parametersJson) {
}
