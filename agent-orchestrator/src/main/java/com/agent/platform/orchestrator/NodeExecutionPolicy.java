package com.agent.platform.orchestrator;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.orchestrator.node.NodeContext;
import com.agent.platform.orchestrator.node.NodeField;
import com.agent.platform.orchestrator.node.NodeHandler;
import com.agent.platform.orchestrator.node.NodeResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 节点执行策略器（纯 Java，可单测）
 * <p>
 * 负责一次节点调用的完整策略语义：
 * <ul>
 *   <li>执行前配置校验（{@code validate}），配置问题不参与重试；</li>
 *   <li>失败重试（退避 = base * (attempt + 1)）；</li>
 *   <li>单节点超时控制（配置了超时的任务走弹性池，避免占满固定调度池）；</li>
 *   <li>产出统一的 {@link NodeOutcome}（状态 / 输出 / 分支 handle / 错误 / 耗时）。</li>
 * </ul>
 * 引擎只负责依据 {@code onError} 策略决定下游释放行为，错误处理的具体调用语义在本类收敛。
 */
public class NodeExecutionPolicy {

    /** 配置类错误：与运行时错误区分，不参与重试 */
    static class ConfigException extends RuntimeException {
        ConfigException(String message) {
            super(message);
        }
    }

    private final ExecutorService timeoutExecutor;
    private final long retryBackoffBaseMs;

    public NodeExecutionPolicy(ExecutorService timeoutExecutor, long retryBackoffBaseMs) {
        this.timeoutExecutor = timeoutExecutor;
        this.retryBackoffBaseMs = Math.max(1, retryBackoffBaseMs);
    }

    /**
     * 执行节点直至成功或用尽重试。
     *
     * @param handler        节点处理器
     * @param ctx            执行上下文
     * @param retries        失败重试次数（0 表示不重试）
     * @param timeoutSeconds 单节点超时（秒）；&lt;=0 表示不限制
     */
    public NodeOutcome run(NodeHandler handler, NodeContext ctx, int retries, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        String output = null;
        String selectedHandle = null;
        String error = null;
        NodeStatus status = NodeStatus.SUCCESS;

        // schema 必填校验（单源：handler 声明的 NodeField.required）
        String missing = requiredFieldMissing(handler, ctx);
        if (missing != null) {
            return new NodeOutcome(NodeStatus.ERROR, null, null, missing, System.currentTimeMillis() - start);
        }

        for (int attempt = 0; ; attempt++) {
            try {
                // 执行前配置校验（扩展点，未实现时无开销）；配置问题重试无意义，直接失败
                String invalid = handler.validate(ctx);
                if (invalid != null && !invalid.isBlank()) {
                    throw new ConfigException(invalid);
                }
                NodeResult result = invoke(handler, ctx, timeoutSeconds);
                if (result != null) {
                    output = result.getOutput();
                    selectedHandle = result.getSelectedHandle();
                }
                status = NodeStatus.SUCCESS;
                error = null;
                break;
            } catch (ConfigException e) {
                status = NodeStatus.ERROR;
                error = e.getMessage();
                break;
            } catch (Exception e) {
                status = NodeStatus.ERROR;
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (attempt >= retries || Thread.currentThread().isInterrupted()) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }
        return new NodeOutcome(status, output, selectedHandle, error, System.currentTimeMillis() - start);
    }

    /** 调用处理器，必要时施加超时控制 */
    private NodeResult invoke(NodeHandler handler, NodeContext ctx, int timeoutSeconds) throws Exception {
        if (timeoutSeconds <= 0) {
            return handler.execute(ctx);
        }
        Future<NodeResult> future = timeoutExecutor.submit(() -> handler.execute(ctx));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BizException("节点「" + ctx.label() + "」执行超时（" + timeoutSeconds + "s）");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new BizException(String.valueOf(cause == null ? "未知错误" : cause.getMessage()));
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(retryBackoffBaseMs * (attempt + 1));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 检查 handler schema 声明的必填字段是否齐全；缺失时返回错误信息 */
    private String requiredFieldMissing(NodeHandler handler, NodeContext ctx) {
        for (NodeField f : handler.fields()) {
            if (!f.isRequired()) {
                continue;
            }
            Object v = ctx.config().get(f.getKey());
            if (v == null) {
                return "节点「" + ctx.label() + "」配置项「" + f.getLabel() + "」为必填";
            }
            if (v instanceof String s && (s.isBlank() || "null".equalsIgnoreCase(s))) {
                return "节点「" + ctx.label() + "」配置项「" + f.getLabel() + "」为必填";
            }
        }
        return null;
    }

    /** 节点执行结果（引擎据此决定 trace 记录与下游释放语义） */
    @Getter
    @AllArgsConstructor
    public static class NodeOutcome {
        /** SUCCESS / ERROR */
        private final NodeStatus status;
        /** 成功时节点输出文本（可为 null） */
        private final String output;
        /** 排他分支选中的出边 handle（null 表示非排他） */
        private final String selectedHandle;
        /** 失败时的错误描述 */
        private final String error;
        /** 总耗时（含重试与退避） */
        private final long costMs;
    }
}
