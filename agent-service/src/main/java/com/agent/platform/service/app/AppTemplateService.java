package com.agent.platform.service.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppTemplate;
import com.agent.platform.dao.mapper.app.AppTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 应用模板服务：平台内置 + 租户自定义模板库，支持从模板一键创建应用草稿。
 */
@Service
@RequiredArgsConstructor
public class AppTemplateService {

    public static final long BUILTIN_TENANT = 0L;

    private final AppTemplateMapper templateMapper;
    private final AppAgentService appAgentService;

    public Page<AppTemplate> page(long page, long size, String keyword, String category,
                                  String appType, Integer status) {
        LambdaQueryWrapper<AppTemplate> wrapper = new LambdaQueryWrapper<AppTemplate>()
                .and(w -> w.eq(AppTemplate::getTenantId, tenant())
                        .or().eq(AppTemplate::getTenantId, BUILTIN_TENANT))
                .orderByDesc(AppTemplate::getBuiltin)
                .orderByDesc(AppTemplate::getUsageCount)
                .orderByDesc(AppTemplate::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AppTemplate::getName, keyword)
                    .or().like(AppTemplate::getDescription, keyword)
                    .or().like(AppTemplate::getUseCase, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(AppTemplate::getCategory, category);
        }
        if (StringUtils.hasText(appType)) {
            wrapper.eq(AppTemplate::getAppType, appType);
        }
        if (status != null) {
            wrapper.eq(AppTemplate::getStatus, status);
        }
        return templateMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 获取模板（平台内置模板对所有租户可见） */
    public AppTemplate getById(Long id) {
        AppTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException("模板不存在: " + id);
        }
        if (template.getTenantId() != null
                && template.getTenantId() != BUILTIN_TENANT
                && !template.getTenantId().equals(tenant())) {
            throw new BizException("模板不存在: " + id);
        }
        return template;
    }

    /** 新建自定义模板 */
    public AppTemplate create(AppTemplate template) {
        validate(template);
        LocalDateTime now = LocalDateTime.now();
        template.setId(null);
        template.setTenantId(tenant());
        template.setBuiltin(0);
        if (template.getStatus() == null) {
            template.setStatus(1);
        }
        if (template.getUsageCount() == null) {
            template.setUsageCount(0);
        }
        template.setCreateBy(UserContext.getUserId());
        template.setCreateTime(now);
        template.setUpdateTime(now);
        templateMapper.insert(template);
        return template;
    }

    /** 更新自定义模板（平台内置模板不可修改） */
    public void update(Long id, AppTemplate template) {
        AppTemplate exist = getById(id);
        if (exist.getBuiltin() != null && exist.getBuiltin() == 1) {
            throw new BizException("平台内置模板不可修改");
        }
        validate(template);
        template.setId(id);
        template.setTenantId(exist.getTenantId());
        template.setBuiltin(exist.getBuiltin());
        template.setCreateBy(exist.getCreateBy());
        template.setCreateTime(exist.getCreateTime());
        if (template.getStatus() == null) {
            template.setStatus(exist.getStatus());
        }
        if (template.getUsageCount() == null) {
            template.setUsageCount(exist.getUsageCount());
        }
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    /** 删除自定义模板（移入回收站，可恢复） */
    public void delete(Long id) {
        AppTemplate exist = getById(id);
        if (exist.getBuiltin() != null && exist.getBuiltin() == 1) {
            throw new BizException("平台内置模板不可删除");
        }
        templateMapper.markDeleted(id);
    }

    /**
     * 从模板一键创建应用（草稿）。
     *
     * @param id          模板ID
     * @param nameOverride 应用名称（为空则沿用模板名）
     */
    public AppAgent instantiate(Long id, String nameOverride) {
        AppTemplate template = getById(id);
        if (template.getStatus() != null && template.getStatus() != 1) {
            throw new BizException("该模板已停用");
        }
        AppAgent app = new AppAgent();
        app.setTenantId(tenant());
        app.setName(StringUtils.hasText(nameOverride) ? nameOverride.trim() : template.getName());
        app.setType(StringUtils.hasText(template.getAppType()) ? template.getAppType() : "chatflow");
        app.setDescription(template.getDescription());
        app.setIcon(template.getIcon());
        app.setWelcomeMessage(template.getWelcomeMessage());
        app.setOpeningQuestions(null);
        AppAgent created = appAgentService.create(app);

        templateMapper.update(null, new LambdaUpdateWrapper<AppTemplate>()
                .eq(AppTemplate::getId, template.getId())
                .set(AppTemplate::getUsageCount,
                        (template.getUsageCount() == null ? 0 : template.getUsageCount()) + 1));
        return created;
    }

    private void validate(AppTemplate template) {
        if (template == null || !StringUtils.hasText(template.getName())) {
            throw new BizException("模板名称不能为空");
        }
        if (template.getName().length() > 128) {
            throw new BizException("模板名称不能超过 128 字");
        }
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
