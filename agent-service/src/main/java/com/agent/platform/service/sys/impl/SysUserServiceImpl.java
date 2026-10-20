package com.agent.platform.service.sys.impl;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.sys.SysUser;
import com.agent.platform.dao.mapper.sys.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import com.agent.platform.service.sys.SysUserService;

/**
 * SysUserService 接口实现。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    @Override
    public Page<SysUser> page(long page, long size) {
        return userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId));
    }

    @Override
    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在: " + id);
        }
        return user;
    }

    @Override
    public SysUser create(SysUser user) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在: " + user.getUsername());
        }
        LocalDateTime now = LocalDateTime.now();
        user.setId(null);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getTenantId() == null) {
            user.setTenantId(1L); // 默认租户
        }
        if (!"admin".equals(user.getRole()) && !"user".equals(user.getRole())) {
            user.setRole("user"); // 非法角色一律降级为普通用户
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userMapper.insert(user);
        return user;
    }

    @Override
    public void update(SysUser user) {
        SysUser existing = getById(user.getId());
        // 改名时校验唯一性，避免造出重复用户名导致两人都无法登录
        if (user.getUsername() != null && !user.getUsername().equals(existing.getUsername())) {
            Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, user.getUsername())
                    .ne(SysUser::getId, user.getId()));
            if (count > 0) {
                throw new BizException("用户名已存在: " + user.getUsername());
            }
        }
        if (user.getRole() != null && !"admin".equals(user.getRole()) && !"user".equals(user.getRole())) {
            throw new BizException("非法角色: " + user.getRole());
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null); // 不修改密码时置空，避免覆盖
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        getById(id);
        userMapper.deleteById(id);
    }
}
