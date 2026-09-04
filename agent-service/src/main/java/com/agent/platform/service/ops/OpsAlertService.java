package com.agent.platform.service.ops;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.ops.OpsAlertEvent;
import com.agent.platform.dao.entity.ops.OpsAlertRule;
import com.agent.platform.dao.mapper.ops.OpsAlertEventMapper;
import com.agent.platform.dao.mapper.ops.OpsAlertRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 告警管理：规则维护 + 事件跟踪（手动测试触发，自动触发将接入运行监控流）
 */
@Service
@RequiredArgsConstructor
public class OpsAlertService {

    private static final Map<String, String> METRICS = new HashMap<>() {{
        put("error_rate", "错误率");
        put("failures", "运行失败数");
        put("latency", "平均延迟(ms)");
        put("cost", "成本(元)");
    }};

    private final OpsAlertRuleMapper ruleMapper;
    private final OpsAlertEventMapper eventMapper;

    /* ==================== 规则 ==================== */

    public Page<OpsAlertRule> rulePage(long page, long size, String keyword) {
        LambdaQueryWrapper<OpsAlertRule> wrapper = new LambdaQueryWrapper<OpsAlertRule>()
                .eq(OpsAlertRule::getTenantId, tenant())
                .orderByDesc(OpsAlertRule::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OpsAlertRule::getName, keyword)
                    .or().like(OpsAlertRule::getMetric, keyword)
                    .or().like(OpsAlertRule::getRemark, keyword));
        }
        return ruleMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public OpsAlertRule createRule(OpsAlertRule rule) {
        validate(rule);
        rule.setId(null);
        rule.setTenantId(tenant());
        rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
        LocalDateTime now = LocalDateTime.now();
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        ruleMapper.insert(rule);
        return rule;
    }

    public OpsAlertRule updateRule(Long id, OpsAlertRule rule) {
        OpsAlertRule exists = getRule(id);
        validate(rule);
        exists.setName(rule.getName().trim());
        exists.setMetric(rule.getMetric());
        exists.setOperator(StringUtils.hasText(rule.getOperator()) ? rule.getOperator() : ">=");
        exists.setThreshold(rule.getThreshold());
        exists.setWindowMinutes(rule.getWindowMinutes() == null ? 60 : rule.getWindowMinutes());
        exists.setLevel(rule.getLevel());
        exists.setChannels(rule.getChannels());
        exists.setWebhookUrl(rule.getWebhookUrl());
        exists.setEnabled(rule.getEnabled() == null ? exists.getEnabled() : rule.getEnabled());
        exists.setRemark(rule.getRemark());
        exists.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(exists);
        return exists;
    }

    public void toggleRule(Long id, Integer enabled) {
        OpsAlertRule rule = getRule(id);
        rule.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
    }

    @Transactional
    public void removeRule(Long id) {
        OpsAlertRule rule = getRule(id);
        ruleMapper.deleteById(rule.getId());
        eventMapper.delete(new LambdaQueryWrapper<OpsAlertEvent>()
                .eq(OpsAlertEvent::getTenantId, tenant())
                .eq(OpsAlertEvent::getRuleId, id));
    }

    private void validate(OpsAlertRule rule) {
        if (rule == null || !StringUtils.hasText(rule.getName())) {
            throw new BizException("规则名称不能为空");
        }
        if (!METRICS.containsKey(rule.getMetric())) {
            throw new BizException("不支持的指标类型");
        }
        if (rule.getThreshold() == null) {
            throw new BizException("阈值不能为空");
        }
        if (!"warning".equals(rule.getLevel()) && !"critical".equals(rule.getLevel())) {
            throw new BizException("级别需为 warning 或 critical");
        }
    }

    private OpsAlertRule getRule(Long id) {
        OpsAlertRule rule = ruleMapper.selectById(id);
        if (rule == null || !rule.getTenantId().equals(tenant())) {
            throw new BizException("告警规则不存在: " + id);
        }
        return rule;
    }

    /* ==================== 事件 ==================== */

    public Page<OpsAlertEvent> eventPage(long page, long size, String status, Long ruleId, String keyword) {
        LambdaQueryWrapper<OpsAlertEvent> wrapper = new LambdaQueryWrapper<OpsAlertEvent>()
                .eq(OpsAlertEvent::getTenantId, tenant())
                .orderByDesc(OpsAlertEvent::getId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(OpsAlertEvent::getStatus, status);
        }
        if (ruleId != null) {
            wrapper.eq(OpsAlertEvent::getRuleId, ruleId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OpsAlertEvent::getRuleName, keyword)
                    .or().like(OpsAlertEvent::getContent, keyword));
        }
        return eventMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 事件统计（未处理数等） */
    public Map<String, Object> eventStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("open", eventMapper.selectCount(new LambdaQueryWrapper<OpsAlertEvent>()
                .eq(OpsAlertEvent::getTenantId, tenant())
                .eq(OpsAlertEvent::getStatus, "open")));
        stats.put("today", eventMapper.selectCount(new LambdaQueryWrapper<OpsAlertEvent>()
                .eq(OpsAlertEvent::getTenantId, tenant())
                .ge(OpsAlertEvent::getTriggerTime, LocalDateTime.now().toLocalDate().atStartOfDay())));
        return stats;
    }

    /** 手动触发一次测试事件，走完整事件链路 */
    public OpsAlertEvent fireTest(Long ruleId) {
        OpsAlertRule rule = getRule(ruleId);
        OpsAlertEvent event = new OpsAlertEvent();
        event.setTenantId(tenant());
        event.setRuleId(rule.getId());
        event.setRuleName(rule.getName());
        event.setMetric(rule.getMetric());
        event.setLevel(rule.getLevel());
        String threshold = rule.getThreshold() == null ? "-" : rule.getThreshold().stripTrailingZeros().toPlainString();
        event.setContent("【手动测试】规则「" + rule.getName() + "」触发：" + labelOf(rule.getMetric())
                + " " + rule.getOperator() + " " + threshold
                + "（" + rule.getWindowMinutes() + " 分钟窗口）");
        event.setStatus("open");
        event.setSource("manual");
        event.setTriggerTime(LocalDateTime.now());
        eventMapper.insert(event);

        rule.setLastFireTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        return event;
    }

    public void updateEventStatus(Long id, String status) {
        if (!"open".equals(status) && !"handled".equals(status) && !"ignored".equals(status)) {
            throw new BizException("不支持的事件状态: " + status);
        }
        OpsAlertEvent event = eventMapper.selectById(id);
        if (event == null || !event.getTenantId().equals(tenant())) {
            throw new BizException("告警事件不存在: " + id);
        }
        event.setStatus(status);
        event.setHandledTime("handled".equals(status) ? LocalDateTime.now() : null);
        eventMapper.updateById(event);
    }

    public void removeEvent(Long id) {
        OpsAlertEvent event = eventMapper.selectById(id);
        if (event == null || !event.getTenantId().equals(tenant())) {
            throw new BizException("告警事件不存在: " + id);
        }
        eventMapper.deleteById(id);
    }

    private String labelOf(String metric) {
        return METRICS.getOrDefault(metric, metric);
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
