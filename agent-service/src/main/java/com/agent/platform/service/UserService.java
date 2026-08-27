package com.agent.platform.service;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.SysUser;
import com.agent.platform.dao.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public Page<SysUser> page(long page, long size) {
        return userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId));
    }

    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在: " + id);
        }
        return user;
    }

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
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userMapper.insert(user);
        return user;
    }

    public void update(SysUser user) {
        getById(user.getId());
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null); // 不修改密码时置空，避免覆盖
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void delete(Long id) {
        getById(id);
        userMapper.deleteById(id);
    }
}
