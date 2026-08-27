package com.agent.platform.controller;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.AgentApp;
import com.agent.platform.dao.entity.AgentAppVersion;
import com.agent.platform.engine.RunResult;
import com.agent.platform.engine.WorkflowEngine;
import com.agent.platform.engine.WorkflowGraph;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.service.AppService;
import com.agent.platform.service.ConversationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用管理
 */
@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;
    private final ConversationService conversationService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<Page<AgentApp>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String type) {
        return Result.ok(appService.page(page, size, keyword, type));
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

    /** 批量会话统计（对外访问/运营数据）：ids 逗号分隔 */
    @GetMapping("/stats/batch")
    public Result<Map<Long, ConversationService.AppStats>> batchStats(@RequestParam String ids) {
        List<Long> idList = parseIds(ids);
        return Result.ok(conversationService.statsBatch(idList));
    }

    /** 批量获取发布版本：ids 逗号分隔，返回 Map<appId, version> */
    @GetMapping("/published/batch")
    public Result<Map<Long, AgentAppVersion>> publishedBatch(@RequestParam String ids) {
        Map<Long, AgentAppVersion> map = new HashMap<>();
        for (Long id : parseIds(ids)) {
            try {
                map.put(id, appService.getPublishedVersion(id));
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
    public Result<RunResult> run(@PathVariable Long id, @RequestBody RunRequest request) {
        String dsl = appService.getRunWorkflow(id);
        if (dsl == null || dsl.isBlank()) {
            throw new BizException("应用尚未编排工作流，请先在画布中保存草稿或发布");
        }
        try {
            WorkflowGraph graph = objectMapper.readValue(dsl, WorkflowGraph.class);
            String userInput = extractUserInput(request);
            return Result.ok(workflowEngine.run(graph, userInput));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("工作流 DSL 解析失败: " + e.getMessage());
        }
    }

    private String extractUserInput(RunRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BizException("请输入消息");
        }
        return request.getMessages().stream()
                .filter(m -> "user".equals(m.role()))
                .reduce((first, second) -> second)
                .map(ChatMessage::content)
                .orElseThrow(() -> new BizException("请输入消息"));
    }

    @Data
    public static class RunRequest {
        private List<ChatMessage> messages;
    }

    @Data
    public static class PublishRequest {
        private String workflowJson;
        private String promptConfig;
    }
}
