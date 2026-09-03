package com.agent.platform.controller.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.app.AppAgentChatDTO;
import com.agent.platform.dao.dto.app.AppAgentPublishDTO;
import com.agent.platform.dao.dto.app.AppAgentRunDTO;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppAgentVersion;
import com.agent.platform.dao.vo.app.AgentChatVO;
import com.agent.platform.dao.vo.chat.AppAgentStatsVO;
import com.agent.platform.orchestrator.RunResult;
import com.agent.platform.orchestrator.WorkflowEngine;
import com.agent.platform.orchestrator.WorkflowEventListener;
import com.agent.platform.orchestrator.WorkflowGraph;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.service.app.AppAgentService;
import com.agent.platform.service.chat.ChatConversationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 智能体应用（AppAgent）接口。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service → Controller → URL」对齐规则：
 * <pre>
 *   表 app_agent → 实体 AppAgent → Mapper AppAgentMapper → Service AppAgentService
 *              → 本类 AppAgentController → URL /api/app-agents（kebab-case 复数）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/app/agents")
@RequiredArgsConstructor
public class AppAgentController {

    private final AppAgentService appAgentService;
    private final ChatConversationService conversationService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    /** SSE 流式运行专用线程池（长连接任务，避免占用 Web 工作线程） */
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    @GetMapping
    public Result<Page<AppAgent>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String type) {
        return Result.ok(appAgentService.page(page, size, keyword, type));
    }

    @GetMapping("/{id}")
    public Result<AppAgent> getById(@PathVariable Long id) {
        return Result.ok(appAgentService.getById(id));
    }

    @PostMapping
    public Result<AppAgent> create(@RequestBody AppAgent app) {
        return Result.ok(appAgentService.create(app));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AppAgent app) {
        app.setId(id);
        appAgentService.update(app);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appAgentService.delete(id);
        return Result.ok();
    }

    /** 发布（保存版本快照） */
    @PostMapping("/{id}/publish")
    public Result<AppAgentVersion> publish(@PathVariable Long id, @RequestBody AppAgentPublishDTO request) {
        return Result.ok(appAgentService.publish(id, request.getWorkflowJson(), request.getPromptConfig(), 1L));
    }

    /** 获取当前发布版本 */
    @GetMapping("/{id}/published")
    public Result<AppAgentVersion> published(@PathVariable Long id) {
        return Result.ok(appAgentService.getPublishedVersion(id));
    }

    /** 版本列表（按版本号倒序） */
    @GetMapping("/{id}/versions")
    public Result<List<AppAgentVersion>> versions(@PathVariable Long id) {
        return Result.ok(appAgentService.listVersions(id));
    }

    /** 回滚到指定版本（恢复草稿，不自动发布） */
    @PostMapping("/{id}/versions/{versionId}/rollback")
    public Result<AppAgentVersion> rollback(@PathVariable Long id, @PathVariable Long versionId) {
        return Result.ok(appAgentService.rollback(id, versionId));
    }

    /** 批量会话统计（对外访问/运营数据）：ids 逗号分隔 */
    @GetMapping("/batch/stats")
    public Result<Map<Long, AppAgentStatsVO>> batchStats(@RequestParam String ids) {
        List<Long> idList = parseIds(ids);
        return Result.ok(conversationService.statsBatch(idList));
    }

    /** 批量获取发布版本：ids 逗号分隔，返回 Map<appId, version> */
    @GetMapping("/batch/published")
    public Result<Map<Long, AppAgentVersion>> batchPublished(@RequestParam String ids) {
        Map<Long, AppAgentVersion> map = new HashMap<>();
        for (Long id : parseIds(ids)) {
            try {
                map.put(id, appAgentService.getPublishedVersion(id));
            } catch (Exception ignored) {
                // 未发布的应用跳过
            }
        }
        return Result.ok(map);
    }

    private List<Long> parseIds(String ids) {
        List<Long> list = new ArrayList<>();
        if (ids == null) {
            return list;
        }
        for (String s : ids.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                list.add(Long.valueOf(t));
            } catch (NumberFormatException ignored) {
                // 忽略非法 id
            }
        }
        return list;
    }

    /** 运行应用工作流（按画布 DSL 执行） */
    @PostMapping("/{id}/run")
    public Result<RunResult> run(@PathVariable Long id, @RequestBody AppAgentRunDTO request) {
        String dsl = appAgentService.getRunWorkflow(id);
        if (dsl == null || dsl.isBlank()) {
            throw new BizException("应用尚未编排工作流，请先在画布中保存草稿或发布");
        }
        try {
            WorkflowGraph graph = objectMapper.readValue(dsl, WorkflowGraph.class);
            String userInput = extractUserInput(request);
            return Result.ok(workflowEngine.run(graph, userInput, id));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("工作流 DSL 解析失败: " + e.getMessage());
        }
    }

    /**
     * 流式运行工作流（SSE 实时节点进度，供画布调试「实时监控」使用）
     * <p>
     * 事件协议（text/event-stream）：
     * <ul>
     *   <li>{@code run-started}：data = {"runId":"run_xxx","startedAt":...}（最先到达，供取消关联）</li>
     *   <li>{@code node-started}：data = {"nodeId":"n1"}（节点进入执行，画布点亮运行中）</li>
     *   <li>{@code node-finished}：data = TraceItem JSON（success / error / skipped / canceled）</li>
     *   <li>{@code done}：data = RunResult JSON，随后连接关闭</li>
     * </ul>
     * 客户端断开时自动取消本次运行（{@link WorkflowEngine#cancel(String)}）。
     */
    @PostMapping(value = "/{id}/run-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runStream(@PathVariable Long id, @RequestBody AppAgentRunDTO request) {
        String dsl = appAgentService.getRunWorkflow(id);
        if (dsl == null || dsl.isBlank()) {
            throw new BizException("应用尚未编排工作流，请先在画布中保存草稿或发布");
        }
        final WorkflowGraph graph;
        try {
            graph = objectMapper.readValue(dsl, WorkflowGraph.class);
        } catch (Exception e) {
            throw new BizException("工作流 DSL 解析失败: " + e.getMessage());
        }
        final String userInput = extractUserInput(request);
        final SseEmitter emitter = new SseEmitter(600_000L);
        final AtomicReference<String> runIdRef = new AtomicReference<>();
        final AtomicBoolean finished = new AtomicBoolean();
        WorkflowEventListener listener = new WorkflowEventListener() {
            /** 推送统一帧：{"type":"node-started","data":...}，data 行 JSON 自描述，便于各端按同协议解析 */
            private void send(String type, Object data) {
                if (finished.get()) {
                    return;
                }
                try {
                    Map<String, Object> frame = new HashMap<>();
                    frame.put("type", type);
                    frame.put("data", data);
                    emitter.send(frame);
                } catch (IOException e) {
                    // 客户端断开：终止本次运行，避免无谓消耗
                    finished.set(true);
                    String rid = runIdRef.get();
                    if (rid != null) {
                        workflowEngine.cancel(rid);
                    }
                    emitter.complete();
                }
            }

            @Override
            public void onFlowStarted(WorkflowEventListener.FlowStarted e) {
                runIdRef.set(e.runId());
                Map<String, Object> payload = new HashMap<>();
                payload.put("runId", e.runId());
                payload.put("startedAt", e.startedAt() == null ? null : e.startedAt().toString());
                send("run-started", payload);
            }

            @Override
            public void onNodeStarted(WorkflowEventListener.NodeStarted e) {
                send("node-started", Map.of("nodeId", e.nodeId()));
            }

            @Override
            public void onNodeFinished(WorkflowEventListener.NodeFinished e) {
                send("node-finished", e.traceItem());
            }

            @Override
            public void onFlowFinished(WorkflowEventListener.FlowFinished e) {
                if (finished.compareAndSet(false, true)) {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(e.result()));
                    } catch (IOException ex) {
                        log.warn("推送工作流完成事件失败 runId={}: {}", e.result().getRunId(), ex.getMessage());
                    } finally {
                        emitter.complete();
                    }
                }
            }
        };
        // 异步执行：方法立即返回 SseEmitter，引擎事件在节点线程驱动推送
        streamExecutor.execute(() -> {
            try {
                workflowEngine.run(graph, userInput, id, null, List.of(listener));
            } catch (Exception e) {
                // 引擎意外抛错（正常失败会走 RunResult + done 事件，不会到此处）
                log.warn("流式运行工作流失败 appId={}: {}", id, e.getMessage());
                if (finished.compareAndSet(false, true)) {
                    try {
                        Map<String, Object> frame = new HashMap<>();
                        frame.put("type", "run-error");
                        frame.put("data", Map.of("message", String.valueOf(e.getMessage())));
                        emitter.send(frame);
                    } catch (IOException ignored) {
                        // 客户端已断开
                    }
                    emitter.complete();
                }
            }
        });
        return emitter;
    }

    private String extractUserInput(AppAgentRunDTO request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BizException("请输入消息");
        }
        return request.getMessages().stream()
                .filter(m -> "user".equals(m.role()))
                .reduce((first, second) -> second)
                .map(ChatMessage::content)
                .orElseThrow(() -> new BizException("请输入消息"));
    }

    /**
     * Agent 自主对话（非流式）：规划-工具调用-观察循环
     */
    @PostMapping("/{id}/agent/chat")
    public Result<AgentChatVO> agentChat(@PathVariable Long id, @RequestBody AppAgentChatDTO request) {
        if (request.getModelId() == null) {
            throw new BizException("请选择对话模型");
        }
        return Result.ok(appAgentService.chat(id, request.getModelId(), request.getSystemPrompt(),
                request.getMessages(), request.getMaxIterations()));
    }
}
