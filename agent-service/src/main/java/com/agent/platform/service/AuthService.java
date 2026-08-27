package com.agent.platform.service;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.result.ResultCode;
import com.agent.platform.common.security.JwtUtil;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.SysUser;
import com.agent.platform.dao.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务：登录 / 当前用户
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /** 登录，成功返回 Token + 用户信息 */
    public LoginResult login(String username, String password) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || !matches(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已被禁用");
        }
        upgradeLegacyPassword(user, password);
        String token = jwtUtil.createToken(user.getId(), user.getTenantId(), user.getUsername());
        return new LoginResult(token, toProfile(user));
    }

    /** 当前登录用户信息 */
    public UserProfile me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return toProfile(user);
    }

    /** 兼容历史明文密码：BCrypt 比对失败且存储值非 BCrypt 格式时，尝试明文比对 */
    private boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        if (isBcrypt(stored)) {
            return passwordEncoder.matches(raw, stored);
        }
        return raw.equals(stored);
    }

    /** 明文密码登录成功后自动升级为 BCrypt 密文 */
    private void upgradeLegacyPassword(SysUser user, String raw) {
        if (!isBcrypt(user.getPassword())) {
            SysUser upd = new SysUser();
            upd.setId(user.getId());
            upd.setPassword(passwordEncoder.encode(raw));
            upd.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(upd);
        }
    }

    private boolean isBcrypt(String pwd) {
        return pwd != null && (pwd.startsWith("$2a$") || pwd.startsWith("$2b$") || pwd.startsWith("$2y$"));
    }

    private UserProfile toProfile(SysUser user) {
        UserProfile p = new UserProfile();
        p.setId(user.getId());
        p.setTenantId(user.getTenantId());
        p.setUsername(user.getUsername());
        p.setNickname(user.getNickname());
        p.setEmail(user.getEmail());
        p.setAvatar(user.getAvatar());
        p.setStatus(user.getStatus());
        return p;
    }

    @Data
    public static class LoginResult {
        private String token;
        private UserProfile user;

        public LoginResult(String token, UserProfile user) {
            this.token = token;
            this.user = user;
        }
    }

    @Data
    public static class UserProfile {
        private Long id;
        private Long tenantId;
        private String username;
        private String nickname;
        private String email;
        private String avatar;
        private Integer status;
    }
}
