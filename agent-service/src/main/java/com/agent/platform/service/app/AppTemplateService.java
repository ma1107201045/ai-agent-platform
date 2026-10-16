package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppTemplate;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 应用模板服务：平台内置 + 租户自定义模板库，支持从模板一键创建应用草稿。
 */
public interface AppTemplateService {

    public static final long BUILTIN_TENANT = 0L;

    public Page<AppTemplate> page(long page, long size, String keyword, String category,
                                  String appType, Integer status);

    /** 获取模板（平台内置模板对所有租户可见） */
    public AppTemplate getById(Long id);

    /** 新建自定义模板 */
    public AppTemplate create(AppTemplate template);

    /** 更新自定义模板（平台内置模板不可修改） */
    public void update(Long id, AppTemplate template);

    /** 删除自定义模板（移入回收站，可恢复） */
    public void delete(Long id);

    /**
     * 从模板一键创建应用（草稿）。
     *
     * @param id          模板ID
     * @param nameOverride 应用名称（为空则沿用模板名）
     */
    public AppAgent instantiate(Long id, String nameOverride);
}
