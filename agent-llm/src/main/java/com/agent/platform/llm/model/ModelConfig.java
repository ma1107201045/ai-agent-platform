package com.agent.platform.llm.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 模型实例配置（由供应商 + 模型组合而来）
 */
@Data
@Builder
public class ModelConfig {

    /** 供应商类型，如 openai-compatible */
    private String provider;

    /** API 基础地址 */
    private String baseUrl;

    private String apiKey;

    /** 默认模型名 */
    private String modelName;

    /** 扩展配置（超时、额外 header 等） */
    private Map<String, Object> extra;
}
