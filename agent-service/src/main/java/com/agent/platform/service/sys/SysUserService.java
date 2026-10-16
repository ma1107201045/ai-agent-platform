package com.agent.platform.service.sys;

import com.agent.platform.dao.entity.sys.SysUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 用户服务
 */
public interface SysUserService {

    public Page<SysUser> page(long page, long size);

    public SysUser getById(Long id);

    public SysUser create(SysUser user);

    public void update(SysUser user);

    public void delete(Long id);
}
