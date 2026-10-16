package com.agent.platform.service.ops;

import com.agent.platform.dao.entity.ops.OpsAlertEvent;
import com.agent.platform.dao.entity.ops.OpsAlertRule;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;

/**
 * 告警管理：规则维护 + 事件跟踪（手动测试触发，自动触发将接入运行监控流）
 */
public interface OpsAlertService {

    public Page<OpsAlertRule> rulePage(long page, long size, String keyword);

    public OpsAlertRule createRule(OpsAlertRule rule);

    public OpsAlertRule updateRule(Long id, OpsAlertRule rule);

    public void toggleRule(Long id, Integer enabled);

    public void removeRule(Long id);

    public Page<OpsAlertEvent> eventPage(long page, long size, String status, Long ruleId, String keyword);

    /** 事件统计（未处理数等） */
    public Map<String, Object> eventStats();

    /** 手动触发一次测试事件，走完整事件链路 */
    public OpsAlertEvent fireTest(Long ruleId);

    public void updateEventStatus(Long id, String status);

    public void removeEvent(Long id);
}
