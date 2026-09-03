package com.agent.platform.orchestrator;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流运行结果
 * <p>
 * 设计原则：{@code answer} 永远是对用户友好的内容；技术性失败信息放 {@code error / errorCode}，
 * 由调用方结合 {@link RunStatus} 决定如何呈现与记录。节点执行细节见 {@link #trace}。
 */
@Data
@Builder
public class RunResult {

    /** 一次运行的唯一标识（可关联运行记录 / 日志 / 取消） */
    private String runId;

    /** 所属应用 ID（可空） */
    private Long appId;

    /** 整体运行状态 */
    @Builder.Default
    private RunStatus status = RunStatus.SUCCESS;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 总耗时（毫秒） */
    private Long costMs;

    /** 技术性错误描述（失败/超时时才有；面向排查与记录，不直接呈现给最终用户） */
    private String error;

    /** 错误码（预留，可按错误类型细分） */
    private String errorCode;

    /** 最终回答文本（对用户友好） */
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
