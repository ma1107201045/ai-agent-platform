package com.agent.platform.service.sys;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.TotpUtil;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.sys.SysUser;
import com.agent.platform.dao.entity.sys.SysUserSecurity;
import com.agent.platform.dao.mapper.sys.SysUserMapper;
import com.agent.platform.dao.mapper.sys.SysUserSecurityMapper;
import com.agent.platform.dao.vo.sys.SysProfileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号与安全：个人资料 / 修改密码 / MFA 二次验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysProfileService {

    private final SysUserMapper userMapper;
    private final SysUserSecurityMapper securityMapper;
    private final PasswordEncoder passwordEncoder;

    /** 聚合资料 + 安全信息 */
    public SysProfileVO getProfile() {
        SysUser user = currentUser();
        SysUserSecurity security = loadOrCreateSecurity(user);
        SysProfileVO vo = new SysProfileVO();
        vo.setId(user.getId());
        vo.setTenantId(user.getTenantId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setPhone(security.getPhone());
        vo.setMfaEnabled(security.getMfaEnabled());
        vo.setMfaBoundAt(security.getMfaBoundAt());
        vo.setLastLoginAt(security.getLastLoginAt());
        vo.setLastLoginIp(security.getLastLoginIp());
        vo.setLoginCount(security.getLoginCount());
        return vo;
    }

    /** 更新资料（昵称/邮箱/头像/手机号；username 不可改） */
    public SysProfileVO update(Map<String, String> body) {
        SysUser user = currentUser();
        String nickname = body.get("nickname");
        String email = body.get("email");
        String avatar = body.get("avatar");
        String phone = body.get("phone");

        SysUser upd = new SysUser();
        upd.setId(user.getId());
        if (nickname != null) {
            if (!StringUtils.hasText(nickname.trim())) {
                throw new BizException("昵称不能为空");
            }
            upd.setNickname(nickname.trim());
        }
        if (email != null) {
            String v = email.trim();
            if (StringUtils.hasText(v) && !v.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
                throw new BizException("邮箱格式不正确");
            }
            upd.setEmail(StringUtils.hasText(v) ? v : null);
        }
        if (avatar != null) {
            upd.setAvatar(StringUtils.hasText(avatar.trim()) ? avatar.trim() : null);
        }
        upd.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(upd);

        SysUserSecurity security = loadOrCreateSecurity(user);
        if (phone != null) {
            security.setPhone(StringUtils.hasText(phone.trim()) ? phone.trim() : null);
            security.setUpdateTime(LocalDateTime.now());
            securityMapper.updateById(security);
        }
        return getProfile();
    }

    /** 修改密码 */
    public void changePassword(String oldPassword, String newPassword) {
        SysUser user = currentUser();
        if (!StringUtils.hasText(oldPassword) || !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException("当前密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("新密码长度至少 6 位");
        }
        SysUser upd = new SysUser();
        upd.setId(user.getId());
        upd.setPassword(passwordEncoder.encode(newPassword));
        upd.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(upd);
    }

    /**
     * MFA 初始化：校验密码后生成并暂存密钥（尚未启用），返回 secret 与 otpauth URL。
     */
    public Map<String, String> mfaInit(String password) {
        SysUser user = currentUser();
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new BizException("密码不正确");
        }
        SysUserSecurity security = loadOrCreateSecurity(user);
        String secret = TotpUtil.generateSecret();
        security.setMfaSecret(secret);
        security.setMfaEnabled(0);
        security.setUpdateTime(LocalDateTime.now());
        securityMapper.updateById(security);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("secret", secret);
        result.put("otpauthUrl", TotpUtil.otpauthUrl(secret, user.getUsername(), "AgentPlatform"));
        return result;
    }

    /** MFA 启用确认：用动态口令校验密钥后正式开启 */
    public void mfaConfirm(String code) {
        SysUser user = currentUser();
        SysUserSecurity security = loadOrCreateSecurity(user);
        if (!StringUtils.hasText(security.getMfaSecret())) {
            throw new BizException("请先初始化 MFA 密钥");
        }
        if (!TotpUtil.verify(security.getMfaSecret(), code)) {
            throw new BizException("动态口令校验失败");
        }
        security.setMfaEnabled(1);
        security.setMfaBoundAt(LocalDateTime.now());
        security.setUpdateTime(LocalDateTime.now());
        securityMapper.updateById(security);
    }

    /** MFA 关闭：需密码 + 动态口令 */
    public void mfaDisable(String password, String code) {
        SysUser user = currentUser();
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new BizException("密码不正确");
        }
        SysUserSecurity security = loadOrCreateSecurity(user);
        if (security.getMfaEnabled() == null || security.getMfaEnabled() != 1) {
            throw new BizException("当前未开启 MFA");
        }
        if (!TotpUtil.verify(security.getMfaSecret(), code)) {
            throw new BizException("动态口令校验失败");
        }
        security.setMfaSecret(null);
        security.setMfaEnabled(0);
        security.setMfaBoundAt(null);
        security.setUpdateTime(LocalDateTime.now());
        securityMapper.updateById(security);
    }

    /** 登录成功后记录登录痕迹（由 SysAuthService 调用） */
    public void recordLogin(Long userId, Long tenantId, String ip) {
        SysUserSecurity sec = securityMapper.selectOne(
                new LambdaQueryWrapper<SysUserSecurity>().eq(SysUserSecurity::getUserId, userId));
        if (sec == null) {
            sec = new SysUserSecurity();
            sec.setTenantId(tenantId);
            sec.setUserId(userId);
            sec.setMfaEnabled(0);
            sec.setLoginCount(1);
            sec.setLastLoginAt(LocalDateTime.now());
            sec.setLastLoginIp(ip);
            securityMapper.insert(sec);
            return;
        }
        sec.setLoginCount((sec.getLoginCount() == null ? 0 : sec.getLoginCount()) + 1);
        sec.setLastLoginAt(LocalDateTime.now());
        sec.setLastLoginIp(ip);
        sec.setUpdateTime(LocalDateTime.now());
        securityMapper.updateById(sec);
    }

    private SysUser currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException("未登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private SysUserSecurity loadOrCreateSecurity(SysUser user) {
        SysUserSecurity sec = securityMapper.selectOne(
                new LambdaQueryWrapper<SysUserSecurity>().eq(SysUserSecurity::getUserId, user.getId()));
        if (sec == null) {
            sec = new SysUserSecurity();
            sec.setTenantId(user.getTenantId());
            sec.setUserId(user.getId());
            sec.setMfaEnabled(0);
            sec.setLoginCount(0);
            sec.setCreateTime(LocalDateTime.now());
            sec.setUpdateTime(LocalDateTime.now());
            securityMapper.insert(sec);
        }
        return sec;
    }
}
