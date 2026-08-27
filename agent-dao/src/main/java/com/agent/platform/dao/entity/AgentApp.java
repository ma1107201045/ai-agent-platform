package com.agent.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体应用
 */
@Data
@TableName("agent_app")
public class AgentApp {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    /** 应用类型：chatflow(对话流) / workflow(工作流) / agent(自主智能体) */
    private String type;

    private String icon;

    /** 开场白 */
    private String welcomeMessage;

    /** 推荐问题（JSON 数组字符串） */
    private String openingQuestions;

    /** 状态：0草稿 1已发布 */
    private Integer status;

    /** 编排草稿 DSL（JSON），发布时快照到版本表 */
    private String workflowJson;

    /** 当前发布版本 ID */
    private Long publishedVersionId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
