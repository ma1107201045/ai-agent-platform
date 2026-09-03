package com.agent.platform.dao.vo.sys;

import lombok.Data;

/**
 * 当前登录用户信息
 */
@Data
public class UserProfileVO {
    private Long id;
    private Long tenantId;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private Integer status;
}
