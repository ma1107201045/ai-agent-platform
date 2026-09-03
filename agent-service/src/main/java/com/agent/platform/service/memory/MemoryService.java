package com.agent.platform.service.memory;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.memory.MemItem;
import com.agent.platform.dao.entity.memory.MemStrategy;
import com.agent.platform.dao.entity.memory.MemVariable;
import com.agent.platform.dao.mapper.memory.MemItemMapper;
import com.agent.platform.dao.mapper.memory.MemStrategyMapper;
import com.agent.platform.dao.mapper.memory.MemVariableMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 记忆管理服务：记忆策略 / 会话变量 / 长期记忆条目
 *
 * <p>本服务提供管理面能力（增删改查 + 检索），不直接参与对话链路；
 * 运行时注入逻辑由编排/对话服务按 {@link MemStrategy} 读取记忆后拼装 Prompt。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final Set<String> VARIABLE_SCOPES = Set.of("global", "session");
    private static final Set<String> VARIABLE_TYPES = Set.of("string", "number", "boolean", "json");
    private static final Set<String> ITEM_SCOPES = Set.of("global", "user");
    private static final Set<String> ITEM_CATEGORIES = Set.of("preference", "fact", "event", "summary", "custom");
    private static final Pattern VARIABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    private final MemStrategyMapper strategyMapper;
    private final MemVariableMapper variableMapper;
    private final MemItemMapper itemMapper;

    // ---------- 记忆策略 ----------

    /**
     * 获取某应用的记忆策略；不存在时返回一份默认配置（enabled=0 需用户显式开启）。
     */
    public MemStrategy getStrategy(Long appId) {
        MemStrategy strategy = strategyMapper.selectOne(
                new LambdaQueryWrapper<MemStrategy>().eq(MemStrategy::getAppId, appId));
        if (strategy != null) {
            return strategy;
        }
        MemStrategy defaults = new MemStrategy();
        defaults.setAppId(appId);
        defaults.setTenantId(1L);
        defaults.setEnabled(0);
        defaults.setAutoExtract(0);
        defaults.setTopN(3);
        defaults.setMaxItems(500);
        defaults.setStatus(1);
        defaults.setCreateTime(LocalDateTime.now());
        defaults.setUpdateTime(LocalDateTime.now());
        strategyMapper.insert(defaults);
        return defaults;
    }

    /**
     * 保存记忆策略（按 appId upsert）。
     */
    public MemStrategy saveStrategy(MemStrategy strategy) {
        if (strategy == null || strategy.getAppId() == null) {
            throw new BizException("应用ID不能为空");
        }
        Long appId = strategy.getAppId();
        MemStrategy exist = strategyMapper.selectOne(
                new LambdaQueryWrapper<MemStrategy>().eq(MemStrategy::getAppId, appId));
        LocalDateTime now = LocalDateTime.now();
        // 兜底默认值
        if (strategy.getEnabled() == null) strategy.setEnabled(0);
        if (strategy.getAutoExtract() == null) strategy.setAutoExtract(0);
        if (strategy.getTopN() == null || strategy.getTopN() <= 0) strategy.setTopN(3);
        if (strategy.getMaxItems() == null || strategy.getMaxItems() <= 0) strategy.setMaxItems(500);
        if (strategy.getStatus() == null) strategy.setStatus(1);
        strategy.setTenantId(1L);
        strategy.setUpdateTime(now);
        if (exist != null) {
            strategy.setId(exist.getId());
            strategy.setCreateTime(exist.getCreateTime());
            strategyMapper.updateById(strategy);
            return strategy;
        }
        strategy.setId(null);
        strategy.setCreateTime(now);
        strategyMapper.insert(strategy);
        return strategy;
    }

    // ---------- 会话变量 ----------

    public List<MemVariable> listVariables(Long appId, String scope, String keyword) {
        LambdaQueryWrapper<MemVariable> wrapper = new LambdaQueryWrapper<MemVariable>()
                .eq(MemVariable::getAppId, appId)
                .eq(MemVariable::getStatus, 1);
        if (StringUtils.hasText(scope)) {
            wrapper.eq(MemVariable::getScope, scope);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(MemVariable::getName, kw)
                    .or().like(MemVariable::getValue, kw)
                    .or().like(MemVariable::getRemark, kw));
        }
        return variableMapper.selectList(wrapper.orderByDesc(MemVariable::getUpdateTime));
    }

    public MemVariable createVariable(Long appId, MemVariable variable) {
        validateApp(appId);
        if (variable == null || !StringUtils.hasText(variable.getName())) {
            throw new BizException("变量名不能为空");
        }
        String name = variable.getName().trim();
        if (!VARIABLE_NAME.matcher(name).matches()) {
            throw new BizException("变量名须为字母/数字/下划线，且以字母或下划线开头");
        }
        if (variable.getScope() == null || !VARIABLE_SCOPES.contains(variable.getScope())) {
            variable.setScope("global");
        }
        String type = variable.getValueType();
        if (type == null || !VARIABLE_TYPES.contains(type)) {
            variable.setValueType("string");
        }
        // 同名同会话去重（session 空会话按空值互斥，避免重复键）
        MemVariable exist = variableMapper.selectOne(new LambdaQueryWrapper<MemVariable>()
                .eq(MemVariable::getAppId, appId)
                .eq(MemVariable::getScope, variable.getScope())
                .eq(MemVariable::getName, name)
                .eq(variable.getConversationId() != null, MemVariable::getConversationId, variable.getConversationId())
                .isNull(variable.getConversationId() == null, MemVariable::getConversationId));
        if (exist != null) {
            throw new BizException("该作用域下已存在同名变量: " + name);
        }
        LocalDateTime now = LocalDateTime.now();
        variable.setId(null);
        variable.setAppId(appId);
        variable.setTenantId(1L);
        variable.setName(name);
        variable.setStatus(variable.getStatus() == null ? 1 : variable.getStatus());
        variable.setCreateTime(now);
        variable.setUpdateTime(now);
        variableMapper.insert(variable);
        return variable;
    }

    public void updateVariable(Long id, MemVariable variable) {
        MemVariable exist = requireVariable(id);
        if (variable == null) {
            throw new BizException("请求体不能为空");
        }
        if (variable.getName() != null) {
            String name = variable.getName().trim();
            if (!VARIABLE_NAME.matcher(name).matches()) {
                throw new BizException("变量名须为字母/数字/下划线，且以字母或下划线开头");
            }
            exist.setName(name);
        }
        if (variable.getValue() != null) {
            exist.setValue(variable.getValue());
        }
        if (variable.getValueType() != null) {
            String type = variable.getValueType();
            if (!VARIABLE_TYPES.contains(type)) {
                throw new BizException("不支持的变量类型: " + type);
            }
            exist.setValueType(type);
        }
        if (variable.getScope() != null && VARIABLE_SCOPES.contains(variable.getScope())) {
            exist.setScope(variable.getScope());
        }
        if (variable.getConversationId() != null) {
            exist.setConversationId(variable.getConversationId());
        }
        if (variable.getRemark() != null) {
            exist.setRemark(variable.getRemark());
        }
        if (variable.getStatus() != null) {
            exist.setStatus(variable.getStatus());
        }
        exist.setUpdateTime(LocalDateTime.now());
        variableMapper.updateById(exist);
    }

    public void deleteVariable(Long id) {
        requireVariable(id);
        variableMapper.deleteById(id);
    }

    // ---------- 长期记忆条目 ----------

    public List<MemItem> listItems(Long appId, String category, String scope, String keyword) {
        LambdaQueryWrapper<MemItem> wrapper = new LambdaQueryWrapper<MemItem>()
                .eq(MemItem::getAppId, appId)
                .eq(StringUtils.hasText(category), MemItem::getCategory, category)
                .eq(StringUtils.hasText(scope), MemItem::getScope, scope);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(MemItem::getContent, kw));
        }
        return itemMapper.selectList(wrapper
                .orderByDesc(MemItem::getStatus)
                .orderByDesc(MemItem::getImportance)
                .orderByDesc(MemItem::getId));
    }

    public MemItem createItem(Long appId, MemItem item) {
        validateApp(appId);
        if (item == null || !StringUtils.hasText(item.getContent())) {
            throw new BizException("记忆内容不能为空");
        }
        if (item.getCategory() == null || !ITEM_CATEGORIES.contains(item.getCategory())) {
            item.setCategory("custom");
        }
        if (item.getScope() == null || !ITEM_SCOPES.contains(item.getScope())) {
            item.setScope("global");
        }
        if (item.getSource() == null) {
            item.setSource("manual");
        }
        if (item.getImportance() == null || item.getImportance() < 1 || item.getImportance() > 5) {
            item.setImportance(3);
        }
        LocalDateTime now = LocalDateTime.now();
        item.setId(null);
        item.setAppId(appId);
        item.setTenantId(1L);
        item.setStatus(item.getStatus() == null ? 1 : item.getStatus());
        item.setHitCount(0);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        itemMapper.insert(item);
        return item;
    }

    public void updateItem(Long id, MemItem item) {
        MemItem exist = requireItem(id);
        if (item == null) {
            throw new BizException("请求体不能为空");
        }
        if (item.getContent() != null && !item.getContent().isBlank()) {
            exist.setContent(item.getContent().trim());
        }
        if (item.getCategory() != null && ITEM_CATEGORIES.contains(item.getCategory())) {
            exist.setCategory(item.getCategory());
        }
        if (item.getScope() != null && ITEM_SCOPES.contains(item.getScope())) {
            exist.setScope(item.getScope());
        }
        if (item.getImportance() != null && item.getImportance() >= 1 && item.getImportance() <= 5) {
            exist.setImportance(item.getImportance());
        }
        if (item.getStatus() != null) {
            exist.setStatus(item.getStatus());
        }
        exist.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(exist);
    }

    public void deleteItem(Long id) {
        requireItem(id);
        itemMapper.deleteById(id);
    }

    // ---------- 私有工具 ----------

    private void validateApp(Long appId) {
        if (appId == null) {
            throw new BizException("应用ID不能为空");
        }
    }

    private MemVariable requireVariable(Long id) {
        MemVariable variable = variableMapper.selectById(id);
        if (variable == null) {
            throw new BizException("会话变量不存在: " + id);
        }
        return variable;
    }

    private MemItem requireItem(Long id) {
        MemItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException("记忆条目不存在: " + id);
        }
        return item;
    }
}
