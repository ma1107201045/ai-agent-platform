package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板版本快照（支持回退历史版本）
 */
@Data
@TableName("app_prompt_version")
public class AppPromptVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板ID */
    private Long templateId;

    /** 版本号（从1自增） */
    private Integer version;

    /** 该版本模板正文 */
    private String content;

    /** 该版本变量定义（JSON） */
    private String variables;

    /** 版本说明 */
    private String remark;

    /** 创建人 */
    private Long createdBy;

    private LocalDateTime createTime;
}
