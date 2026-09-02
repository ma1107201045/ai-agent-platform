package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板（支持 {{var}} 变量占位，版本留痕）
 */
@Data
@TableName("app_prompt_template")
public class AppPromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 分类：general 通用 / system 系统 / business 业务 / custom 自定义 */
    private String category;

    /** 模板正文（支持 {{var}} 占位） */
    private String content;

    /** 变量定义（JSON 数组：[{"name":"var","desc":"说明"}]） */
    private String variables;

    /** 当前版本号 */
    private Integer version;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
