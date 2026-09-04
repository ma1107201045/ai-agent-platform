package com.agent.platform.dao.vo.sys;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人中心 / 账号安全页聚合信息
 */
@Data
public class SysProfileVO {

    private Long id;

    private Long tenantId;

    private String username;

    private String nickname;

    private String email;

    private String avatar;

    /** 状态：0禁用 1启用 */
    private Integer status;

    // ---- 账号安全 ----

    private String phone;

    /** MFA：0关闭 1开启 */
    private Integer mfaEnabled;

    private LocalDateTime mfaBoundAt;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private Integer loginCount;
}
