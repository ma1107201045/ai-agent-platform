package com.agent.platform.service.orchestrator;

import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.tool.ToolInfo;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.service.app.AppAgentService;
import com.agent.platform.orchestrator.spi.AgentRunner;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link AgentRunner} 的业务实现：委托给 {@link AppAgentService}。
 * <p>
 * 本类是 agent-service 与 agent-workflow 之间的适配层，
 * 使工作流 Agent 节点无需依赖业务实现即可执行「规划-工具调用-观察」循环。
 * <p>
 * 节点未显式配置 toolIds / datasetIds 时，回退到应用（{@link AppAgent}）绑定的工具与数据集。
 */
@Component
@RequiredArgsConstructor
public class AgentRunnerImpl implements AgentRunner {

    private final AppAgentService appAgentService;
    private final ObjectMapper objectMapper;

    @Override
    public AgentOutcome run(AgentTask task) {
        if (task == null || task.modelId() == null) {
            throw new IllegalArgumentException("Agent 任务缺少模型配置");
        }
        AppAgent app = task.appId() == null ? null : appAgentService.getById(task.appId());

        List<Long> toolIds = nullIfEmpty(task.toolIds());
        if (toolIds == null && app != null) {
            toolIds = parseIds(app.getToolIds());
        }
        String datasetIdsJson = toJsonArray(task.datasetIds());
        if ((datasetIdsJson == null) && app != null) {
            datasetIdsJson = app.getDatasetIds();
        }

        List<ToolInfo> tools = appAgentService.loadTools(toJsonArray(toolIds));
        List<ChatMessage> history = List.of(ChatMessage.user(
                task.userMessage() == null ? "" : task.userMessage()));

        AppAgentService.AgentResult result = appAgentService.chat(
                task.modelId(), task.systemPrompt(), tools, datasetIdsJson, history, task.maxIterations());

        List<AgentStep> steps = result.getSteps() == null ? List.of()
                : result.getSteps().stream()
                        .map(s -> new AgentStep(s.getToolName(), s.getArguments(), s.getResult(), s.getCostMs()))
                        .toList();
        return new AgentOutcome(result.getAnswer(), steps);
    }

    private List<Long> nullIfEmpty(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null : ids;
    }

    /** 解析 [1,2] 形式的 ID 数组；空或非法返回 null */
    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json.trim())) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private String toJsonArray(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return null;
        }
    }
}
