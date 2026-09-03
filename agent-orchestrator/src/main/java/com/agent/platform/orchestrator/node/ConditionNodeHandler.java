package com.agent.platform.orchestrator.node;

import com.agent.platform.orchestrator.NodeType;
import org.mvel2.MVEL;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件分支节点（排他分支）：多分支 / 二分支两种模式，执行后仅释放选中分支的出边。
 * <p>
 * <b>多分支模式</b>（配置 {@code branches: [{key, label, expression}]}）：
 * 自上而下求值，第一个为真的分支被选中；全部不成立时走默认分支（handle = {@code "else"}）。
 * <br>
 * <b>二分支模式</b>（兼容旧配置 {@code expression}）：为真走 {@code "true"}，为假走 {@code "false"}。
 * <p>
 * 求值规则：
 * <ol>
 *   <li>true/false 字面量直接判定；</li>
 *   <li>其余交给 MVEL 求值，支持 ==、!=、&gt;、&lt;、&gt;=、&lt;=、contains、&amp;&amp;、||、! 与括号；</li>
 *   <li>MVEL 求值失败时回退为「非空即真」。</li>
 * </ol>
 */
@Component
public class ConditionNodeHandler implements NodeHandler {

    /** 默认分支 handle：多分支模式下所有条件都不成立时走该出边 */
    public static final String ELSE_HANDLE = "else";

    @Override
    public NodeType type() {
        return NodeType.CONDITION;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        List<Map<String, Object>> branches = readBranches(ctx);
        if (!branches.isEmpty()) {
            for (Map<String, Object> b : branches) {
                String key = str(b.get("key"));
                String expr = str(b.get("expression"));
                if (key == null || key.isBlank() || expr == null || expr.isBlank()) {
                    continue;
                }
                if (evaluate(ctx, expr)) {
                    return NodeResult.of(key, key);
                }
            }
            return NodeResult.of(ELSE_HANDLE, ELSE_HANDLE);
        }
        // 兼容二分支：expression 缺省时视为真
        boolean value = ctx.cfgStr("expression") == null || evaluate(ctx, ctx.cfgStr("expression", ""));
        String handle = value ? "true" : "false";
        return NodeResult.of(handle, handle);
    }

    /** 读取多分支配置（前端数组，历史数据可能是 JSON 字符串） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readBranches(NodeContext ctx) {
        Object raw = ctx.config().get("branches");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    result.add((Map<String, Object>) m);
                }
            }
            return result;
        }
        return List.of();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 条件表达式求值。
     * 表达式中的 {{变量}} 先经渲染器替换为实际值，再交给 MVEL 求值。
     */
    private boolean evaluate(NodeContext ctx, String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        // 条件表达式中不引用用户输入，固定传空，避免 {{input}} 被误替换
        String rendered = ctx.renderer().render(expression, "", ctx.outputs()).trim();
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
