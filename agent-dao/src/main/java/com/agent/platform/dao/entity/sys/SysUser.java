package com.agent.platform.dao.entity.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 登录名（唯一） */
    private String username;

    /** 密码（BCrypt 加密，后续接入 Security 后启用） */
    private String password;

    private String nickname;

    private String email;

    private String avatar;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
