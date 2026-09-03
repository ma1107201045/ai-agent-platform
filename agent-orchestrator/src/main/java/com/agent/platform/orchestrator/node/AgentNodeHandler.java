package com.agent.platform.orchestrator.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.orchestrator.NodeType;
import com.agent.platform.orchestrator.spi.AgentRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 节点：在画布中调用「规划-工具调用-观察」循环（ReAct）。
 * <p>
 * 支持配置项：
 * <ul>
 *   <li>{@code modelId} 对话模型（必填）</li>
 *   <li>{@code systemPrompt} 系统提示词</li>
 *   <li>{@code userPrompt} 用户提示词模板，默认 {{input}}</li>
 *   <li>{@code toolIds} 节点级可用工具；留空回退到应用绑定的工具</li>
 *   <li>{@code datasetIds} 节点级可用数据集；留空回退到应用绑定的数据集</li>
 *   <li>{@code maxIterations} 最大循环轮数，默认 6</li>
 *   <li>{@code includeSteps} 是否在输出中附带工具调用过程，默认 false</li>
 * </ul>
 */
@Component
public class AgentNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.AGENT;
    }

    @Override
    public String validate(NodeContext ctx) {
        if (ctx.cfgLong("modelId") == null) {
            return "Agent 节点「" + ctx.label() + "」未配置模型";
        }
        if (ctx.agentRunner() == null) {
            return "Agent 节点「" + ctx.label() + "」未接入 Agent 执行器";
        }
        return null;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        Long modelId = ctx.cfgLong("modelId");
        if (modelId == null) {
            throw new BizException("Agent 节点「" + ctx.label() + "」未配置模型");
        }
        AgentRunner runner = ctx.agentRunner();
        if (runner == null) {
            throw new BizException("Agent 节点「" + ctx.label() + "」未接入 Agent 执行器");
        }
        String systemPrompt = ctx.render(ctx.cfgStr("systemPrompt", ""));
        String userPrompt = ctx.render(ctx.cfgStr("userPrompt", "{{input}}"));
        List<Long> toolIds = ctx.cfgLongList("toolIds");
        List<Long> datasetIds = ctx.cfgLongList("datasetIds");
        int maxIterations = ctx.cfgInt("maxIterations", 6);

        AgentRunner.AgentOutcome outcome = runner.run(new AgentRunner.AgentTask(
                ctx.appId(), modelId,
                systemPrompt == null || systemPrompt.isBlank() ? null : systemPrompt,
                userPrompt, toolIds, datasetIds, maxIterations));

        String answer = outcome == null ? "" : (outcome.answer() == null ? "" : outcome.answer());
        if (ctx.cfgBool("includeSteps", false) && outcome != null && !outcome.steps().isEmpty()) {
            StringBuilder sb = new StringBuilder(answer).append("\n\n【工具调用过程】\n");
            for (int i = 0; i < outcome.steps().size(); i++) {
                AgentRunner.AgentStep s = outcome.steps().get(i);
                sb.append(i + 1).append(". ").append(s.toolName())
                        .append("(").append(s.arguments()).append(") → ")
                        .append(s.result()).append("\n");
            }
            answer = sb.toString();
        }
        ctx.emit(answer);
        return NodeResult.of(answer);
    }

    @Override
    public String describeInput(NodeContext ctx) {
        String base = ctx.render(ctx.cfgStr("userPrompt", "{{input}}")).trim();
        List<Long> toolIds = ctx.cfgLongList("toolIds");
        String tools = toolIds.isEmpty() ? "应用绑定工具" : toolIds.size() + " 个节点工具";
        return base + "（可用工具：" + tools + "）";
    }
}
