package com.agent.platform.orchestrator.node;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.orchestrator.NodeType;
import com.agent.platform.orchestrator.WorkflowGraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 节点处理器注册表
 * <p>
 * 启动时自动收集容器中所有 {@link NodeHandler} bean，按 {@link NodeHandler#type()}（{@link NodeType}）建立索引。
 * 支持运行时通过 {@link #register(NodeHandler)} 动态注册，便于接入外部插件。
 */
@Slf4j
@Component
public class NodeHandlerRegistry {

    private final Map<NodeType, NodeHandler> handlers = new EnumMap<>(NodeType.class);

    public NodeHandlerRegistry(List<NodeHandler> beans) {
        if (beans != null) {
            beans.forEach(this::register);
        }
        log.info("工作流节点处理器注册完成，共 {}/{} 种类型: {}",
                handlers.size(), NodeType.values().length, types());
    }

    /**
     * 注册处理器。重复注册同一类型时，后者覆盖前者（便于插件改写内置节点行为）。
     */
    public void register(NodeHandler handler) {
        if (handler == null || handler.type() == null) {
            return;
        }
        NodeHandler prev = handlers.put(handler.type(), handler);
        if (prev != null && prev.getClass() != handler.getClass()) {
            log.warn("节点类型 [{}] 被覆盖: {} -> {}", handler.type().getCode(),
                    prev.getClass().getSimpleName(), handler.getClass().getSimpleName());
        }
    }

    /** 按类型查找，未注册时返回 null */
    public NodeHandler get(NodeType type) {
        return type == null ? null : handlers.get(type);
    }

    /** 按类型查找，未注册时抛出业务异常 */
    public NodeHandler required(NodeType type) {
        NodeHandler h = get(type);
        if (h == null) {
            throw new BizException("不支持的节点类型: "
                    + (type == null ? "空" : type.getCode()) + "，已注册类型: " + types());
        }
        return h;
    }

    /** 已注册的全部节点类型 */
    public Collection<NodeType> types() {
        return Collections.unmodifiableCollection(handlers.keySet());
    }

    /** 已注册的全部处理器（快照） */
    public Collection<NodeHandler> all() {
        return List.copyOf(handlers.values());
    }

    /**
     * 按处理器声明的 schema 补齐节点配置默认值（不覆盖已存在的配置值）。
     * <p>
     * 「默认配置」由 handler {@link #fields()} 单源生成，前端新建节点与引擎运行时行为天然一致。
     */
    public WorkflowGraph.WorkflowNode applyDefaults(WorkflowGraph.WorkflowNode node) {
        if (node == null) {
            return node;
        }
        NodeHandler h = handlers.get(node.nodeType());
        if (h == null) {
            return node;
        }
        Map<String, Object> cfg = node.getConfig() == null
                ? new java.util.HashMap<>()
                : new java.util.HashMap<>(node.getConfig());
        for (NodeField f : h.fields()) {
            if (f.getDefaultValue() != null && !cfg.containsKey(f.getKey())) {
                cfg.put(f.getKey(), f.getDefaultValue());
            }
        }
        node.setConfig(cfg);
        return node;
    }

    /**
     * 节点类型 Schema 列表（按 NodeType 枚举顺序稳定输出）。
     * 前端节点面板 / 配置表单据此渲染，单一事实来源。
     */
    public List<NodeTypeSchema> schemas() {
        List<NodeTypeSchema> list = new java.util.ArrayList<>();
        for (NodeType t : NodeType.values()) {
            NodeHandler h = handlers.get(t);
            if (h == null) {
                continue;
            }
            list.add(NodeTypeSchema.builder()
                    .code(t.getCode())
                    .label(t.getLabel())
                    .branch(t.isBranch())
                    .source(t != NodeType.END)
                    .target(t != NodeType.START)
                    .fields(h.fields())
                    .build());
        }
        return list;
    }
}
