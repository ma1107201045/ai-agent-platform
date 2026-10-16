package com.agent.platform.service.app.impl;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppSkill;
import com.agent.platform.dao.entity.app.AppSkillVersion;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.app.AppSkillMapper;
import com.agent.platform.dao.mapper.app.AppSkillVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import com.agent.platform.service.app.AppSkillService;

/**
 * AppSkillService 接口实现。
 */
@Service
@RequiredArgsConstructor
public class AppSkillServiceImpl implements AppSkillService {

    /** 一次运行最多注入的技能数（超出按与用户问题的相关度择优） */
    private static final int MAX_INJECT_SKILLS = 6;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private static final Set<String> VALID_CATEGORIES = Set.of("general", "system", "business", "custom");

    private static final Set<String> VALID_TYPES = Set.of("instruction", "tool", "knowledge");

    private final AppSkillMapper skillMapper;

    private final AppSkillVersionMapper versionMapper;

    private final AppAgentMapper appAgentMapper;

    private final ObjectMapper objectMapper;

    // ---------- 技能 CRUD ----------

    @Override
    public Page<AppSkill> page(long page, long size, String keyword, String category, String type) {
        LambdaQueryWrapper<AppSkill> wrapper = new LambdaQueryWrapper<AppSkill>()
                .eq(category != null && !category.isBlank(), AppSkill::getCategory, category)
                .eq(type != null && !type.isBlank(), AppSkill::getType, type)
                .orderByDesc(AppSkill::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AppSkill::getName, keyword.trim())
                    .or().like(AppSkill::getCode, keyword.trim())
                    .or().like(AppSkill::getDescription, keyword.trim()));
        }
        return skillMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 启用技能列表（应用绑定下拉 / Agent 节点选择） */
    @Override
    public List<AppSkill> listEnabled(String category, String type) {
        LambdaQueryWrapper<AppSkill> wrapper = new LambdaQueryWrapper<AppSkill>()
                .eq(AppSkill::getStatus, 1)
                .eq(category != null && !category.isBlank(), AppSkill::getCategory, category)
                .eq(type != null && !type.isBlank(), AppSkill::getType, type)
                .orderByDesc(AppSkill::getId);
        return skillMapper.selectList(wrapper);
    }

    @Override
    public AppSkill getById(Long id) {
        AppSkill skill = skillMapper.selectById(id);
        if (skill == null) {
            throw new BizException("技能不存在: " + id);
        }
        return skill;
    }

    /** 创建技能：初始版本 v1 并写入快照 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AppSkill create(AppSkill skill) {
        normalize(skill, null);
        ensureCodeUnique(skill.getCode(), null);

        if (skill.getTenantId() == null) {
            skill.setTenantId(UserContext.getTenantId() == null ? 1L : UserContext.getTenantId());
        }
        if (skill.getStatus() == null) {
            skill.setStatus(1);
        }
        skill.setVersion(1);
        skillMapper.insert(skill);

        versionMapper.insert(buildSnapshot(skill, "初始版本"));
        return skill;
    }

    /**
     * 更新技能：基础信息（名称/描述/分类/类型/状态）直接更新；
     * 指令正文、变量定义、依赖工具或参考数据集变化时自动升版快照。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AppSkill update(AppSkill skill) {
        AppSkill exists = getById(skill.getId());
        normalize(skill, exists.getId());
        if (!exists.getCode().equals(skill.getCode())) {
            ensureCodeUnique(skill.getCode(), exists.getId());
        }

        boolean contentChanged = !safeEquals(exists.getContent(), skill.getContent())
                || !safeEquals(exists.getVariables(), skill.getVariables())
                || !safeEquals(exists.getToolIds(), skill.getToolIds())
                || !safeEquals(exists.getDatasetIds(), skill.getDatasetIds());

        exists.setName(skill.getName());
        exists.setCode(skill.getCode());
        exists.setDescription(skill.getDescription());
        exists.setCategory(skill.getCategory());
        exists.setType(skill.getType());
        if (skill.getIcon() != null) {
            exists.setIcon(skill.getIcon());
        }
        if (skill.getStatus() != null) {
            exists.setStatus(skill.getStatus());
        }
        if (contentChanged) {
            exists.setContent(skill.getContent());
            exists.setVariables(skill.getVariables());
            exists.setToolIds(skill.getToolIds());
            exists.setDatasetIds(skill.getDatasetIds());
            int newVersion = nextVersion(exists.getId());
            exists.setVersion(newVersion);
            skillMapper.updateById(exists);

            AppSkillVersion snapshot = buildSnapshot(exists, "内容更新");
            versionMapper.insert(snapshot);
        } else {
            skillMapper.updateById(exists);
        }
        return exists;
    }

    /** 删除技能：若已被应用绑定则拒绝（需先解绑），软删除进回收站（版本快照保留，恢复时还原） */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long id) {
        getById(id);
        long refs = referencingAppCount(id);
        if (refs > 0) {
            throw new BizException("技能已被 " + refs + " 个应用绑定，请先解绑后再删除");
        }
        skillMapper.markDeleted(id);
    }

    /** 引用该技能的应用数量（app_agent.skill_ids 为 JSON 数组） */
    @Override
    public long referencingAppCount(Long skillId) {
        return appAgentMapper.selectCount(new QueryWrapper<AppAgent>()
                .apply("skill_ids::jsonb @> jsonb_build_array({0}::bigint)", skillId));
    }

    /** 引用该技能的应用（供前端提示哪些应用在用） */
    @Override
    public List<AppAgent> referencingApps(Long skillId) {
        return appAgentMapper.selectList(new QueryWrapper<AppAgent>()
                .apply("skill_ids::jsonb @> jsonb_build_array({0}::bigint)", skillId)
                .orderByDesc("id"));
    }

    // ---------- 版本快照 ----------

    @Override
    public List<AppSkillVersion> versions(Long skillId) {
        getById(skillId);
        return versionMapper.selectList(new LambdaQueryWrapper<AppSkillVersion>()
                .eq(AppSkillVersion::getSkillId, skillId)
                .orderByDesc(AppSkillVersion::getVersion));
    }

    @Override
    public AppSkillVersion getVersion(Long skillId, Integer version) {
        AppSkillVersion snapshot = versionMapper.selectOne(
                new LambdaQueryWrapper<AppSkillVersion>()
                        .eq(AppSkillVersion::getSkillId, skillId)
                        .eq(AppSkillVersion::getVersion, version));
        if (snapshot == null) {
            throw new BizException("版本不存在: v" + version);
        }
        return snapshot;
    }

    /** 回退到指定历史版本：目标内容写回主表并生成新版本快照留痕 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AppSkill rollback(Long skillId, Integer version) {
        AppSkill skill = getById(skillId);
        AppSkillVersion snapshot = getVersion(skillId, version);

        int newVersion = nextVersion(skillId);
        skill.setContent(snapshot.getContent());
        skill.setVariables(snapshot.getVariables());
        skill.setToolIds(snapshot.getToolIds());
        skill.setDatasetIds(snapshot.getDatasetIds());
        skill.setVersion(newVersion);
        skillMapper.updateById(skill);

        AppSkillVersion trace = buildSnapshot(skill, "回退至 v" + version);
        trace.setVersion(newVersion);
        versionMapper.insert(trace);
        return skill;
    }

    // ---------- 导入导出 ----------

    /** 导出技能：返回当前版本自包含数据（含元信息 + 指令 + 依赖），供分享/备份 */
    @Override
    public AppSkill exportSkill(Long skillId) {
        return getById(skillId);
    }

    /** 导入技能：按外部 JSON 新建技能（id 清零重新落库），code 冲突时自动追加后缀 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AppSkill importSkill(AppSkill skill) {
        if (skill == null) {
            throw new BizException("导入内容为空");
        }
        skill.setId(null);
        skill.setTenantId(UserContext.getTenantId() == null ? 1L : UserContext.getTenantId());
        if (skill.getCode() == null || skill.getCode().isBlank()) {
            skill.setCode(autoCode(skill.getName()));
        }
        normalize(skill, null);
        ensureCodeUnique(skill.getCode(), null);

        skill.setStatus(1);
        skill.setVersion(1);
        skillMapper.insert(skill);
        versionMapper.insert(buildSnapshot(skill, "导入"));
        return skill;
    }

    /**
     * 解析绑定技能（JSON 数组）为运行期注入信息：指令 / 依赖工具 / 参考数据集。
     * 忽略已禁用技能；技能数量超过阈值时按与用户问题的相关度择优。
     */
    @Override
    public SkillRuntimeBO resolveRuntime(String skillIdsJson, String query) {
        List<Long> ids = parseIdList(skillIdsJson);
        if (ids == null || ids.isEmpty()) {
            return new SkillRuntimeBO("", List.of(), List.of());
        }
        return resolveRuntime(ids, query);
    }

    @Override
    public SkillRuntimeBO resolveRuntime(List<Long> skillIds, String query) {
        if (skillIds == null || skillIds.isEmpty()) {
            return new SkillRuntimeBO("", List.of(), List.of());
        }
        List<AppSkill> skills = skillMapper.selectList(new LambdaQueryWrapper<AppSkill>()
                .in(AppSkill::getId, skillIds)
                .eq(AppSkill::getStatus, 1));
        if (skills.isEmpty()) {
            return new SkillRuntimeBO("", List.of(), List.of());
        }

        // 数量超限时按与用户问题的相关度择优（名称/标识/描述/类型文本命中）
        List<AppSkill> picked;
        if (skills.size() > MAX_INJECT_SKILLS) {
            picked = skills.stream()
                    .sorted((a, b) -> Integer.compare(score(b, query), score(a, query)))
                    .limit(MAX_INJECT_SKILLS)
                    .toList();
        } else {
            picked = skills;
        }

        StringBuilder sb = new StringBuilder();
        Set<Long> toolSet = new LinkedHashSet<>();
        Set<Long> datasetSet = new LinkedHashSet<>();
        for (AppSkill skill : picked) {
            if (skill.getContent() != null && !skill.getContent().isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("【技能 ").append(skill.getName()).append("｜").append(typeLabel(skill.getType()))
                        .append("】");
                if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                    sb.append("\n适用场景：").append(skill.getDescription());
                }
                sb.append("\n").append(skill.getContent());
            }
            List<Long> toolIds = parseIdList(skill.getToolIds());
            if (toolIds != null) {
                toolSet.addAll(toolIds);
            }
            List<Long> datasetIds = parseIdList(skill.getDatasetIds());
            if (datasetIds != null) {
                datasetSet.addAll(datasetIds);
            }
        }
        return new SkillRuntimeBO(sb.toString(), List.copyOf(toolSet), List.copyOf(datasetSet));
    }

    /** 轻量相关度打分：用户查询词在技能可检索文本中的命中数 */
    private int score(AppSkill skill, String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        String haystack = (skill.getName() + " " + skill.getCode() + " "
                + (skill.getDescription() == null ? "" : skill.getDescription()) + " "
                + typeLabel(skill.getType())).toLowerCase(Locale.ROOT);
        int hit = 0;
        for (String term : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!term.isBlank() && haystack.contains(term)) {
                hit++;
            }
        }
        return hit;
    }

    private String typeLabel(String type) {
        return switch (type == null ? "" : type) {
            case "tool" -> "工具复合型";
            case "knowledge" -> "知识技能型";
            default -> "指令型";
        };
    }

    // ---------- 内部工具 ----------

    private void normalize(AppSkill skill, Long selfId) {
        if (skill.getName() == null || skill.getName().isBlank()) {
            throw new BizException("技能名称不能为空");
        }
        if (skill.getName().length() > 128) {
            throw new BizException("技能名称不能超过 128 字符");
        }
        if (skill.getContent() == null || skill.getContent().isBlank()) {
            throw new BizException("技能指令正文不能为空");
        }
        if (skill.getCode() == null || skill.getCode().isBlank()) {
            skill.setCode(autoCode(skill.getName()));
        } else if (!CODE_PATTERN.matcher(skill.getCode().trim()).matches()) {
            throw new BizException("技能标识须为小写字母/数字/中划线（1-64 位），如 marketing-copywriter");
        } else {
            skill.setCode(skill.getCode().trim());
        }
        if (!VALID_CATEGORIES.contains(skill.getCategory())) {
            skill.setCategory("general");
        }
        if (!VALID_TYPES.contains(skill.getType())) {
            skill.setType("instruction");
        }
        validateJsonArray(skill.getToolIds(), "依赖工具");
        validateJsonArray(skill.getDatasetIds(), "参考数据集");
        validateVariables(skill.getVariables());
    }

    private void validateVariables(String variables) {
        if (variables == null || variables.isBlank()) {
            return;
        }
        try {
            List<?> list = objectMapper.readValue(variables, List.class);
            if (list.size() > 50) {
                throw new BizException("变量数量不能超过 50 个");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("变量定义须为 JSON 数组，如 [{\"name\":\"var\",\"desc\":\"说明\"}]");
        }
    }

    private void validateJsonArray(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            throw new BizException(fieldName + "须为 ID 的 JSON 数组，如 [1,2]");
        }
    }

    private List<Long> parseIdList(String json) {
        if (json == null || json.isBlank() || "null".equals(json.trim())) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private String autoCode(String name) {
        String base = name.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "skill";
        }
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        String code = base;
        int suffix = 2;
        while (existsCode(code, null)) {
            String tail = "-" + suffix++;
            code = (base + tail).length() > 64 ? base.substring(0, 64 - tail.length()) + tail : base + tail;
        }
        return code;
    }

    private boolean existsCode(String code, Long selfId) {
        LambdaQueryWrapper<AppSkill> qw = new LambdaQueryWrapper<AppSkill>()
                .eq(AppSkill::getCode, code);
        if (selfId != null) {
            qw.ne(AppSkill::getId, selfId);
        }
        return skillMapper.selectCount(qw) > 0;
    }

    private void ensureCodeUnique(String code, Long selfId) {
        if (existsCode(code, selfId)) {
            throw new BizException("技能标识已存在: " + code);
        }
    }

    private int nextVersion(Long skillId) {
        Integer max = versionMapper.selectList(new LambdaQueryWrapper<AppSkillVersion>()
                        .eq(AppSkillVersion::getSkillId, skillId)
                        .orderByDesc(AppSkillVersion::getVersion)
                        .last("limit 1"))
                .stream().findFirst().map(AppSkillVersion::getVersion).orElse(0);
        return max + 1;
    }

    private AppSkillVersion buildSnapshot(AppSkill skill, String remark) {
        AppSkillVersion snapshot = new AppSkillVersion();
        snapshot.setSkillId(skill.getId());
        snapshot.setVersion(skill.getVersion());
        snapshot.setContent(skill.getContent());
        snapshot.setVariables(skill.getVariables());
        snapshot.setToolIds(skill.getToolIds());
        snapshot.setDatasetIds(skill.getDatasetIds());
        snapshot.setRemark(remark);
        snapshot.setCreatedBy(UserContext.getUserId());
        return snapshot;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
