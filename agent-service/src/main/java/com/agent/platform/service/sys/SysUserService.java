package com.agent.platform.service.sys;

import com.agent.platform.dao.entity.sys.SysUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 用户服务
 */
public interface SysUserService {

    Page<SysUser> page(long page, long size);

    SysUser getById(Long id);

    SysUser create(SysUser user);

    void update(SysUser user);

    void delete(Long id);
}
