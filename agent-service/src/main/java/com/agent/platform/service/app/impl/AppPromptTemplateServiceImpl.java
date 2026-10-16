package com.agent.platform.service.app.impl;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppPromptTemplate;
import com.agent.platform.dao.entity.app.AppPromptVersion;
import com.agent.platform.dao.mapper.app.AppPromptTemplateMapper;
import com.agent.platform.dao.mapper.app.AppPromptVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.agent.platform.service.app.AppApiKeyService;
import com.agent.platform.service.app.AppPromptTemplateService;

/**
 * AppPromptTemplateService 接口实现。
 */
@Service
@RequiredArgsConstructor
public class AppPromptTemplateServiceImpl implements AppPromptTemplateService {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");

    private final AppPromptTemplateMapper templateMapper;

    private final AppPromptVersionMapper versionMapper;

    private final ObjectMapper objectMapper;

    // ---------- 模板 CRUD ----------

    @Override
    public Page<AppPromptTemplate> page(long page, long size, String keyword, String category) {
        LambdaQueryWrapper<AppPromptTemplate> wrapper = new LambdaQueryWrapper<AppPromptTemplate>()
                .eq(category != null && !category.isBlank(), AppPromptTemplate::getCategory, category)
                .orderByDesc(AppPromptTemplate::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AppPromptTemplate::getName, keyword)
                    .or().like(AppPromptTemplate::getDescription, keyword));
        }
        return templateMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<AppPromptTemplate> listEnabled(String category) {
        LambdaQueryWrapper<AppPromptTemplate> wrapper = new LambdaQueryWrapper<AppPromptTemplate>()
                .eq(AppPromptTemplate::getStatus, 1)
                .eq(category != null && !category.isBlank(), AppPromptTemplate::getCategory, category)
                .orderByDesc(AppPromptTemplate::getId);
        return templateMapper.selectList(wrapper);
    }

    @Override
    public AppPromptTemplate getById(Long id) {
        AppPromptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException("提示词模板不存在: " + id);
        }
        return template;
    }

    /** 创建模板：初始版本 v1 并写入快照 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AppPromptTemplate create(AppPromptTemplate template) {
        validate(template);
        template.setId(null);
        if (template.getTenantId() == null) {
            // 优先取登录上下文租户，未登录兜底默认租户（与 AppApiKeyService 一致）
            template.setTenantId(UserContext.getTenantId() == null ? 1L : UserContext.getTenantId());
        }
        if (template.getCategory() == null || template.getCategory().isBlank()) {
            template.setCategory("general");
        }
        if (template.getStatus() == null) {
            template.setStatus(1);
        }
        template.setVersion(1);
        templateMapper.insert(template);

        AppPromptVersion snapshot = new AppPromptVersion();
        snapshot.setTemplateId(template.getId());
        snapshot.setVersion(1);
        snapshot.setContent(template.getContent());
        snapshot.setVariables(template.getVariables());
        snapshot.setRemark("初始版本");
        snapshot.setCreatedBy(UserContext.getUserId());
        versionMapper.insert(snapshot);
        return template;
    }

    /**
     * 更新模板：基础信息（名称/描述/分类/状态）直接更新；
     * 正文或变量定义有变化时自动生成新版本快照。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AppPromptTemplate update(AppPromptTemplate template) {
        AppPromptTemplate exists = getById(template.getId());
        validate(template);

        boolean contentChanged = !safeEquals(exists.getContent(), template.getContent())
                || !safeEquals(exists.getVariables(), template.getVariables());

        exists.setName(template.getName());
        exists.setDescription(template.getDescription());
        exists.setCategory(template.getCategory());
        if (template.getStatus() != null) {
            exists.setStatus(template.getStatus());
        }
        if (contentChanged) {
            exists.setContent(template.getContent());
            exists.setVariables(template.getVariables());
            int newVersion = nextVersion(exists.getId());
            exists.setVersion(newVersion);
            templateMapper.updateById(exists);

            AppPromptVersion snapshot = new AppPromptVersion();
            snapshot.setTemplateId(exists.getId());
            snapshot.setVersion(newVersion);
            snapshot.setContent(template.getContent());
            snapshot.setVariables(template.getVariables());
            snapshot.setRemark("内容更新");
            snapshot.setCreatedBy(UserContext.getUserId());
            versionMapper.insert(snapshot);
        } else {
            templateMapper.updateById(exists);
        }
        return exists;
    }

    /** 删除模板：软删除进回收站（版本快照保留，恢复时还原） */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long id) {
        getById(id);
        templateMapper.markDeleted(id);
    }

    private void validate(AppPromptTemplate template) {
        if (template.getName() == null || template.getName().isBlank()) {
            throw new BizException("模板名称不能为空");
        }
        if (template.getName().length() > 128) {
            throw new BizException("模板名称不能超过 128 字符");
        }
        if (template.getContent() == null || template.getContent().isBlank()) {
            throw new BizException("模板正文不能为空");
        }
        if (template.getVariables() != null && !template.getVariables().isBlank()) {
            try {
                List<?> list = objectMapper.readValue(template.getVariables(), List.class);
                if (list.size() > 50) {
                    throw new BizException("变量数量不能超过 50 个");
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException("变量定义须为 JSON 数组，如 [{\"name\":\"var\",\"desc\":\"说明\"}]");
            }
        }
    }

    // ---------- 版本快照 ----------

    @Override
    public List<AppPromptVersion> versions(Long templateId) {
        getById(templateId);
        return versionMapper.selectList(new LambdaQueryWrapper<AppPromptVersion>()
                .eq(AppPromptVersion::getTemplateId, templateId)
                .orderByDesc(AppPromptVersion::getVersion));
    }

    @Override
    public AppPromptVersion getVersion(Long templateId, Integer version) {
        AppPromptVersion snapshot = versionMapper.selectOne(
                new LambdaQueryWrapper<AppPromptVersion>()
                        .eq(AppPromptVersion::getTemplateId, templateId)
                        .eq(AppPromptVersion::getVersion, version));
        if (snapshot == null) {
            throw new BizException("版本不存在: v" + version);
        }
        return snapshot;
    }

    /** 回退到指定历史版本：目标内容写回主表并生成新版本快照留痕 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AppPromptTemplate rollback(Long id, Integer version) {
        AppPromptTemplate template = getById(id);
        AppPromptVersion snapshot = getVersion(id, version);

        int newVersion = nextVersion(id);
        template.setContent(snapshot.getContent());
        template.setVariables(snapshot.getVariables());
        template.setVersion(newVersion);
        templateMapper.updateById(template);

        AppPromptVersion trace = new AppPromptVersion();
        trace.setTemplateId(id);
        trace.setVersion(newVersion);
        trace.setContent(snapshot.getContent());
        trace.setVariables(snapshot.getVariables());
        trace.setRemark("回退至 v" + version);
        trace.setCreatedBy(UserContext.getUserId());
        versionMapper.insert(trace);
        return template;
    }

    private int nextVersion(Long templateId) {
        Integer max = versionMapper.selectList(new LambdaQueryWrapper<AppPromptVersion>()
                        .eq(AppPromptVersion::getTemplateId, templateId)
                        .orderByDesc(AppPromptVersion::getVersion)
                        .last("limit 1"))
                .stream().findFirst().map(AppPromptVersion::getVersion).orElse(0);
        return max + 1;
    }

    // ---------- 变量渲染（试跑支持） ----------

    /**
     * 渲染模板正文：将 {{var}} 占位替换为变量值。
     * 返回渲染后文本；未提供的变量保持原样以便前端提示。
     */
    @Override
    public String render(String content, Map<String, Object> variables) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return content;
        }
        Matcher matcher = VAR_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            String replacement = value == null ? matcher.group(0) : String.valueOf(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 从正文提取占位变量名列表 */
    @Override
    public List<String> extractVariables(String content) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        if (content != null) {
            Matcher matcher = VAR_PATTERN.matcher(content);
            while (matcher.find()) {
                seen.put(matcher.group(1), Boolean.TRUE);
            }
        }
        return List.copyOf(seen.keySet());
    }

    /** 解析变量定义 JSON（宽松解析，失败返回空 map） */
    @Override
    public Map<String, String> parseVariableDefs(String variablesJson) {
        Map<String, String> defs = new LinkedHashMap<>();
        if (variablesJson == null || variablesJson.isBlank()) {
            return defs;
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(variablesJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> item : list) {
                Object name = item.get("name");
                if (name != null) {
                    defs.put(String.valueOf(name), item.get("desc") == null ? "" : String.valueOf(item.get("desc")));
                }
            }
        } catch (Exception ignored) {
        }
        return defs;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
