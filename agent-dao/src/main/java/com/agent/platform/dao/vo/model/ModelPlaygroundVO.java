package com.agent.platform.dao.vo.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模型广场目录项：展示供应商下每个模型及其可用状态（不含 API Key 等敏感信息）
 */
@Data
@Builder
public class ModelPlaygroundVO {

    /** 模型 ID（试玩调用时使用） */
    private Long modelId;

    /** 模型名 */
    private String modelName;

    /** 模型类型：llm / embedding / rerank / tts / asr / image */
    private String modelType;

    private Integer contextWindow;

    private Integer maxTokens;

    /** 能力标签（如 function_call / vision / stream / json） */
    private List<String> capabilities;

    /** 模型状态：0禁用 1启用 */
    private Integer modelStatus;

    private Long providerId;

    private String providerName;

    /** 供应商类型：openai-compatible / anthropic 等 */
    private String providerType;

    /** 供应商状态：0禁用 1启用（供应商禁用时模型不可用） */
    private Integer providerStatus;
}
