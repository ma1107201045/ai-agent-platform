package com.agent.platform.service.guard;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.guard.GuardAppBind;
import com.agent.platform.dao.entity.guard.GuardRule;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.guard.GuardAppBindMapper;
import com.agent.platform.dao.mapper.guard.GuardRuleMapper;
import com.agent.platform.dao.vo.guard.GuardAppBindVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 内容安全服务：敏感词/正则/注入检测规则维护、规则命中测试、应用级绑定。
 */
@Service
@RequiredArgsConstructor
public class GuardService {

    private final GuardRuleMapper ruleMapper;
    private final GuardAppBindMapper bindMapper;
    private final AppAgentMapper appAgentMapper;
    private final ObjectMapper objectMapper;

    // ---------- 规则 ----------

    public Page<GuardRule> page(long page, long size, String direction, String matchType, String action, Integer enabled, String keyword) {
        return ruleMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<GuardRule>()
                .eq(GuardRule::getTenantId, tenant())
                .eq(StringUtils.hasText(direction), GuardRule::getDirection, direction)
                .eq(StringUtils.hasText(matchType), GuardRule::getMatchType, matchType)
                .eq(StringUtils.hasText(action), GuardRule::getAction, action)
                .eq(enabled != null, GuardRule::getEnabled, enabled)
                .and(StringUtils.hasText(keyword),
                        w -> w.like(GuardRule::getName, keyword).or().like(GuardRule::getDescription, keyword).or().like(GuardRule::getRuleContent, keyword))
                .orderByAsc(GuardRule::getPriority)
                .orderByDesc(GuardRule::getId));
    }

    public GuardRule get(Long id) {
        GuardRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("规则不存在: " + id);
        }
        return rule;
    }

    public GuardRule create(GuardRule rule) {
        validate(rule);
        rule.setId(null);
        rule.setTenantId(tenant());
        rule.setHitCount(0L);
        rule.setRiskLevel(rule.getRiskLevel() == null ? 3 : rule.getRiskLevel());
        rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
        rule.setPriority(rule.getPriority() == null ? 1 : rule.getPriority());
        rule.setReplaceText(StringUtils.hasText(rule.getReplaceText()) ? rule.getReplaceText() : "****");
        LocalDateTime now = LocalDateTime.now();
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        ruleMapper.insert(rule);
        return rule;
    }

    public GuardRule update(Long id, GuardRule req) {
        GuardRule rule = get(id);
        validate(req);
        if (StringUtils.hasText(req.getName())) {
            rule.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            rule.setDescription(req.getDescription());
        }
        if (StringUtils.hasText(req.getDirection())) {
            rule.setDirection(req.getDirection());
        }
        if (StringUtils.hasText(req.getMatchType())) {
            rule.setMatchType(req.getMatchType());
        }
        if (StringUtils.hasText(req.getRuleContent())) {
            rule.setRuleContent(req.getRuleContent().trim());
        }
        if (StringUtils.hasText(req.getAction())) {
            rule.setAction(req.getAction());
        }
        if (req.getReplaceText() != null) {
            rule.setReplaceText(StringUtils.hasText(req.getReplaceText()) ? req.getReplaceText() : "****");
        }
        if (req.getRiskLevel() != null) {
            rule.setRiskLevel(req.getRiskLevel());
        }
        if (req.getEnabled() != null) {
            rule.setEnabled(req.getEnabled());
        }
        if (req.getPriority() != null) {
            rule.setPriority(req.getPriority());
        }
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        return rule;
    }

    public void delete(Long id) {
        get(id);
        ruleMapper.deleteById(id);
    }

    private void validate(GuardRule rule) {
        if (!StringUtils.hasText(rule.getName())) {
            throw new BizException("规则名称不能为空");
        }
        if (!StringUtils.hasText(rule.getDirection()) || (!"input".equals(rule.getDirection()) && !"output".equals(rule.getDirection()))) {
            throw new BizException("请选择作用方向(input/output)");
        }
        String matchType = rule.getMatchType();
        if (!StringUtils.hasText(matchType)
                || (!"keyword".equals(matchType) && !"regex".equals(matchType) && !"prompt_injection".equals(matchType))) {
            throw new BizException("请选择匹配方式");
        }
        if (!StringUtils.hasText(rule.getRuleContent())) {
            throw new BizException("匹配内容不能为空");
        }
        String action = rule.getAction();
        if (!StringUtils.hasText(action) || (!"block".equals(action) && !"mask".equals(action) && !"replace".equals(action))) {
            throw new BizException("请选择处置动作");
        }
    }

    /** 命中测试：对文本执行一组规则（空=全部启用规则），返回命中详情与处理结果 */
    public Map<String, Object> testHit(List<Long> ruleIds, String text) {
        if (!StringUtils.hasText(text)) {
            throw new BizException("请输入待检测文本");
        }
        List<GuardRule> rules;
        if (ruleIds == null || ruleIds.isEmpty()) {
            rules = ruleMapper.selectList(new LambdaQueryWrapper<GuardRule>()
                    .eq(GuardRule::getTenantId, tenant())
                    .eq(GuardRule::getEnabled, 1)
                    .orderByAsc(GuardRule::getPriority));
        } else {
            rules = ruleMapper.selectList(new LambdaQueryWrapper<GuardRule>()
                    .eq(GuardRule::getTenantId, tenant())
                    .in(GuardRule::getId, ruleIds)
                    .eq(GuardRule::getEnabled, 1)
                    .orderByAsc(GuardRule::getPriority));
        }
        boolean blocked = false;
        List<Map<String, Object>> hits = new ArrayList<>();
        String output = text;
        for (GuardRule rule : rules) {
            List<String> matched = match(rule, output);
            if (matched.isEmpty()) {
                continue;
            }
            // 处理动作对文本生效（按优先级顺序依次 mask/replace）
            if ("mask".equals(rule.getAction()) || "replace".equals(rule.getAction())) {
                output = applyRule(rule, output);
            }
            if ("block".equals(rule.getAction())) {
                blocked = true;
            }
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("ruleId", rule.getId());
            hit.put("name", rule.getName());
            hit.put("matchType", rule.getMatchType());
            hit.put("direction", rule.getDirection());
            hit.put("action", rule.getAction());
            hit.put("riskLevel", rule.getRiskLevel());
            hit.put("matched", matched);
            hits.add(hit);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blocked", blocked);
        result.put("hits", hits);
        result.put("hitCount", hits.size());
        result.put("output", blocked ? "" : output);
        result.put("changed", !output.equals(text));
        return result;
    }

    private List<String> match(GuardRule rule, String text) {
        if ("regex".equals(rule.getMatchType())) {
            try {
                Pattern p = Pattern.compile(rule.getRuleContent(), Pattern.CASE_INSENSITIVE);
                List<String> out = new ArrayList<>();
                java.util.regex.Matcher m = p.matcher(text);
                while (m.find()) {
                    out.add(m.group());
                }
                return out;
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        // keyword / prompt_injection：逗号分隔关键词包含匹配
        String lower = text.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String kw : splitKeywords(rule.getRuleContent())) {
            if (lower.contains(kw.toLowerCase())) {
                out.add(kw);
            }
        }
        return out;
    }

    /** 对 keyword 类型执行字面量替换（保留大小写不敏感语义用正则）；regex 类型直接替换全部匹配 */
    private String applyRule(GuardRule rule, String text) {
        String rep = StringUtils.hasText(rule.getReplaceText()) ? rule.getReplaceText() : "****";
        if ("regex".equals(rule.getMatchType())) {
            try {
                Pattern p = Pattern.compile(rule.getRuleContent(), Pattern.CASE_INSENSITIVE);
                return p.matcher(text).replaceAll(java.util.regex.Matcher.quoteReplacement(rep));
            } catch (Exception e) {
                return text;
            }
        }
        String out = text;
        for (String kw : splitKeywords(rule.getRuleContent())) {
            if (kw.isEmpty()) {
                continue;
            }
            out = Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE)
                    .matcher(out)
                    .replaceAll(java.util.regex.Matcher.quoteReplacement(rep));
        }
        return out;
    }

    private List<String> splitKeywords(String content) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return out;
        }
        for (String part : content.split("[,，;；\\s]+")) {
            if (StringUtils.hasText(part)) {
                out.add(part.trim());
            }
        }
        return out;
    }

    // ---------- 应用绑定 ----------

    public List<GuardAppBindVO> bindList() {
        List<AppAgent> apps = appAgentMapper.selectList(new LambdaQueryWrapper<AppAgent>()
                .eq(AppAgent::getTenantId, tenant())
                .orderByDesc(AppAgent::getId));
        List<GuardAppBind> binds = bindMapper.selectList(new LambdaQueryWrapper<GuardAppBind>()
                .eq(GuardAppBind::getTenantId, tenant()));
        Map<Long, GuardAppBind> bindMap = binds.stream().collect(Collectors.toMap(GuardAppBind::getAppId, b -> b, (a, b) -> a));
        List<GuardAppBindVO> vos = new ArrayList<>();
        for (AppAgent app : apps) {
            GuardAppBindVO vo = new GuardAppBindVO();
            vo.setAppId(app.getId());
            vo.setAppName(app.getName());
            vo.setAppType(app.getType());
            GuardAppBind bind = bindMap.get(app.getId());
            if (bind != null) {
                vo.setBindId(bind.getId());
                vo.setBindEnabled(bind.getEnabled());
                vo.setBindMode(bind.getMode());
                vo.setRuleIds(bind.getRuleIds());
                vo.setRuleCount(parseRuleIds(bind.getRuleIds()).size());
            }
            vos.add(vo);
        }
        return vos;
    }

    public GuardAppBind getBind(Long appId) {
        if (appId == null) {
            throw new BizException("应用ID不能为空");
        }
        return bindMapper.selectOne(new LambdaQueryWrapper<GuardAppBind>()
                .eq(GuardAppBind::getTenantId, tenant())
                .eq(GuardAppBind::getAppId, appId));
    }

    public GuardAppBind saveBind(Long appId, List<Long> ruleIds, String mode, Integer enabled) {
        AppAgent app = appAgentMapper.selectById(appId);
        if (app == null) {
            throw new BizException("应用不存在: " + appId);
        }
        String ruleJson;
        try {
            ruleJson = objectMapper.writeValueAsString(ruleIds == null ? new ArrayList<Long>() : ruleIds);
        } catch (Exception e) {
            throw new BizException("规则数据序列化失败");
        }
        GuardAppBind bind = getBind(appId);
        if (bind == null) {
            bind = new GuardAppBind();
            bind.setTenantId(tenant());
            bind.setAppId(appId);
            bind.setMode(StringUtils.hasText(mode) ? mode : "enforce");
            bind.setEnabled(enabled == null ? 1 : enabled);
            bind.setRuleIds(ruleJson);
            LocalDateTime now = LocalDateTime.now();
            bind.setCreateTime(now);
            bind.setUpdateTime(now);
            bindMapper.insert(bind);
        } else {
            bind.setRuleIds(ruleJson);
            bind.setMode(StringUtils.hasText(mode) ? mode : bind.getMode());
            if (enabled != null) {
                bind.setEnabled(enabled);
            }
            bind.setUpdateTime(LocalDateTime.now());
            bindMapper.updateById(bind);
        }
        return bind;
    }

    public void deleteBind(Long appId) {
        bindMapper.delete(new LambdaQueryWrapper<GuardAppBind>()
                .eq(GuardAppBind::getTenantId, tenant())
                .eq(GuardAppBind::getAppId, appId));
    }

    private Set<Long> parseRuleIds(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptySet();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Long.class));
            return new LinkedHashSet<>(ids);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
