package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多智能体编排-团队成员
 */
@Data
@TableName("agent_team_member")
public class AgentTeamMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;

    /** 成员/角色名 */
    private String name;

    private String description;

    /** 绑定应用ID(app_agent.id) */
    private Long appId;

    /** 意图关键词(逗号分隔，first_match 路由用) */
    private String keywords;

    /** 路由优先级(数字小优先) */
    private Integer priority;

    /** 是否启用: 0否 1是 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
