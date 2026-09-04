package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技能版本快照（支持回退历史版本）
 */
@Data
@TableName("app_skill_version")
public class AppSkillVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 技能 ID */
    private Long skillId;

    /** 版本号（从1自增） */
    private Integer version;

    /** 该版本指令正文 */
    private String content;

    /** 该版本变量定义（JSON） */
    private String variables;

    /** 该版本依赖工具（JSON 数组字符串） */
    private String toolIds;

    /** 该版本参考数据集（JSON 数组字符串） */
    private String datasetIds;

    /** 版本说明 */
    private String remark;

    /** 创建人 */
    private Long createdBy;

    private LocalDateTime createTime;
}
