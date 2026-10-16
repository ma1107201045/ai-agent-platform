package com.agent.platform.service.model;

import com.agent.platform.dao.entity.model.ModelGatewayRoute;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/**
 * 模型网关路由：配置 fallback / 轮询 / 加权路由目标，
 * 提供 simulate 决策模拟（含目标模型名回显与调用计数）。
 */
public interface ModelGatewayService {

    public Page<ModelGatewayRoute> page(long page, long size, String keyword, Integer enabled);

    public ModelGatewayRoute get(Long id);

    public ModelGatewayRoute create(ModelGatewayRoute route, List<Map<String, Object>> targets);

    public ModelGatewayRoute update(Long id, ModelGatewayRoute req, List<Map<String, Object>> targets);

    public void delete(Long id);

    /** 路由决策模拟：按策略选择一个目标模型 */
    public Map<String, Object> simulate(Long id);
}
