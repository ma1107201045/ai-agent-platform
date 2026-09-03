package com.agent.platform.controller.chat;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.chat.AgentRun;
import com.agent.platform.service.chat.AgentRunService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工作流运行记录接口（运行监控）
 */
@RestController
@RequestMapping("/api/agent-runs")
@RequiredArgsConstructor
public class AgentRunController {

    private final AgentRunService agentRunService;

    /** 运行记录分页（可按应用 / 状态过滤） */
    @GetMapping
    public Result<Page<AgentRun>> page(@RequestParam(required = false) Long appId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size) {
        return Result.ok(agentRunService.page(appId, status, page, size));
    }

    /** 运行详情（含节点轨迹 JSON） */
    @GetMapping("/{runId}")
    public Result<AgentRun> detail(@PathVariable String runId,
                                   @RequestParam(required = false) Long appId) {
        return Result.ok(agentRunService.getByRunId(runId, appId));
    }
}
