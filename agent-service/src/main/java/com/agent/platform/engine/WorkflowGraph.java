package com.agent.platform.engine;

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
        /** start / end / llm / agent / condition / code / http / template / knowledge */
        private String type;
        private String label;
        private Map<String, Object> config;
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
