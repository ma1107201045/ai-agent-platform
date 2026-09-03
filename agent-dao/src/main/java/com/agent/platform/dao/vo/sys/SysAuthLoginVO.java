package com.agent.platform.dao.vo.sys;

import lombok.Data;

/**
 * 登录结果：Token + 用户信息
 */
@Data
public class SysAuthLoginVO {
    private String token;
    private UserProfileVO user;

    public SysAuthLoginVO(String token, UserProfileVO user) {
        this.token = token;
        this.user = user;
    }
}
