package com.agent.platform.orchestrator.node;

import com.agent.platform.orchestrator.NodeType;

import java.util.List;

/**
 * 节点处理器 SPI（扩展点）
 * <p>
 * 新增一种节点类型只需：
 * <ol>
 *   <li>在 {@link NodeType} 中增加枚举值</li>
 *   <li>实现本接口，{@link #type()} 返回对应枚举</li>
 *   <li>实现 {@link #fields()} 声明配置字段（前端自动渲染 + 默认值）</li>
 *   <li>交给 Spring 管理（{@code @Component}）</li>
 * </ol>
 * {@link NodeHandlerRegistry} 会自动收集并注册，无需改动 {@code WorkflowEngine}。
 */
public interface NodeHandler {

    /**
     * 本处理器负责的节点类型。
     */
    NodeType type();

    /**
     * 执行节点。
     *
     * @param ctx 执行上下文（节点定义、用户输入、上游输出、渲染器、业务服务）
     * @return 执行结果；返回 null 等价于 {@link NodeResult#empty()}
     */
    NodeResult execute(NodeContext ctx);

    /**
     * 执行前的配置校验（扩展点，可选实现）。
     *
     * @return 错误描述；null / 空表示校验通过
     */
    default String validate(NodeContext ctx) {
        return null;
    }

    /**
     * 执行轨迹中展示的「输入摘要」（扩展点，可选实现）。
     * 在节点执行完成后调用，可读取渲染后的最终参数。
     *
     * @return 摘要文本；null 表示不展示
     */
    default String describeInput(NodeContext ctx) {
        return null;
    }

    /**
     * 节点配置字段描述（节点 Schema 单源化，可选实现）。
     * <p>
     * 返回的字段描述用于：前端自动渲染配置表单 / 生成默认配置 / 后端必填校验。
     * 未配置字段的处理器等价于无配置项（如 start 无 variables 时），返回空列表即可。
     */
    default List<NodeField> fields() {
        return List.of();
    }
}
