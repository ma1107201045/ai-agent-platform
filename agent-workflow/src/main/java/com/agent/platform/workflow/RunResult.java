package com.agent.platform.workflow;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 工作流运行结果
 */
@Data
@Builder
public class RunResult {

    /** 最终回答文本 */
    private String answer;

    /** 节点执行轨迹 */
    private List<TraceItem> trace;

    @Data
    @Builder
    public static class TraceItem {
        private String nodeId;
        /** 节点类型；序列化为 {@link NodeType#getCode()} 小写标识 */
        private NodeType nodeType;
        private String label;
        /** 执行状态；序列化为 {@link NodeStatus#getCode()} 小写标识 */
        private NodeStatus status;
        private String input;
        private String output;
        private long costMs;
        private String error;
    }
}
