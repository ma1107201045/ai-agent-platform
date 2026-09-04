package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用模板（tenant_id=0 表示平台内置模板）
 */
@Data
@TableName("app_template")
public class AppTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 模板名称 */
    private String name;

    /** 分类：customer-service/translate/content/data-analysis/marketing/coding/custom */
    private String category;

    /** 应用类型：chatflow/workflow/agent */
    private String appType;

    /** 图标（emoji） */
    private String icon;

    /** 模板简介 */
    private String description;

    /** 适用场景 */
    private String useCase;

    /** 创建应用后的默认开场白 */
    private String welcomeMessage;

    /** 是否平台内置：0否 1是 */
    private Integer builtin;

    /** 被使用次数 */
    private Integer usageCount;

    /** 状态：0停用 1启用 */
    private Integer status;

    /** 创建人 */
    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
