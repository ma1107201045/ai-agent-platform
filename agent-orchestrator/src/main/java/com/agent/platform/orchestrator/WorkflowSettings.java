package com.agent.platform.orchestrator;

/**
 * 工作流引擎运行配置（外部化，见 application.yml 的 {@code workflow.*} 前缀）。
 * <p>
 * 引擎不硬编码常量，全部运行参数由装配层注入，便于部署时按实例调节与单测注入。
 *
 * @param parallelism             DAG 并行执行线程数
 * @param runTimeoutSeconds       整体兜底超时（秒）；0 表示不限制
 * @param defaultNodeTimeoutSeconds 单节点默认超时（秒）；0 表示不限制
 * @param traceLimit              轨迹中输入/输出文本截断长度
 * @param retryBackoffBaseMs      节点失败重试的退避基数（毫秒），实际退避 = base * (attempt + 1)
 */
public record WorkflowSettings(
        int parallelism,
        long runTimeoutSeconds,
        int defaultNodeTimeoutSeconds,
        int traceLimit,
        long retryBackoffBaseMs) {

    public WorkflowSettings {
        if (parallelism <= 0) {
            parallelism = 4;
        }
        if (runTimeoutSeconds < 0) {
            runTimeoutSeconds = 300;
        }
        if (defaultNodeTimeoutSeconds < 0) {
            defaultNodeTimeoutSeconds = 0;
        }
        if (traceLimit <= 0) {
            traceLimit = 300;
        }
        if (retryBackoffBaseMs < 0) {
            retryBackoffBaseMs = 300;
        }
    }

    /** 引擎默认配置（供纯测试 / 非 Spring 环境使用） */
    public static WorkflowSettings defaults() {
        return new WorkflowSettings(4, 300, 0, 300, 300);
    }
}
