package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用版本（workflow_json 为完整快照，支持发布/回滚）
 */
@Data
@TableName("app_agent_version")
public class AppAgentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    /** 版本号（自增） */
    private Integer version;

    /** 工作流图定义（JSON DSL） */
    private String workflowJson;

    /** Prompt 配置（JSON） */
    private String promptConfig;

    /** 是否已发布：0否 1是 */
    private Integer isPublished;

    private Long createdBy;

    private LocalDateTime createTime;
}
