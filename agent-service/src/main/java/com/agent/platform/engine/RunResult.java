package com.agent.platform.engine;

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
        private String nodeType;
        private String label;
        /** success / skipped / error */
        private String status;
        private String input;
        private String output;
        private long costMs;
        private String error;
    }
}
