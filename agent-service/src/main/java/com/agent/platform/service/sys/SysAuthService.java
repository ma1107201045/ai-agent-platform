package com.agent.platform.service.sys;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.result.ResultCode;
import com.agent.platform.common.security.JwtUtil;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.sys.SysUser;
import com.agent.platform.dao.entity.sys.SysUserSecurity;
import com.agent.platform.dao.mapper.sys.SysUserMapper;
import com.agent.platform.dao.mapper.sys.SysUserSecurityMapper;
import com.agent.platform.dao.vo.sys.SysAuthLoginVO;
import com.agent.platform.dao.vo.sys.UserProfileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务：登录 / 当前用户
 */
@Service
@RequiredArgsConstructor
public class SysAuthService {

    private final SysUserMapper userMapper;
    private final SysUserSecurityMapper securityMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /** 登录，成功返回 Token + 用户信息 */
    public SysAuthLoginVO login(String username, String password) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || !matches(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已被禁用");
        }
        upgradeLegacyPassword(user, password);
        recordLoginTrace(user);
        String token = jwtUtil.createToken(user.getId(), user.getTenantId(), user.getUsername());
        return new SysAuthLoginVO(token, toProfile(user));
    }

    /** 登录成功记录安全扩展表中的登录次数与时间 */
    private void recordLoginTrace(SysUser user) {
        SysUserSecurity sec = securityMapper.selectOne(
                new LambdaQueryWrapper<SysUserSecurity>().eq(SysUserSecurity::getUserId, user.getId()));
        LocalDateTime now = LocalDateTime.now();
        if (sec == null) {
            sec = new SysUserSecurity();
            sec.setTenantId(user.getTenantId());
            sec.setUserId(user.getId());
            sec.setMfaEnabled(0);
            sec.setLoginCount(1);
            sec.setLastLoginAt(now);
            sec.setCreateTime(now);
            sec.setUpdateTime(now);
            securityMapper.insert(sec);
            return;
        }
        sec.setLoginCount((sec.getLoginCount() == null ? 0 : sec.getLoginCount()) + 1);
        sec.setLastLoginAt(now);
        sec.setUpdateTime(now);
        securityMapper.updateById(sec);
    }

    /** 当前登录用户信息 */
    public UserProfileVO me() {
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

    private UserProfileVO toProfile(SysUser user) {
        UserProfileVO p = new UserProfileVO();
        p.setId(user.getId());
        p.setTenantId(user.getTenantId());
        p.setUsername(user.getUsername());
        p.setNickname(user.getNickname());
        p.setEmail(user.getEmail());
        p.setAvatar(user.getAvatar());
        p.setStatus(user.getStatus());
        return p;
    }
}
