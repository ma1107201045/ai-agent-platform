package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppMarketItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;

/**
 * 应用市场服务：浏览官方/共享应用并一键安装。
 */
public interface AppMarketService {

    /** 展示上架条目（官方 + 当前租户共享 + 已公开的共享） */
    Page<AppMarketItem> page(long page, long size, String category, String type, String keyword);

    /** 分类及安装量统计 */
    Map<String, Object> stats();

    AppMarketItem get(Long id);

    /**
     * 一键安装：拷贝条目创建应用并自动发布，返回新应用ID。
     */
    AppAgent install(Long itemId);
}
