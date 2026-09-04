package com.agent.platform.service.sys;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.sys.SysTenant;
import com.agent.platform.dao.entity.sys.SysUser;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.sys.SysTenantMapper;
import com.agent.platform.dao.mapper.sys.SysUserMapper;
import com.agent.platform.dao.vo.sys.SysWorkspaceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 工作空间服务：管理当前租户空间的基础信息与空间内资源统计
 */
@Service
@RequiredArgsConstructor
public class SysWorkspaceService {

    private static final Set<String> PLANS = Set.of("free", "pro", "enterprise");

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final AppAgentMapper appAgentMapper;

    public SysWorkspaceVO current() {
        Long tenantId = tenant();
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException("工作空间不存在: " + tenantId);
        }
        SysWorkspaceVO vo = new SysWorkspaceVO();
        vo.setId(tenant.getId());
        vo.setName(tenant.getName());
        vo.setCode(tenant.getCode());
        vo.setPlan(tenant.getPlan());
        vo.setStatus(tenant.getStatus());
        vo.setMemberCount(userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)));
        vo.setAppCount(appAgentMapper.selectCount(new LambdaQueryWrapper<AppAgent>()
                .eq(AppAgent::getTenantId, tenantId)));
        vo.setCreateTime(tenant.getCreateTime());
        vo.setUpdateTime(tenant.getUpdateTime());
        return vo;
    }

    public SysWorkspaceVO update(SysTenant update) {
        SysTenant tenant = tenantMapper.selectById(tenant());
        if (tenant == null) {
            throw new BizException("工作空间不存在");
        }
        if (update == null || !StringUtils.hasText(update.getName())) {
            throw new BizException("空间名称不能为空");
        }
        String plan = StringUtils.hasText(update.getPlan()) ? update.getPlan() : "free";
        if (!PLANS.contains(plan)) {
            throw new BizException("不支持的套餐类型: " + plan);
        }
        tenant.setName(update.getName().trim());
        tenant.setPlan(plan);
        tenantMapper.updateById(tenant);
        return current();
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
