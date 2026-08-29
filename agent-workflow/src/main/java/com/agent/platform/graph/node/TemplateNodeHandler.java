package com.agent.platform.graph.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.graph.NodeType;
import org.springframework.stereotype.Component;

/**
 * 模板节点：对模板内容做 {{input}} / {{节点id}} 变量插值后输出。
 * 常用于拼装 prompt、格式化文本，输出可被下游节点引用。
 */
@Component
public class TemplateNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.TEMPLATE;
    }

    @Override
    public String validate(NodeContext ctx) {
        if (ctx.cfgStr("template") == null) {
            return "模板节点「" + ctx.label() + "」未配置模板内容";
        }
        return null;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        String template = ctx.cfgStr("template");
        if (template == null) {
            throw new BizException("模板节点「" + ctx.label() + "」未配置模板内容");
        }
        String text = ctx.render(template);
        ctx.emit(text);
        return NodeResult.of(text);
    }

    @Override
    public String describeInput(NodeContext ctx) {
        String tpl = ctx.cfgStr("template", "");
        return ctx.render(tpl).trim();
    }
}
