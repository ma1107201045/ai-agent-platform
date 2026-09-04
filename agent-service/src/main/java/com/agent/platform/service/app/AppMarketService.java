package com.agent.platform.service.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppMarketItem;
import com.agent.platform.dao.mapper.app.AppMarketItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用市场服务：浏览官方/共享应用并一键安装。
 */
@Service
@RequiredArgsConstructor
public class AppMarketService {

    private final AppMarketItemMapper marketItemMapper;
    private final AppAgentService appAgentService;
    private final ObjectMapper objectMapper;

    /** 展示上架条目（官方 + 当前租户共享 + 已公开的共享） */
    public Page<AppMarketItem> page(long page, long size, String category, String type, String keyword) {
        return marketItemMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<AppMarketItem>()
                .eq(AppMarketItem::getStatus, 1)
                .and(w -> w.isNull(AppMarketItem::getTenantId)
                        .or().eq(AppMarketItem::getTenantId, 0L)
                        .or().eq(AppMarketItem::getTenantId, tenant()))
                .eq(StringUtils.hasText(category), AppMarketItem::getCategory, category)
                .eq(StringUtils.hasText(type), AppMarketItem::getType, type)
                .and(StringUtils.hasText(keyword),
                        w -> w.like(AppMarketItem::getName, keyword).or().like(AppMarketItem::getDescription, keyword))
                .orderByAsc(AppMarketItem::getCategory)
                .orderByDesc(AppMarketItem::getInstallCount));
    }

    /** 分类及安装量统计 */
    public Map<String, Object> stats() {
        List<AppMarketItem> items = marketItemMapper.selectList(new LambdaQueryWrapper<AppMarketItem>()
                .eq(AppMarketItem::getStatus, 1)
                .and(w -> w.isNull(AppMarketItem::getTenantId)
                        .or().eq(AppMarketItem::getTenantId, 0L)
                        .or().eq(AppMarketItem::getTenantId, tenant())));
        Map<String, Long> byCategory = new LinkedHashMap<>();
        long totalInstall = 0;
        for (AppMarketItem item : items) {
            String cat = item.getCategory() == null ? "other" : item.getCategory();
            byCategory.merge(cat, 1L, Long::sum);
            totalInstall += item.getInstallCount() == null ? 0 : item.getInstallCount();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", items.size());
        map.put("totalInstall", totalInstall);
        map.put("byCategory", byCategory);
        return map;
    }

    public AppMarketItem get(Long id) {
        AppMarketItem item = marketItemMapper.selectById(id);
        if (item == null || item.getStatus() == null || item.getStatus() != 1) {
            throw new BizException("应用不存在或已下架: " + id);
        }
        return item;
    }

    /**
     * 一键安装：拷贝条目创建应用并自动发布，返回新应用ID。
     */
    @Transactional(rollbackFor = Exception.class)
    public AppAgent install(Long itemId) {
        AppMarketItem item = get(itemId);
        AppAgent app = new AppAgent();
        app.setTenantId(tenant());
        app.setName(item.getName());
        app.setDescription(item.getDescription());
        app.setType(item.getType() == null ? "chatflow" : item.getType());
        app.setIcon(item.getIcon());
        app.setWorkflowJson(item.getWorkflowJson());
        applyConfig(app, item.getConfigJson());
        AppAgent created = appAgentService.create(app);
        // 自动发布，安装即可对话/调试
        appAgentService.publish(created.getId(), item.getWorkflowJson(), null, UserContext.getUserId());
        marketItemMapper.update(null, new LambdaUpdateWrapper<AppMarketItem>()
                .eq(AppMarketItem::getId, itemId)
                .setSql("install_count = install_count + 1"));
        return created;
    }

    @SuppressWarnings("unchecked")
    private void applyConfig(AppAgent app, String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, Map.class);
            Object welcome = config.get("welcome_message");
            if (welcome != null) {
                app.setWelcomeMessage(String.valueOf(welcome));
            }
            Object questions = config.get("opening_questions");
            if (questions instanceof List<?> list && !list.isEmpty()) {
                app.setOpeningQuestions(objectMapper.writeValueAsString(list));
            }
        } catch (Exception ignored) {
            // 配置解析失败不阻塞安装
        }
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
