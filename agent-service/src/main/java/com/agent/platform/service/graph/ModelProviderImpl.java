package com.agent.platform.service.graph;

import com.agent.platform.graph.spi.ModelProvider;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.service.ModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ModelProvider} 的业务实现：委托给 {@link ModelService}。
 * <p>
 * 本类是 agent-service 与 agent-graph 之间的适配层，
 * 使图引擎无需依赖模型管理实现即可获取对话模型。
 */
@Component
@RequiredArgsConstructor
public class ModelProviderImpl implements ModelProvider {

    private final ModelService modelService;

    @Override
    public ChatModel chatModelOf(Long modelId) {
        return modelService.chatModelOf(modelId);
    }
}
