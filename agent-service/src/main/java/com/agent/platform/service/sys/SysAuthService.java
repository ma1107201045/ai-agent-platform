package com.agent.platform.service.sys;

import com.agent.platform.dao.vo.sys.SysAuthLoginVO;
import com.agent.platform.dao.vo.sys.UserProfileVO;

/**
 * 认证服务：登录 / 当前用户
 */
public interface SysAuthService {

    /** 登录，成功返回 Token + 用户信息 */
    SysAuthLoginVO login(String username, String password);

    /** 当前登录用户信息 */
    UserProfileVO me();
}
