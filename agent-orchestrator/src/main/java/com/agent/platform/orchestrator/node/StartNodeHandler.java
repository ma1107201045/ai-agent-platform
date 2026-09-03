package com.agent.platform.orchestrator.node;

import com.agent.platform.orchestrator.NodeType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 开始节点：流程入口，负责初始化流程变量。
 * <p>
 * 支持配置项：
 * <ul>
 *   <li>{@code variables} 流程变量（JSON 对象，值支持 {{input}} 变量替换），
 *       渲染后写入全局输出表，下游可用 {{变量名}} 引用</li>
 *   <li>{@code welcome} 开场白（仅前端展示使用）</li>
 * </ul>
 */
@Component
public class StartNodeHandler implements NodeHandler {

    @Override
    public NodeType type() {
        return NodeType.START;
    }

    @Override
    public List<NodeField> fields() {
        return List.of(
                NodeField.json("variables", "流程变量").description("键值对；值支持 {{input}} / {{节点id}} 替换，渲染后下游用 {{变量名}} 引用"),
                NodeField.text("welcome", "开场白").description("仅前端聊天页展示使用"));
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        Object vars = ctx.config().get("variables");
        if (vars instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = e.getKey() == null ? null : String.valueOf(e.getKey()).trim();
                String value = e.getValue() == null ? "" : String.valueOf(e.getValue());
                ctx.putVar(key, ctx.render(value));
            }
        }
        return NodeResult.empty();
    }
}
