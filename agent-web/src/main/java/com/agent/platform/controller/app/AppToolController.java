package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.app.AppAgentTool;
import com.agent.platform.service.app.AppToolService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 工具管理接口
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class AppToolController {

    private final AppToolService toolService;

    @GetMapping
    public Result<Page<AppAgentTool>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size) {
        return Result.ok(toolService.page(page, size));
    }

    @GetMapping("/enabled")
    public Result<List<AppAgentTool>> enabled() {
        return Result.ok(toolService.listEnabled());
    }

    @GetMapping("/{id}")
    public Result<AppAgentTool> getById(@PathVariable Long id) {
        return Result.ok(toolService.getById(id));
    }

    @PostMapping
    public Result<AppAgentTool> create(@RequestBody AppAgentTool tool) {
        return Result.ok(toolService.create(tool));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AppAgentTool tool) {
        tool.setId(id);
        toolService.update(tool);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        toolService.delete(id);
        return Result.ok();
    }

    /** 工具执行测试 */
    @PostMapping("/{id}/test")
    public Result<String> test(@PathVariable Long id, @RequestBody TestReq req) {
        return Result.ok(toolService.execute(toolService.getById(id), req.getArguments()));
    }

    @Data
    public static class TestReq {
        private String arguments;
    }
}
