package com.agent.platform.service.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppAgentVersion;
import com.agent.platform.dao.entity.chat.ChatConversation;
import com.agent.platform.dao.entity.chat.ChatMessage;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.app.AppAgentVersionMapper;
import com.agent.platform.dao.mapper.chat.ChatConversationMapper;
import com.agent.platform.dao.mapper.chat.ChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用服务
 */
@Service
@RequiredArgsConstructor
public class AppService {

    private final AppAgentMapper appMapper;
    private final AppAgentVersionMapper versionMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;

    /** 分页查询应用，支持名称模糊搜索与类型过滤 */
    public Page<AppAgent> page(long page, long size, String keyword, String type) {
        LambdaQueryWrapper<AppAgent> qw = new LambdaQueryWrapper<AppAgent>()
                .orderByDesc(AppAgent::getId);
        if (keyword != null && !keyword.isBlank()) {
            qw.like(AppAgent::getName, keyword.trim());
        }
        if (type != null && !type.isBlank()) {
            qw.eq(AppAgent::getType, type);
        }
        return appMapper.selectPage(new Page<>(page, size), qw);
    }

    public AppAgent getById(Long id) {
        AppAgent app = appMapper.selectById(id);
        if (app == null) {
            throw new BizException("应用不存在: " + id);
        }
        return app;
    }

    public AppAgent create(AppAgent app) {
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

    /** 更新应用：仅允许更新业务字段，防止 status / publishedVersionId 等敏感字段被覆盖 */
    public void update(AppAgent app) {
        getById(app.getId());
        LambdaUpdateWrapper<AppAgent> uw = new LambdaUpdateWrapper<AppAgent>()
                .eq(AppAgent::getId, app.getId())
                .set(AppAgent::getUpdateTime, LocalDateTime.now());
        if (app.getName() != null) uw.set(AppAgent::getName, app.getName());
        if (app.getDescription() != null) uw.set(AppAgent::getDescription, app.getDescription());
        if (app.getType() != null) uw.set(AppAgent::getType, app.getType());
        if (app.getIcon() != null) uw.set(AppAgent::getIcon, app.getIcon());
        if (app.getWelcomeMessage() != null) uw.set(AppAgent::getWelcomeMessage, app.getWelcomeMessage());
        if (app.getOpeningQuestions() != null) uw.set(AppAgent::getOpeningQuestions, app.getOpeningQuestions());
        if (app.getWorkflowJson() != null) uw.set(AppAgent::getWorkflowJson, app.getWorkflowJson());
        if (app.getToolIds() != null) uw.set(AppAgent::getToolIds, app.getToolIds());
        if (app.getDatasetIds() != null) uw.set(AppAgent::getDatasetIds, app.getDatasetIds());
        appMapper.update(null, uw);
    }

    /** 删除应用：级联清理发布版本、会话与消息，避免脏数据残留 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        List<Long> convIds = conversationMapper.selectList(
                        new LambdaQueryWrapper<ChatConversation>()
                                .select(ChatConversation::getId)
                                .eq(ChatConversation::getAppId, id))
                .stream()
                .map(ChatConversation::getId)
                .toList();
        if (!convIds.isEmpty()) {
            messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                    .in(ChatMessage::getConversationId, convIds));
        }
        conversationMapper.delete(new LambdaQueryWrapper<ChatConversation>().eq(ChatConversation::getAppId, id));
        appMapper.deleteById(id);
        versionMapper.delete(new LambdaQueryWrapper<AppAgentVersion>().eq(AppAgentVersion::getAppId, id));
    }

    /**
     * 发布：创建新版本快照，旧发布版本置为未发布
     */
    @Transactional(rollbackFor = Exception.class)
    public AppAgentVersion publish(Long appId, String workflowJson, String promptConfig, Long operatorId) {
        AppAgent app = getById(appId);
        List<Object> versions = versionMapper.selectObjs(
                new LambdaQueryWrapper<AppAgentVersion>()
                        .select(AppAgentVersion::getVersion)
                        .eq(AppAgentVersion::getAppId, appId)
                        .orderByDesc(AppAgentVersion::getVersion)
                        .last("limit 1"));
        int maxVersion = versions.isEmpty() ? 0 : ((Number) versions.get(0)).intValue();

        AppAgentVersion version = new AppAgentVersion();
        version.setAppId(appId);
        version.setVersion(maxVersion + 1);
        version.setWorkflowJson(workflowJson);
        version.setPromptConfig(promptConfig);
        version.setIsPublished(1);
        version.setCreatedBy(operatorId);
        version.setCreateTime(LocalDateTime.now());
        versionMapper.insert(version);

        // 旧版本取消发布
        versionMapper.update(null, new LambdaUpdateWrapper<AppAgentVersion>()
                .eq(AppAgentVersion::getAppId, appId)
                .eq(AppAgentVersion::getIsPublished, 1)
                .ne(AppAgentVersion::getId, version.getId())
                .set(AppAgentVersion::getIsPublished, 0));

        app.setPublishedVersionId(version.getId());
        app.setStatus(1);
        app.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(app);
        return version;
    }

    public AppAgentVersion getPublishedVersion(Long appId) {
        AppAgent app = getById(appId);
        if (app.getPublishedVersionId() == null) {
            throw new BizException("应用尚未发布: " + appId);
        }
        AppAgentVersion version = versionMapper.selectById(app.getPublishedVersionId());
        if (version == null) {
            throw new BizException("应用发布版本不存在");
        }
        return version;
    }

    /** 版本列表（按版本号倒序） */
    public List<AppAgentVersion> listVersions(Long appId) {
        getById(appId);
        return versionMapper.selectList(new LambdaQueryWrapper<AppAgentVersion>()
                .eq(AppAgentVersion::getAppId, appId)
                .orderByDesc(AppAgentVersion::getVersion));
    }

    /**
     * 回滚到指定版本：将版本快照恢复到应用草稿（不自动发布，用户确认后再发布）
     */
    @Transactional(rollbackFor = Exception.class)
    public AppAgentVersion rollback(Long appId, Long versionId) {
        getById(appId);
        AppAgentVersion version = versionMapper.selectById(versionId);
        if (version == null || !appId.equals(version.getAppId())) {
            throw new BizException("版本不存在: " + versionId);
        }
        appMapper.update(null, new LambdaUpdateWrapper<AppAgent>()
                .eq(AppAgent::getId, appId)
                .set(AppAgent::getWorkflowJson, version.getWorkflowJson())
                .set(AppAgent::getUpdateTime, LocalDateTime.now()));
        return version;
    }

    /**
     * 获取已发布版本的工作流 DSL（公开访问只用线上版本，不用草稿）
     */
    public String getPublishedWorkflow(Long appId) {
        AppAgentVersion version = getPublishedVersion(appId);
        if (version.getWorkflowJson() == null || version.getWorkflowJson().isBlank()) {
            throw new BizException("应用发布版本未编排工作流");
        }
        return version.getWorkflowJson();
    }

    /**
     * 获取运行用 DSL：草稿优先，否则取已发布版本
     */
    public String getRunWorkflow(Long appId) {
        AppAgent app = getById(appId);
        if (app.getWorkflowJson() != null && !app.getWorkflowJson().isBlank()) {
            return app.getWorkflowJson();
        }
        if (app.getPublishedVersionId() != null) {
            AppAgentVersion version = versionMapper.selectById(app.getPublishedVersionId());
            if (version != null && version.getWorkflowJson() != null && !version.getWorkflowJson().isBlank()) {
                return version.getWorkflowJson();
            }
        }
        return null;
    }
}
