package com.agent.platform.orchestrator.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.orchestrator.NodeType;
import com.agent.platform.orchestrator.spi.AgentRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技能执行节点：以绑定的「技能」驱动 Agent 循环（技能指令 + 依赖工具/数据集由业务执行器解析）。
 * <p>
 * 技能是应用层（app_skill）的可复用能力包：本节点仅声明「用哪个技能 + 哪台模型」，
 * 指令拼装 / 依赖工具与知识库合并统一由 {@code AgentRunner} 实现完成（引擎不感知技能细节）。
 * 支持配置项：
 * <ul>
 *   <li>{@code modelId} 对话模型（必填）</li>
 *   <li>{@code skillId} 启用的技能（必填）</li>
 *   <li>{@code userPrompt} 用户提示词模板，默认 {{input}}</li>
 *   <li>{@code maxIterations} 最大循环轮数，默认 6</li>
 *   <li>{@code includeSteps} 是否在输出中附带工具调用过程，默认 false</li>
 * </ul>
 * 未显式配置工具/数据集时回退到应用绑定配置（与 Agent 节点策略一致）。
 */
@Component
public class SkillNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.SKILL;
    }

    @Override
    public List<NodeField> fields() {
        return List.of(
                NodeField.builder().key("modelId").label("对话模型").type("model")
                        .description("执行技能所需模型").required(true).build(),
                NodeField.builder().key("skillId").label("技能").type("skill")
                        .description("选择要执行的技能（指令/依赖自动注入）").required(true).build(),
                NodeField.textarea("userPrompt", "用户提示词").defaultValue("{{input}}"),
                NodeField.number("maxIterations", "最大循环轮数").defaultValue(6),
                NodeField.bool("includeSteps", "输出中附带工具调用过程"));
    }

    @Override
    public String validate(NodeContext ctx) {
        if (ctx.cfgLong("modelId") == null) {
            return "技能节点「" + ctx.label() + "」未配置模型";
        }
        if (ctx.cfgLong("skillId") == null) {
            return "技能节点「" + ctx.label() + "」未选择技能";
        }
        if (ctx.agentRunner() == null) {
            return "技能节点「" + ctx.label() + "」未接入 Agent 执行器";
        }
        return null;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        Long modelId = ctx.cfgLong("modelId");
        Long skillId = ctx.cfgLong("skillId");
        if (modelId == null) {
            throw new BizException("技能节点「" + ctx.label() + "」未配置模型");
        }
        if (skillId == null) {
            throw new BizException("技能节点「" + ctx.label() + "」未选择技能");
        }
        AgentRunner runner = ctx.agentRunner();
        if (runner == null) {
            throw new BizException("技能节点「" + ctx.label() + "」未接入 Agent 执行器");
        }
        String userPrompt = ctx.render(ctx.cfgStr("userPrompt", "{{input}}"));
        int maxIterations = ctx.cfgInt("maxIterations", 6);

        AgentRunner.AgentOutcome outcome = runner.run(new AgentRunner.AgentTask(
                ctx.appId(), modelId, null, userPrompt, null, null,
                maxIterations, List.of(skillId)));

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
        return ctx.render(ctx.cfgStr("userPrompt", "{{input}}")).trim() + "（技能执行）";
    }
}
