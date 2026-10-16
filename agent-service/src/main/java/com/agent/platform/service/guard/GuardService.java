package com.agent.platform.service.guard;

import com.agent.platform.dao.entity.guard.GuardAppBind;
import com.agent.platform.dao.entity.guard.GuardRule;
import com.agent.platform.dao.vo.guard.GuardAppBindVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/**
 * 内容安全服务：敏感词/正则/注入检测规则维护、规则命中测试、应用级绑定。
 */
public interface GuardService {

    public Page<GuardRule> page(long page, long size, String direction, String matchType, String action, Integer enabled, String keyword);

    public GuardRule get(Long id);

    public GuardRule create(GuardRule rule);

    public GuardRule update(Long id, GuardRule req);

    public void delete(Long id);

    /** 命中测试：对文本执行一组规则（空=全部启用规则），返回命中详情与处理结果 */
    public Map<String, Object> testHit(List<Long> ruleIds, String text);

    public List<GuardAppBindVO> bindList();

    public GuardAppBind getBind(Long appId);

    public GuardAppBind saveBind(Long appId, List<Long> ruleIds, String mode, Integer enabled);

    public void deleteBind(Long appId);
}
