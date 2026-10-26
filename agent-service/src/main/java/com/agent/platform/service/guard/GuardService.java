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

    Page<GuardRule> page(long page, long size, String direction, String matchType, String action, Integer enabled, String keyword);

    GuardRule get(Long id);

    GuardRule create(GuardRule rule);

    GuardRule update(Long id, GuardRule req);

    void delete(Long id);

    /** 命中测试：对文本执行一组规则（空=全部启用规则），返回命中详情与处理结果 */
    Map<String, Object> testHit(List<Long> ruleIds, String text);

    List<GuardAppBindVO> bindList();

    GuardAppBind getBind(Long appId);

    GuardAppBind saveBind(Long appId, List<Long> ruleIds, String mode, Integer enabled);

    void deleteBind(Long appId);
}
