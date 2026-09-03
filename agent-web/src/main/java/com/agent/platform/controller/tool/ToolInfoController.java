package com.agent.platform.controller.tool;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.tool.ToolTestDTO;
import com.agent.platform.dao.entity.tool.ToolInfo;
import com.agent.platform.service.tool.ToolInfoService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 工具（AppAgentTool）管理接口。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service → Controller → URL」对齐规则：
 * <pre>
 *   表 app_agent_tool → 实体 AppAgentTool → Mapper AppAgentToolMapper
 *                    → Service AppAgentToolService → 本类 AppAgentToolController
 *                    → URL /api/app-agent-tools（kebab-case 复数）
 * </pre>
 */
@RestController
@RequestMapping("/api/tool/infos")
@RequiredArgsConstructor
public class ToolInfoController {

    private final ToolInfoService toolService;

    @GetMapping
    public Result<Page<ToolInfo>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size) {
        return Result.ok(toolService.page(page, size));
    }

    @GetMapping("/enabled")
    public Result<List<ToolInfo>> enabled() {
        return Result.ok(toolService.listEnabled());
    }

    @GetMapping("/{id}")
    public Result<ToolInfo> getById(@PathVariable Long id) {
        return Result.ok(toolService.getById(id));
    }

    @PostMapping
    public Result<ToolInfo> create(@RequestBody ToolInfo tool) {
        return Result.ok(toolService.create(tool));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ToolInfo tool) {
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
    public Result<String> test(@PathVariable Long id, @RequestBody ToolTestDTO req) {
        return Result.ok(toolService.execute(toolService.getById(id), req.getArguments()));
    }
}
