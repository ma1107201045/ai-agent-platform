package com.agent.platform.orchestrator.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.orchestrator.NodeType;
import org.mvel2.MVEL;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码节点：执行 MVEL 表达式脚本。
 * <p>
 * 脚本内可用变量：input（用户输入）、outputs（全部节点输出）、以及各节点 id 对应的输出值。
 * 通过 return 返回结果文本。
 */
@Component
public class CodeNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.CODE;
    }

    @Override
    public List<NodeField> fields() {
        return List.of(
                NodeField.builder().key("code").label("MVEL 表达式").type("code").required(true)
                        .description("可用变量：input（用户输入）、outputs（全部输出）、节点 id（其输出值）；return 返回文本")
                        .build());
    }

    @Override
    public String validate(NodeContext ctx) {
        if (ctx.cfgStr("code") == null) {
            return "代码节点「" + ctx.label() + "」未配置代码";
        }
        return null;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        String code = ctx.cfgStr("code");
        if (code == null) {
            throw new BizException("代码节点「" + ctx.label() + "」未配置代码");
        }
        try {
            Map<String, Object> vars = new HashMap<>(ctx.outputs());
            vars.put("input", ctx.userInput());
            vars.put("outputs", ctx.outputs());
            Object result = MVEL.executeExpression(MVEL.compileExpression(code), vars);
            String text = result == null ? "" : String.valueOf(result);
            ctx.emit(text);
            return NodeResult.of(text);
        } catch (Exception e) {
            throw new BizException("代码节点「" + ctx.label() + "」执行失败: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    @Override
    public String describeInput(NodeContext ctx) {
        return ctx.render(ctx.label()).trim();
    }
}
