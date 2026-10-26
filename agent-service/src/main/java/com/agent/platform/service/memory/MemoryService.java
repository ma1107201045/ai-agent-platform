package com.agent.platform.service.memory;

import com.agent.platform.dao.entity.memory.MemItem;
import com.agent.platform.dao.entity.memory.MemStrategy;
import com.agent.platform.dao.entity.memory.MemVariable;
import java.util.List;

/**
 * 记忆管理服务：记忆策略 / 会话变量 / 长期记忆条目
 *
 * <p>本服务提供管理面能力（增删改查 + 检索），不直接参与对话链路；
 * 运行时注入逻辑由编排/对话服务按 {@link MemStrategy} 读取记忆后拼装 Prompt。</p>
 */
public interface MemoryService {

    /**
     * 获取某应用的记忆策略；不存在时返回一份默认配置（enabled=0 需用户显式开启）。
     */
    MemStrategy getStrategy(Long appId);

    /**
     * 保存记忆策略（按 appId upsert）。
     */
    MemStrategy saveStrategy(MemStrategy strategy);

    List<MemVariable> listVariables(Long appId, String scope, String keyword);

    MemVariable createVariable(Long appId, MemVariable variable);

    void updateVariable(Long id, MemVariable variable);

    void deleteVariable(Long id);

    List<MemItem> listItems(Long appId, String category, String scope, String keyword);

    MemItem createItem(Long appId, MemItem item);

    void updateItem(Long id, MemItem item);

    void deleteItem(Long id);
}
