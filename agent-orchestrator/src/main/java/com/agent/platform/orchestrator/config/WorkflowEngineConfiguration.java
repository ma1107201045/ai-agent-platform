package com.agent.platform.orchestrator.config;

import com.agent.platform.orchestrator.VariableRenderer;
import com.agent.platform.orchestrator.WorkflowEngine;
import com.agent.platform.orchestrator.WorkflowEventListener;
import com.agent.platform.orchestrator.WorkflowSettings;
import com.agent.platform.orchestrator.node.NodeHandlerRegistry;
import com.agent.platform.orchestrator.spi.AgentRunner;
import com.agent.platform.orchestrator.spi.KnowledgeProvider;
import com.agent.platform.orchestrator.spi.ModelProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 工作流引擎装配层（Spring 侧唯一入口）
 * <p>
 * 引擎核心 {@link WorkflowEngine} 本身不持有 Spring 注解、不硬编码运行参数：
 * <ul>
 *   <li>运行参数由本类读取 {@code workflow.*} 配置并构建 {@link WorkflowSettings}；</li>
 *   <li>线程池生命周期由 {@code destroyMethod = "shutdown"} 统一管理（容器关闭时优雅释放）；</li>
 *   <li>业务 SPI（模型 / 知识库 / Agent 执行器）与节点注册表、渲染器均由容器注入。</li>
 * </ul>
 * 配置示例（application.yml）：
 * <pre>
 * workflow:
 *   parallelism: 4                # DAG 并行线程数
 *   run-timeout-seconds: 300      # 整体兜底超时
 *   default-node-timeout-seconds: 0
 *   trace-limit: 300
 *   retry-backoff-base-ms: 300
 * </pre>
 */
@Configuration
public class WorkflowEngineConfiguration {

    @Bean
    public WorkflowSettings workflowSettings(
            @Value("${workflow.parallelism:4}") int parallelism,
            @Value("${workflow.run-timeout-seconds:300}") long runTimeoutSeconds,
            @Value("${workflow.default-node-timeout-seconds:0}") int defaultNodeTimeoutSeconds,
            @Value("${workflow.trace-limit:300}") int traceLimit,
            @Value("${workflow.retry-backoff-base-ms:300}") long retryBackoffBaseMs) {
        return new WorkflowSettings(parallelism, runTimeoutSeconds,
                defaultNodeTimeoutSeconds, traceLimit, retryBackoffBaseMs);
    }

    @Bean(destroyMethod = "shutdown")
    public WorkflowEngine workflowEngine(WorkflowSettings workflowSettings,
                                         NodeHandlerRegistry nodeHandlerRegistry,
                                         VariableRenderer variableRenderer,
                                         ModelProvider modelProvider,
                                         KnowledgeProvider knowledgeProvider,
                                         AgentRunner agentRunner,
                                         List<WorkflowEventListener> eventListeners) {
        return new WorkflowEngine(workflowSettings, nodeHandlerRegistry, variableRenderer,
                modelProvider, knowledgeProvider, agentRunner, eventListeners);
    }
}
