package com.agent.platform.llm.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 聊天请求
 */
@Data
@Builder
public class ChatRequest {

    /** 模型名，为空时使用 Provider 默认模型 */
    private String model;

    private List<ChatMessage> messages;

    private Double temperature;

    private Double topP;

    private Integer maxTokens;

    private List<FunctionTool> tools;

    /** 结构化输出：如 {"type":"json_object"} */
    private Map<String, Object> responseFormat;

    /** 是否流式（由 ChatModel 实现自行决定是否支持） */
    private Boolean stream;
}
