package com.agent.platform.dao.entity.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号安全扩展（MFA / 手机号 / 登录痕迹）
 */
@Data
@TableName("sys_user_security")
public class SysUserSecurity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String phone;

    /** MFA 二次验证：0关闭 1开启 */
    private Integer mfaEnabled;

    /** MFA 密钥（Base32） */
    private String mfaSecret;

    /** MFA 绑定时间 */
    private LocalDateTime mfaBoundAt;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private Integer loginCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
