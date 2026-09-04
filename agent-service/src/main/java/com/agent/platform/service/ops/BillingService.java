package com.agent.platform.service.ops;

import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.chat.ChatUsage;
import com.agent.platform.dao.entity.model.ModelInfo;
import com.agent.platform.dao.entity.ops.BillBudget;
import com.agent.platform.dao.mapper.chat.ChatUsageMapper;
import com.agent.platform.dao.mapper.model.ModelInfoMapper;
import com.agent.platform.dao.mapper.ops.BillBudgetMapper;
import com.agent.platform.service.app.AppAgentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 费用账单：按月聚合用量成本、按日趋势、按应用/模型拆解，并维护预算。
 * 数据源为运行时写入的 chat_usage（cost 已按模型单价估算）。
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private final ChatUsageMapper chatUsageMapper;
    private final BillBudgetMapper budgetMapper;
    private final AppAgentService appAgentService;
    private final ModelInfoMapper modelInfoMapper;

    public Map<String, Object> summary(String month) {
        String m = normalize(month);
        List<ChatUsage> rows = rowsOf(m);
        BigDecimal totalCost = BigDecimal.ZERO;
        long tokens = 0;
        long prompt = 0;
        long completion = 0;
        BigDecimal todayCost = BigDecimal.ZERO;
        String today = LocalDate.now().toString();
        for (ChatUsage u : rows) {
            totalCost = totalCost.add(safe(u.getCost()));
            tokens += nvl(u.getTotalTokens());
            prompt += nvl(u.getPromptTokens());
            completion += nvl(u.getCompletionTokens());
            if (u.getCreateTime() != null && u.getCreateTime().toLocalDate().toString().equals(today)) {
                todayCost = todayCost.add(safe(u.getCost()));
            }
        }
        BillBudget budget = budgetMapper.selectOne(new LambdaQueryWrapper<BillBudget>()
                .eq(BillBudget::getTenantId, tenant())
                .eq(BillBudget::getMonth, m));
        BigDecimal budgetVal = budget == null || budget.getBudget() == null ? BigDecimal.ZERO : budget.getBudget();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", m);
        result.put("totalCost", totalCost.setScale(2, RoundingMode.HALF_UP));
        result.put("todayCost", todayCost.setScale(2, RoundingMode.HALF_UP));
        result.put("totalTokens", tokens);
        result.put("promptTokens", prompt);
        result.put("completionTokens", completion);
        result.put("callCount", rows.size());
        result.put("budget", budgetVal.setScale(2, RoundingMode.HALF_UP));
        result.put("notifyEnabled", budget == null ? 0 : budget.getNotifyEnabled());
        if (budgetVal.compareTo(BigDecimal.ZERO) > 0) {
            result.put("budgetUsedPct", totalCost.multiply(BigDecimal.valueOf(100))
                    .divide(budgetVal, 1, RoundingMode.HALF_UP));
        } else {
            result.put("budgetUsedPct", null);
        }
        return result;
    }

    /** 当月每日成本（缺日补零） */
    public List<Map<String, Object>> dailyTrend(String month) {
        String m = normalize(month);
        YearMonth ym = YearMonth.parse(m);
        Map<Integer, BigDecimal> byDay = new LinkedHashMap<>();
        for (ChatUsage u : rowsOf(m)) {
            if (u.getCreateTime() != null) {
                byDay.merge(u.getCreateTime().getDayOfMonth(), safe(u.getCost()), BigDecimal::add);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", ym.atDay(day).format(DateTimeFormatter.ofPattern("MM-dd")));
            item.put("cost", byDay.getOrDefault(day, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            result.add(item);
        }
        return result;
    }

    /** 按应用拆解当月费用（降序） */
    public List<Map<String, Object>> byApp(String month) {
        String m = normalize(month);
        Map<Long, Map<String, Object>> agg = new LinkedHashMap<>();
        for (ChatUsage u : rowsOf(m)) {
            Long appId = u.getAppId() == null ? 0L : u.getAppId();
            Map<String, Object> item = agg.computeIfAbsent(appId, k -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("appId", k);
                map.put("cost", BigDecimal.ZERO);
                map.put("tokens", 0L);
                map.put("calls", 0);
                return map;
            });
            item.put("cost", ((BigDecimal) item.get("cost")).add(safe(u.getCost())));
            item.put("tokens", (Long) item.get("tokens") + nvl(u.getTotalTokens()));
            item.put("calls", (Integer) item.get("calls") + 1);
        }
        List<Map<String, Object>> result = new ArrayList<>(agg.values());
        for (Map<String, Object> item : result) {
            Long appId = (Long) item.get("appId");
            if (appId == 0L) {
                item.put("appName", "公共调用");
                item.put("appType", "-");
            } else {
                AppAgent app = appAgentService.getById(appId);
                item.put("appName", app == null ? "应用 #" + appId : app.getName());
                item.put("appType", app == null ? "-" : app.getType());
            }
            item.put("cost", ((BigDecimal) item.get("cost")).setScale(2, RoundingMode.HALF_UP));
        }
        result.sort((a, b) -> ((BigDecimal) b.get("cost")).compareTo((BigDecimal) a.get("cost")));
        return result;
    }

    /** 按模型拆解当月费用（降序） */
    public List<Map<String, Object>> byModel(String month) {
        String m = normalize(month);
        Map<Long, Map<String, Object>> agg = new LinkedHashMap<>();
        for (ChatUsage u : rowsOf(m)) {
            if (u.getModelId() == null) {
                continue;
            }
            Long modelId = u.getModelId();
            Map<String, Object> item = agg.computeIfAbsent(modelId, k -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("modelId", k);
                map.put("cost", BigDecimal.ZERO);
                map.put("tokens", 0L);
                map.put("calls", 0);
                return map;
            });
            item.put("cost", ((BigDecimal) item.get("cost")).add(safe(u.getCost())));
            item.put("tokens", (Long) item.get("tokens") + nvl(u.getTotalTokens()));
            item.put("calls", (Integer) item.get("calls") + 1);
        }
        List<Map<String, Object>> result = new ArrayList<>(agg.values());
        for (Map<String, Object> item : result) {
            Long modelId = (Long) item.get("modelId");
            ModelInfo model = modelInfoMapper.selectById(modelId);
            item.put("modelName", model == null ? "模型 #" + modelId : model.getName());
            item.put("cost", ((BigDecimal) item.get("cost")).setScale(2, RoundingMode.HALF_UP));
        }
        result.sort((a, b) -> ((BigDecimal) b.get("cost")).compareTo((BigDecimal) a.get("cost")));
        return result;
    }

    /** 保存/更新月度预算 */
    public void setBudget(String month, BigDecimal budget, Integer notifyEnabled) {
        String m = normalize(month);
        BillBudget bill = budgetMapper.selectOne(new LambdaQueryWrapper<BillBudget>()
                .eq(BillBudget::getTenantId, tenant())
                .eq(BillBudget::getMonth, m));
        BigDecimal b = budget == null ? BigDecimal.ZERO : budget;
        if (bill == null) {
            bill = new BillBudget();
            bill.setTenantId(tenant());
            bill.setMonth(m);
            bill.setBudget(b);
            bill.setNotifyEnabled(notifyEnabled == null ? 0 : notifyEnabled);
            bill.setCreateTime(LocalDateTime.now());
            bill.setUpdateTime(LocalDateTime.now());
            budgetMapper.insert(bill);
            return;
        }
        bill.setBudget(b);
        bill.setNotifyEnabled(notifyEnabled == null ? 0 : notifyEnabled);
        bill.setUpdateTime(LocalDateTime.now());
        budgetMapper.updateById(bill);
    }

    private List<ChatUsage> rowsOf(String month) {
        YearMonth ym = YearMonth.parse(month);
        return chatUsageMapper.selectList(new LambdaQueryWrapper<ChatUsage>()
                .eq(ChatUsage::getTenantId, tenant())
                .ge(ChatUsage::getCreateTime, ym.atDay(1).atStartOfDay())
                .lt(ChatUsage::getCreateTime, ym.plusMonths(1).atDay(1).atStartOfDay())
                .orderByAsc(ChatUsage::getCreateTime));
    }

    private String normalize(String month) {
        return StringUtils.hasText(month) ? month : YearMonth.now().toString();
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private long nvl(Long v) {
        return v == null ? 0 : v;
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
