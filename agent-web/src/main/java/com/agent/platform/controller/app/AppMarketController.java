package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppMarketItem;
import com.agent.platform.service.app.AppMarketService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 应用市场接口
 *
 * <p>URL：/api/app-market</p>
 */
@RestController
@RequestMapping("/api/app-market")
@RequiredArgsConstructor
public class AppMarketController {

    private final AppMarketService marketService;

    @GetMapping
    public Result<Page<AppMarketItem>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String keyword) {
        return Result.ok(marketService.page(page, size, category, type, keyword));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(marketService.stats());
    }

    @GetMapping("/{id}")
    public Result<AppMarketItem> get(@PathVariable Long id) {
        return Result.ok(marketService.get(id));
    }

    /** 一键安装（自动发布），返回新应用 */
    @PostMapping("/{id}/install")
    public Result<AppAgent> install(@PathVariable Long id) {
        return Result.ok(marketService.install(id));
    }
}
