package com.agent.platform.common.security;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户上下文（ThreadLocal），由鉴权拦截器写入，业务层读取
 */
public class UserContext {

    /** 管理员角色标识（sys_user.role） */
    public static final String ROLE_ADMIN = "admin";

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static Long getTenantId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getTenantId();
    }

    public static String getUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static String getRole() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getRole();
    }

    public static boolean isAdmin() {
        return !ROLE_ADMIN.equals(getRole());
    }

    /** 要求当前登录用户为管理员，否则抛出 403 业务异常 */
    public static void requireAdmin() {
        if (isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权限：该操作需要管理员角色");
        }
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginUser {
        private Long userId;
        private Long tenantId;
        private String username;
        private String role;

        public LoginUser(Long userId, Long tenantId, String username) {
            this(userId, tenantId, username, null);
        }
    }
}
