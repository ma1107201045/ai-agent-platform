package com.agent.platform.orchestrator.node;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 节点类型描述（对外 Schema）
 * <p>
 * 由引擎侧注册表统一生成，前端据此渲染节点面板与默认配置，
 * 与后端 {@link NodeType} + handler 保持单源一致。
 */
@Data
@Builder
public class NodeTypeSchema {

    /** 节点类型 code（与 DSL type 字段一致，如 llm） */
    private String code;

    /** 默认展示名 */
    private String label;

    /** 是否排他分支节点 */
    private boolean branch;

    /** 是否需要下游连线（start 无入线、end 无出线等展示用） */
    private boolean source;

    /** 是否需要上游连线 */
    private boolean target;

    /** 配置字段 */
    @Builder.Default
    private List<NodeField> fields = List.of();
}
