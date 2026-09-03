package com.agent.platform.orchestrator;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.orchestrator.WorkflowGraph.WorkflowNode;
import com.agent.platform.orchestrator.node.NodeContext;
import com.agent.platform.orchestrator.node.NodeHandler;
import com.agent.platform.orchestrator.node.NodeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeExecutionPolicyTest {

    private static final ExecutorService ELASTIC = Executors.newCachedThreadPool();
    private static final NodeExecutionPolicy POLICY = new NodeExecutionPolicy(ELASTIC, 5);

    private NodeContext ctx(Map<String, Object> config) {
        WorkflowNode n = WorkflowDsl.node("n1", NodeType.LLM, config);
        return new NodeContext(n, null, "hi", new ConcurrentHashMap<>(),
                new VariableRenderer(), null, null, null, new ObjectMapper());
    }

    @Test
    void success_returnsOutputAndSelected() {
        NodeHandler h = handler(ctx -> {
            ctx.emit("ok");
            return NodeResult.of("ok", "true");
        });
        NodeExecutionPolicy.NodeOutcome out = POLICY.run(h, ctx(Map.of()), 0, 0);
        assertEquals(NodeStatus.SUCCESS, out.getStatus());
        assertEquals("ok", out.getOutput());
        assertEquals("true", out.getSelectedHandle());
        assertNull(out.getError());
    }

    @Test
    void validateFailure_doesNotRetry() {
        int[] calls = {0};
        NodeHandler h = new NodeHandler() {
            @Override
            public NodeType type() {
                return NodeType.LLM;
            }

            @Override
            public String validate(NodeContext c) {
                calls[0]++;
                return "配置缺失";
            }

            @Override
            public NodeResult execute(NodeContext c) {
                calls[0]++;
                return NodeResult.empty();
            }
        };
        NodeExecutionPolicy.NodeOutcome out = POLICY.run(h, ctx(Map.of()), 5, 0);
        assertEquals(NodeStatus.ERROR, out.getStatus());
        assertEquals("配置缺失", out.getError());
        assertEquals(1, calls[0]); // 配置错误不参与重试
    }

    @Test
    void exception_retriesThenSucceeds() {
        int[] attempts = {0};
        NodeHandler h = handler(c -> {
            attempts[0]++;
            if (attempts[0] < 3) {
                throw new IllegalStateException("transient");
            }
            return NodeResult.of("done");
        });
        NodeExecutionPolicy.NodeOutcome out = POLICY.run(h, ctx(Map.of()), 3, 0);
        assertEquals(NodeStatus.SUCCESS, out.getStatus());
        assertEquals("done", out.getOutput());
        assertEquals(3, attempts[0]);
    }

    @Test
    void exception_retriesExhausted_fails() {
        int[] attempts = {0};
        NodeHandler h = handler(c -> {
            attempts[0]++;
            throw new BizException("boom");
        });
        NodeExecutionPolicy.NodeOutcome out = POLICY.run(h, ctx(Map.of()), 2, 0);
        assertEquals(NodeStatus.ERROR, out.getStatus());
        assertEquals("boom", out.getError());
        assertEquals(3, attempts[0]); // 首次 + 2 次重试
    }

    @Test
    void timeout_failsWithTimeoutMessage() {
        NodeHandler h = handler(c -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return NodeResult.of("late");
        });
        long start = System.currentTimeMillis();
        NodeExecutionPolicy.NodeOutcome out = POLICY.run(h, ctx(Map.of()), 0, 1);
        assertTrue(System.currentTimeMillis() - start < 2_500, "超时应快速返回");
        assertEquals(NodeStatus.ERROR, out.getStatus());
        assertTrue(out.getError().contains("执行超时"));
    }

    // ---------- helpers ----------

    private NodeHandler handler(java.util.function.Function<NodeContext, NodeResult> fn) {
        return new NodeHandler() {
            @Override
            public NodeType type() {
                return NodeType.LLM;
            }

            @Override
            public NodeResult execute(NodeContext c) {
                return fn.apply(c);
            }
        };
    }
}
