package com.agent.platform.dao.entity.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户
 */
@Data
@TableName("sys_tenant")
public class SysTenant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户名称 */
    private String name;

    /** 租户编码（唯一） */
    private String code;

    /** 套餐：free/pro/enterprise */
    private String plan;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
