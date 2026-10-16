package com.agent.platform.service.ops;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 费用账单：按月聚合用量成本、按日趋势、按应用/模型拆解，并维护预算。
 * 数据源为运行时写入的 chat_usage（cost 已按模型单价估算）。
 */
public interface BillingService {

    public Map<String, Object> summary(String month);

    /** 当月每日成本（缺日补零） */
    public List<Map<String, Object>> dailyTrend(String month);

    /** 按应用拆解当月费用（降序） */
    public List<Map<String, Object>> byApp(String month);

    /** 按模型拆解当月费用（降序） */
    public List<Map<String, Object>> byModel(String month);

    /** 保存/更新月度预算 */
    public void setBudget(String month, BigDecimal budget, Integer notifyEnabled);
}
