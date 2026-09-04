package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多智能体编排-团队
 */
@Data
@TableName("agent_team")
public class AgentTeam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    /** 路由策略: first_match意图匹配/round_robin轮询/all并行汇合 */
    private String routing;

    /** 状态: 0停用 1启用 */
    private Integer status;

    private Integer runCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
