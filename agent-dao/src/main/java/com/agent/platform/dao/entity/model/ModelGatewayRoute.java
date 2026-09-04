package com.agent.platform.dao.entity.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型网关路由
 */
@Data
@TableName("model_gateway_route")
public class ModelGatewayRoute {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    /** priority优先级 / failover故障回退 / round_robin轮询 */
    private String routeType;

    /** 目标列表 JSON: [{modelId, weight, priority}] */
    private String targetsJson;

    /** 是否默认路由 0否1是 */
    private Integer isDefault;

    /** 0停用 1启用 */
    private Integer enabled;

    private Long callCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
