package com.agent.platform.service.sys;

import com.agent.platform.dao.entity.sys.SysTenant;
import com.agent.platform.dao.vo.sys.SysWorkspaceVO;

/**
 * 工作空间服务：管理当前租户空间的基础信息与空间内资源统计
 */
public interface SysWorkspaceService {

    public SysWorkspaceVO current();

    public SysWorkspaceVO update(SysTenant update);
}
