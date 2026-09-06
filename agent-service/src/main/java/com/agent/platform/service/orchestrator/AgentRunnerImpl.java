package com.agent.platform.service.orchestrator;

import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppTool;
import com.agent.platform.dao.vo.app.AppAgentChatVO;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.orchestrator.spi.AgentRunner;
import com.agent.platform.service.app.AppAgentService;
import com.agent.platform.service.app.AppSkillService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * {@link AgentRunner} 的业务实现：委托给 {@link AppAgentService}。
 * <p>
 * 本类是 agent-service 与 agent-orchestrator 之间的适配层，
 * 使工作流 Agent / 技能节点无需依赖业务实现即可执行「规划-工具调用-观察」循环。
 * <p>
 * 节点未显式配置 toolIds / datasetIds 时，回退到应用（{@link AppAgent}）绑定的工具与数据集；
 * 显式配置 skillIds 时，技能指令自动拼入系统提示词，技能依赖的工具/数据集与本次配置合并。
 */
@Component
@RequiredArgsConstructor
public class AgentRunnerImpl implements AgentRunner {

    /** 与 AppAgentService 默认系统提示词保持一致（技能注入时以此为基底） */
    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个智能助手。请根据对话内容判断是否需要调用工具来完成任务，如果工具结果对回答有帮助，请结合工具结果作答。";

    private final AppAgentService appAgentService;
    private final AppSkillService appSkillService;
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
        List<Long> datasetIds = nullIfEmpty(task.datasetIds());
        if (datasetIds == null && app != null) {
            datasetIds = parseIds(app.getDatasetIds());
        }

        // 技能注入：指令拼入提示词，依赖工具/数据集与显式配置合并
        List<Long> skillIds = nullIfEmpty(task.skillIds());
        String systemPrompt = task.systemPrompt();
        if (skillIds != null) {
            AppSkillService.SkillRuntimeBO rt = appSkillService.resolveRuntime(skillIds, task.userMessage());
            if (rt.instructions() != null && !rt.instructions().isBlank()) {
                systemPrompt = appendSkillInstructions(systemPrompt, rt.instructions());
            }
            toolIds = mergeIds(toolIds, rt.toolIds());
            datasetIds = mergeIds(datasetIds, rt.datasetIds());
        }

        List<AppTool> tools = appAgentService.loadTools(toJsonArray(toolIds));
        List<ChatMessage> history = List.of(ChatMessage.user(
                task.userMessage() == null ? "" : task.userMessage()));

        AppAgentChatVO result = appAgentService.chat(
                task.modelId(), systemPrompt, tools, toJsonArray(datasetIds), history, task.maxIterations());

        List<AgentStep> steps = result.getSteps() == null ? List.of()
                : result.getSteps().stream()
                        .map(s -> new AgentStep(s.getToolName(), s.getArguments(), s.getResult(), s.getCostMs()))
                        .toList();
        return new AgentOutcome(result.getAnswer(), steps);
    }

    /** 将技能指令拼到系统提示词（未显式配置时使用默认提示词为基底） */
    private String appendSkillInstructions(String systemPrompt, String instructions) {
        String base = systemPrompt == null || systemPrompt.isBlank()
                ? DEFAULT_SYSTEM_PROMPT
                : systemPrompt;
        return base + "\n\n【已启用技能】请仅在相关场景按对应技能要求作答，技能要求与通用指令冲突时以技能为准。\n\n"
                + instructions;
    }

    /** 合并两组 ID（保持顺序、去重），可为 null */
    private List<Long> mergeIds(List<Long> base, List<Long> extra) {
        LinkedHashSet<Long> merged = new LinkedHashSet<>();
        if (base != null) {
            merged.addAll(base);
        }
        if (extra != null) {
            merged.addAll(extra);
        }
        return merged.isEmpty() ? null : new ArrayList<>(merged);
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
