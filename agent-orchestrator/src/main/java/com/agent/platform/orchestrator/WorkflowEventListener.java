package com.agent.platform.orchestrator;

import java.time.LocalDateTime;

/**
 * 工作流执行生命周期监听器（事件扩展点）
 * <p>
 * 引擎只负责发事件、不感知消费者，因此可实现为：
 * <ul>
 *   <li>运行记录持久化（agent_run 表）；</li>
 *   <li>聊天 SSE 实时进度推送（workflow 流式化基础）；</li>
 *   <li>埋点 / 计量 / 监控告警。</li>
 * </ul>
 * 各方法默认空实现，新增事件类型不影响已有实现。
 * 注意：事件在节点线程同步派发，实现类应快速返回、自行捕获异常（引擎已隔离实现抛错影响主流程）。
 */
public interface WorkflowEventListener {

    /** 一次 run 开始（runId 已分配） */
    default void onFlowStarted(FlowStarted e) {
    }

    /** 某节点进入执行（运行中） */
    default void onNodeStarted(NodeStarted e) {
    }

    /** 某节点结束（SUCCESS / ERROR / SKIPPED 等终态） */
    default void onNodeFinished(NodeFinished e) {
    }

    /** 整场运行结束（携带完整 RunResult，可持久化 / 推送最终消息） */
    default void onFlowFinished(FlowFinished e) {
    }

    // ==================== 事件对象 ====================

    record FlowStarted(String runId, Long appId, String userInput, LocalDateTime startedAt) {
    }

    record NodeStarted(String runId, Long appId, String nodeId, String label, NodeType nodeType) {
    }

    record NodeFinished(String runId, Long appId, RunResult.TraceItem traceItem) {
    }

    record FlowFinished(RunResult result) {
    }
}
