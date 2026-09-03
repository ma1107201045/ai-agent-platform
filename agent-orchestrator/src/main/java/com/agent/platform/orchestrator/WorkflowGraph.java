package com.agent.platform.orchestrator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流 DSL 图模型（与前端画布 JSON 结构一致）
 */
@Data
public class WorkflowGraph {

    private List<WorkflowNode> nodes;
    private List<WorkflowEdge> edges;

    @Data
    public static class WorkflowNode {
        private String id;
        /** 节点类型标识，取值见 {@link NodeType#getCode()}（保持字符串以兼容前端 DSL） */
        private String type;
        private String label;
        private Map<String, Object> config;

        /**
         * 节点类型枚举视图；未知类型返回 null。
         * 引擎内部请用本方法做类型判断，避免字符串比较。
         */
        @JsonIgnore
        public NodeType nodeType() {
            return NodeType.fromCode(type);
        }
    }

    @Data
    public static class WorkflowEdge {
        private String id;
        private String source;
        private String target;
        private String sourceHandle;
        private String targetHandle;
        /** 连线文字标注（可选，引擎不参与执行，仅展示与持久化） */
        private String label;
    }
}
