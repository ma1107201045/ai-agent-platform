package com.agent.platform.llm.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 流式聊天增量块
 */
@Data
@Builder
public class ChatChunk {

    /** 内容增量 */
    private String delta;

    /** 工具调用增量（调用方需自行拼接） */
    private List<ToolCall> toolCalls;

    private String finishReason;

    /** 仅最后一个 chunk 可能携带 */
    private Usage usage;

    private String model;
}
