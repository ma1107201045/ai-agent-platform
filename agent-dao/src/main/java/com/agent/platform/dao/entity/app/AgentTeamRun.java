package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多智能体编排-团队运行记录
 */
@Data
@TableName("agent_team_run")
public class AgentTeamRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long teamId;

    private String input;

    private String answer;

    /** 命中的成员(名称，并行时逗号分隔) */
    private String routedMember;

    /** 执行轨迹(JSON数组) */
    private String traceJson;

    /** 状态: running/success/failed */
    private String status;

    private String error;

    /** 耗时(毫秒) */
    private Long costMs;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
