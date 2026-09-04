package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppTemplate;
import com.agent.platform.service.app.AppTemplateService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 应用模板接口
 *
 * <p>URL：/api/app/templates</p>
 */
@RestController
@RequestMapping("/api/app/templates")
@RequiredArgsConstructor
public class AppTemplateController {

    private final AppTemplateService templateService;

    @GetMapping
    public Result<Page<AppTemplate>> page(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "12") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String appType,
                                          @RequestParam(required = false) Integer status) {
        return Result.ok(templateService.page(page, size, keyword, category, appType, status));
    }

    @GetMapping("/{id}")
    public Result<AppTemplate> getById(@PathVariable Long id) {
        return Result.ok(templateService.getById(id));
    }

    @PostMapping
    public Result<AppTemplate> create(@RequestBody AppTemplate template) {
        return Result.ok(templateService.create(template));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AppTemplate template) {
        templateService.update(id, template);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.ok();
    }

    /** 从模板一键创建应用草稿，body 可选 {name: 自定义应用名} */
    @PostMapping("/{id}/instantiate")
    public Result<AppAgent> instantiate(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        String name = body == null ? null : (String) body.get("name");
        return Result.ok(templateService.instantiate(id, name));
    }
}
