package com.agent.platform.workflow.spi;

import java.util.List;

/**
 * Agent（自主规划 + 工具调用）执行能力 SPI（由业务模块实现并注入）
 * <p>
 * 引擎只依赖本接口，不依赖工具/模型管理的具体实现，
 * 从而让工作流 Agent 节点具备与「智能体应用」一致的 ReAct 循环能力。
 */
public interface AgentRunner {

    /**
     * 执行一次 Agent 循环。
     */
    AgentOutcome run(AgentTask task);

    /**
     * Agent 执行任务（引擎侧模型，与业务实现解耦）
     *
     * @param appId         所属应用 ID，可为 null；Agent 节点未显式配置工具/数据集时回退到应用绑定配置
     * @param modelId       对话模型 ID
     * @param systemPrompt  系统提示词（已渲染），可为 null
     * @param userMessage   用户消息（已渲染）
     * @param toolIds       本次可用的工具 ID；为空表示由业务实现自行回退
     * @param datasetIds    本次可用的知识库数据集 ID；为空表示由业务实现自行回退
     * @param maxIterations 最大循环轮数
     */
    record AgentTask(Long appId, Long modelId, String systemPrompt, String userMessage,
                     List<Long> toolIds, List<Long> datasetIds, Integer maxIterations) {
    }

    /**
     * Agent 执行结果
     *
     * @param answer 最终回答
     * @param steps  工具调用步骤（用于轨迹展示）
     */
    record AgentOutcome(String answer, List<AgentStep> steps) {

        public AgentOutcome {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    /** 单步工具调用记录 */
    record AgentStep(String toolName, String arguments, String result, long costMs) {
    }
}
