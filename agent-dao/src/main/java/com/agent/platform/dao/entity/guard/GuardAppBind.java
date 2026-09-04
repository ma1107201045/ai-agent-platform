package com.agent.platform.dao.entity.guard;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容安全-应用绑定
 */
@Data
@TableName("guard_app_bind")
public class GuardAppBind {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 应用ID */
    private Long appId;

    /** 绑定规则ID列表(JSON数组) */
    private String ruleIds;

    /** 模式: enforce强制/log仅记录 */
    private String mode;

    /** 启用: 0否 1是 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
