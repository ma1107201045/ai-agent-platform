package com.agent.platform.llm.spi;

import com.agent.platform.llm.model.ChatChunk;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;

import java.util.function.Consumer;

/**
 * 对话模型 SPI
 */
public interface ChatModel {

    /** 模型名 */
    String name();

    /** 非流式调用 */
    ChatResponse call(ChatRequest request);

    /**
     * 流式调用（阻塞式回调）
     *
     * @param onChunk 每个增量块回调；结束后通过 finishReason=stop 标识
     */
    default void stream(ChatRequest request, Consumer<ChatChunk> onChunk) {
        throw new UnsupportedOperationException("当前模型不支持流式输出: " + name());
    }
}
