package com.agent.platform.graph.node;

import com.agent.platform.graph.NodeType;
import org.mvel2.MVEL;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 条件分支节点（排他分支）：求值表达式，仅释放选中分支的出边。
 * <p>
 * 求值规则：
 * <ol>
 *   <li>true/false 字面量直接判定；</li>
 *   <li>其余交给 MVEL 求值，支持 ==、!=、&gt;、&lt;、&gt;=、&lt;=、contains、&amp;&amp;、||、! 与括号；</li>
 *   <li>MVEL 求值失败时回退为「非空即真」。</li>
 * </ol>
 * 出边 handle 约定见 {@link BranchHandle}：true 分支为 {@code "true"}，false 分支为 {@code "false"}。
 */
@Component
public class ConditionNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.CONDITION;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        return NodeResult.branch(BranchHandle.of(evaluate(ctx)));
    }

    /**
     * 条件表达式求值。
     * 表达式中的 {{变量}} 先经渲染器替换为实际值，再交给 MVEL 求值。
     */
    private boolean evaluate(NodeContext ctx) {
        String expr = ctx.cfgStr("expression");
        if (expr == null) {
            return true;
        }
        // 条件表达式中不引用用户输入，固定传空，避免 {{input}} 被误替换
        String rendered = ctx.renderer().render(expr, "", ctx.outputs()).trim();
        if ("true".equalsIgnoreCase(rendered)) {
            return true;
        }
        if ("false".equalsIgnoreCase(rendered)) {
            return false;
        }
        try {
            Map<String, Object> vars = new HashMap<>(ctx.outputs());
            vars.put("input", ctx.outputs().getOrDefault("input", ""));
            Object result = MVEL.eval(rendered, vars);
            if (result instanceof Boolean b) {
                return b;
            }
            return result != null && !String.valueOf(result).isBlank();
        } catch (Exception e) {
            // 表达式语法不支持或变量未渲染为可比较值：按非空即真处理
            return !rendered.isEmpty();
        }
    }
}
