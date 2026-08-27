package com.agent.platform.controller;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.AgentApp;
import com.agent.platform.dao.entity.AgentAppVersion;
import com.agent.platform.service.AppService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 应用管理
 */
@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;

    @GetMapping
    public Result<Page<AgentApp>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size) {
        return Result.ok(appService.page(page, size));
    }

    @GetMapping("/{id}")
    public Result<AgentApp> getById(@PathVariable Long id) {
        return Result.ok(appService.getById(id));
    }

    @PostMapping
    public Result<AgentApp> create(@RequestBody AgentApp app) {
        return Result.ok(appService.create(app));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AgentApp app) {
        app.setId(id);
        appService.update(app);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appService.delete(id);
        return Result.ok();
    }

    /** 发布（保存版本快照） */
    @PostMapping("/{id}/publish")
    public Result<AgentAppVersion> publish(@PathVariable Long id, @RequestBody PublishRequest request) {
        return Result.ok(appService.publish(id, request.getWorkflowJson(), request.getPromptConfig(), 1L));
    }

    /** 获取当前发布版本 */
    @GetMapping("/{id}/published")
    public Result<AgentAppVersion> published(@PathVariable Long id) {
        return Result.ok(appService.getPublishedVersion(id));
    }

    @Data
    public static class PublishRequest {
        private String workflowJson;
        private String promptConfig;
    }
}
