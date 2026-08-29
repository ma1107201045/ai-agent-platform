package com.agent.platform.workflow.spi;

import com.agent.platform.llm.spi.ChatModel;

/**
 * 模型获取能力 SPI（由业务模块实现并注入）
 * <p>
 * 引擎只依赖本接口，不依赖模型管理的具体实现，从而与业务模块解耦。
 */
public interface ModelProvider {

    /**
     * 按模型 ID 获取可用的对话模型实例。
     *
     * @throws RuntimeException 模型不存在或不可用时
     */
    ChatModel chatModelOf(Long modelId);
}
