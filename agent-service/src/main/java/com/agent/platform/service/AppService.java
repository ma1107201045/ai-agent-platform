package com.agent.platform.service;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.AgentApp;
import com.agent.platform.dao.entity.AgentAppVersion;
import com.agent.platform.dao.mapper.AgentAppMapper;
import com.agent.platform.dao.mapper.AgentAppVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 应用服务
 */
@Service
@RequiredArgsConstructor
public class AppService {

    private final AgentAppMapper appMapper;
    private final AgentAppVersionMapper versionMapper;

    public Page<AgentApp> page(long page, long size) {
        return appMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AgentApp>().orderByDesc(AgentApp::getId));
    }

    public AgentApp getById(Long id) {
        AgentApp app = appMapper.selectById(id);
        if (app == null) {
            throw new BizException("应用不存在: " + id);
        }
        return app;
    }

    public AgentApp create(AgentApp app) {
        LocalDateTime now = LocalDateTime.now();
        app.setId(null);
        app.setStatus(0); // 草稿
        app.setCreateTime(now);
        app.setUpdateTime(now);
        if (app.getType() == null) {
            app.setType("chatflow");
        }
        if (app.getTenantId() == null) {
            app.setTenantId(1L);
        }
        appMapper.insert(app);
        return app;
    }

    public void update(AgentApp app) {
        getById(app.getId());
        app.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(app);
    }

    public void delete(Long id) {
        getById(id);
        appMapper.deleteById(id);
        versionMapper.delete(new LambdaQueryWrapper<AgentAppVersion>().eq(AgentAppVersion::getAppId, id));
    }

    /**
     * 发布：创建新版本快照，旧发布版本置为未发布
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentAppVersion publish(Long appId, String workflowJson, String promptConfig, Long operatorId) {
        AgentApp app = getById(appId);
        Integer maxVersion = versionMapper.selectList(
                        new LambdaQueryWrapper<AgentAppVersion>().eq(AgentAppVersion::getAppId, appId))
                .stream()
                .map(AgentAppVersion::getVersion)
                .max(Integer::compareTo)
                .orElse(0);

        AgentAppVersion version = new AgentAppVersion();
        version.setAppId(appId);
        version.setVersion(maxVersion + 1);
        version.setWorkflowJson(workflowJson);
        version.setPromptConfig(promptConfig);
        version.setIsPublished(1);
        version.setCreatedBy(operatorId);
        version.setCreateTime(LocalDateTime.now());
        versionMapper.insert(version);

        // 旧版本取消发布
        versionMapper.update(null, new LambdaUpdateWrapper<AgentAppVersion>()
                .eq(AgentAppVersion::getAppId, appId)
                .eq(AgentAppVersion::getIsPublished, 1)
                .ne(AgentAppVersion::getId, version.getId())
                .set(AgentAppVersion::getIsPublished, 0));

        app.setPublishedVersionId(version.getId());
        app.setStatus(1);
        app.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(app);
        return version;
    }

    public AgentAppVersion getPublishedVersion(Long appId) {
        AgentApp app = getById(appId);
        if (app.getPublishedVersionId() == null) {
            throw new BizException("应用尚未发布: " + appId);
        }
        AgentAppVersion version = versionMapper.selectById(app.getPublishedVersionId());
        if (version == null) {
            throw new BizException("应用发布版本不存在");
        }
        return version;
    }

    /**
     * 获取运行用 DSL：草稿优先，否则取已发布版本
     */
    public String getRunWorkflow(Long appId) {
        AgentApp app = getById(appId);
        if (app.getWorkflowJson() != null && !app.getWorkflowJson().isBlank()) {
            return app.getWorkflowJson();
        }
        if (app.getPublishedVersionId() != null) {
            AgentAppVersion version = versionMapper.selectById(app.getPublishedVersionId());
            if (version != null && version.getWorkflowJson() != null && !version.getWorkflowJson().isBlank()) {
                return version.getWorkflowJson();
            }
        }
        return null;
    }
}
