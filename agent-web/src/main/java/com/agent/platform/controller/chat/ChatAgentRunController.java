package com.agent.platform.controller.chat;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.chat.ChatAgentRun;
import com.agent.platform.orchestrator.WorkflowEngine;
import com.agent.platform.service.chat.ChatAgentRunService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工作流运行记录接口（运行监控）
 */
@RestController
@RequestMapping("/api/chat/runs")
@RequiredArgsConstructor
public class ChatAgentRunController {

    private final ChatAgentRunService chatAgentRunService;
    private final WorkflowEngine workflowEngine;

    /** 运行记录分页（可按应用 / 状态过滤） */
    @GetMapping
    public Result<Page<ChatAgentRun>> page(@RequestParam(required = false) Long appId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return Result.ok(chatAgentRunService.page(appId, status, page, size));
    }

    /** 运行详情（含节点轨迹 JSON） */
    @GetMapping("/{runId}")
    public Result<ChatAgentRun> detail(@PathVariable String runId,
                                       @RequestParam(required = false) Long appId) {
        return Result.ok(chatAgentRunService.getByRunId(runId, appId));
    }

    /** 取消一次运行中的工作流；已结束或不存在返回 false */
    @PostMapping("/{runId}/cancel")
    public Result<Boolean> cancel(@PathVariable String runId) {
        return Result.ok(workflowEngine.cancel(runId));
    }
}
