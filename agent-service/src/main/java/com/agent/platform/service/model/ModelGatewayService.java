package com.agent.platform.service.model;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.model.ModelGatewayRoute;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.mapper.model.ModelGatewayRouteMapper;
import com.agent.platform.dao.mapper.model.ModelInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模型网关路由：配置 fallback / 轮询 / 加权路由目标，
 * 提供 simulate 决策模拟（含目标模型名回显与调用计数）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelGatewayService {

    private final ModelGatewayRouteMapper routeMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final ObjectMapper objectMapper;

    public Page<ModelGatewayRoute> page(long page, long size, String keyword, Integer enabled) {
        return routeMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ModelGatewayRoute>()
                        .eq(ModelGatewayRoute::getTenantId, tenant())
                        .eq(enabled != null, ModelGatewayRoute::getEnabled, enabled)
                        .and(StringUtils.hasText(keyword),
                                w -> w.like(ModelGatewayRoute::getName, keyword).or().like(ModelGatewayRoute::getDescription, keyword))
                        .orderByDesc(ModelGatewayRoute::getId));
    }

    public ModelGatewayRoute get(Long id) {
        ModelGatewayRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new BizException("路由不存在: " + id);
        }
        return route;
    }

    public ModelGatewayRoute create(ModelGatewayRoute route, List<Map<String, Object>> targets) {
        validate(route, targets);
        route.setId(null);
        route.setTenantId(tenant());
        route.setTargetsJson(writeJson(targets));
        route.setCallCount(0L);
        route.setEnabled(route.getEnabled() == null ? 1 : route.getEnabled());
        route.setIsDefault(route.getIsDefault() == null ? 0 : route.getIsDefault());
        LocalDateTime now = LocalDateTime.now();
        route.setCreateTime(now);
        route.setUpdateTime(now);
        if (route.getIsDefault() == 1) {
            clearDefault(route.getId());
        }
        routeMapper.insert(route);
        return route;
    }

    public ModelGatewayRoute update(Long id, ModelGatewayRoute req, List<Map<String, Object>> targets) {
        ModelGatewayRoute route = get(id);
        validate(req, targets);
        if (StringUtils.hasText(req.getName())) {
            route.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            route.setDescription(req.getDescription());
        }
        if (StringUtils.hasText(req.getRouteType())) {
            route.setRouteType(req.getRouteType());
        }
        if (targets != null && !targets.isEmpty()) {
            route.setTargetsJson(writeJson(targets));
        }
        if (req.getEnabled() != null) {
            route.setEnabled(req.getEnabled());
        }
        if (req.getIsDefault() != null) {
            route.setIsDefault(req.getIsDefault());
            if (req.getIsDefault() == 1) {
                clearDefault(id);
            }
        }
        route.setUpdateTime(LocalDateTime.now());
        routeMapper.updateById(route);
        return route;
    }

    public void delete(Long id) {
        get(id);
        routeMapper.deleteById(id);
    }

    /** 路由决策模拟：按策略选择一个目标模型 */
    public Map<String, Object> simulate(Long id) {
        ModelGatewayRoute route = get(id);
        if (route.getEnabled() == null || route.getEnabled() != 1) {
            throw new BizException("路由未启用，请先启用后再验证");
        }
        List<Map<String, Object>> targets = parseTargets(route.getTargetsJson());
        if (targets.isEmpty()) {
            throw new BizException("路由没有可用目标");
        }
        String type = route.getRouteType() == null ? "priority" : route.getRouteType();
        Map<String, Object> picked;
        long calls = route.getCallCount() == null ? 0 : route.getCallCount();
        switch (type) {
            case "failover":
            case "priority": {
                // 加权随机：priority 按权重、failover 取优先级最高
                if ("failover".equals(type)) {
                    Map<String, Object> best = targets.get(0);
                    for (Map<String, Object> t : targets) {
                        if (Integer.parseInt(String.valueOf(t.get("priority"))) < Integer.parseInt(String.valueOf(best.get("priority")))) {
                            best = t;
                        }
                    }
                    picked = best;
                } else {
                    picked = weightedPick(targets);
                }
                break;
            }
            case "round_robin":
            default:
                picked = targets.get((int) (calls % targets.size()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("routeId", route.getId());
        result.put("routeName", route.getName());
        result.put("strategy", type);
        result.put("selected", enrichTarget(picked));
        result.put("callIndex", calls + 1);
        ModelGatewayRoute upd = new ModelGatewayRoute();
        upd.setId(route.getId());
        upd.setCallCount(calls + 1);
        upd.setUpdateTime(LocalDateTime.now());
        routeMapper.updateById(upd);
        return result;
    }

    // ---------- helpers ----------

    private void validate(ModelGatewayRoute route, List<Map<String, Object>> targets) {
        if (!StringUtils.hasText(route.getName())) {
            throw new BizException("路由名称不能为空");
        }
        if (!StringUtils.hasText(route.getRouteType())) {
            throw new BizException("请选择路由类型");
        }
        if (targets == null || targets.isEmpty()) {
            throw new BizException("请至少添加一个模型目标");
        }
        for (Map<String, Object> t : targets) {
            Object modelId = t.get("modelId");
            if (modelId == null) {
                throw new BizException("目标模型ID不能为空");
            }
        }
    }

    private Map<String, Object> weightedPick(List<Map<String, Object>> targets) {
        long sum = 0;
        for (Map<String, Object> t : targets) {
            sum += Math.max(1, Long.parseLong(String.valueOf(t.get("weight"))));
        }
        long hit = ThreadLocalRandom.current().nextLong(sum);
        for (Map<String, Object> t : targets) {
            hit -= Math.max(1, Long.parseLong(String.valueOf(t.get("weight"))));
            if (hit < 0) {
                return t;
            }
        }
        return targets.get(targets.size() - 1);
    }

    private Map<String, Object> enrichTarget(Map<String, Object> target) {
        Map<String, Object> copy = new LinkedHashMap<>(target);
        try {
            Long modelId = Long.parseLong(String.valueOf(target.get("modelId")));
            ModelInfo model = modelInfoMapper.selectById(modelId);
            copy.put("modelName", model == null ? "模型 #" + modelId : model.getName());
        } catch (Exception e) {
            copy.put("modelName", "未知模型");
        }
        return copy;
    }

    private List<Map<String, Object>> parseTargets(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException("目标数据序列化失败");
        }
    }

    private void clearDefault(Long exceptId) {
        List<ModelGatewayRoute> defaults = routeMapper.selectList(new LambdaQueryWrapper<ModelGatewayRoute>()
                .eq(ModelGatewayRoute::getTenantId, tenant())
                .eq(ModelGatewayRoute::getIsDefault, 1)
                .ne(exceptId != null, ModelGatewayRoute::getId, exceptId));
        for (ModelGatewayRoute d : defaults) {
            d.setIsDefault(0);
            d.setUpdateTime(LocalDateTime.now());
            routeMapper.updateById(d);
        }
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
