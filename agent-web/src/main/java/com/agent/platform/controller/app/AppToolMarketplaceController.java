package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.app.AppTool;
import com.agent.platform.dao.vo.app.AppToolTemplateVO;
import com.agent.platform.service.app.AppToolMarketplaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 插件市场接口：内置工具模板目录 + 一键安装。
 *
 * <p>命名遵循「Service → Controller → URL」对齐规则：
 * Service ToolMarketplaceService → 本类 ToolMarketplaceController → URL /api/tool/marketplace
 */
@RestController
@RequestMapping("/api/app/tool-marketplace")
@RequiredArgsConstructor
public class AppToolMarketplaceController {

    private final AppToolMarketplaceService marketplaceService;

    /** 模板目录（含已安装标记），可按分类 / 关键字过滤 */
    @GetMapping("/templates")
    public Result<List<AppToolTemplateVO>> templates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return Result.ok(marketplaceService.templates(category, keyword));
    }

    /** 一键安装模板：以模板内容创建真实工具 */
    @PostMapping("/templates/{key}/install")
    public Result<AppTool> install(@PathVariable String key) {
        return Result.ok(marketplaceService.install(key));
    }
}
