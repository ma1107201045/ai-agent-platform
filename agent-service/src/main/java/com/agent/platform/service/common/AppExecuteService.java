package com.agent.platform.service.common;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.vo.app.AgentChatVO;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.orchestrator.RunResult;
import com.agent.platform.orchestrator.WorkflowEngine;
import com.agent.platform.orchestrator.WorkflowGraph;
import com.agent.platform.service.app.AppAgentService;
import com.agent.platform.service.model.ModelRuntimeService;
import com.agent.platform.service.model.ModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 应用/模型程序化执行服务（供渠道回调、评测运行、多智能体路由等内部场景复用）。
 *
 * <p>与 {@code PortalPublicController.chat} 语义一致：
 * workflow → 运行已发布版本 DSL；agent → 自主工具调用；chatflow → 默认模型直连。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppExecuteService {

    private final AppAgentService appAgentService;
    private final ModelService modelService;
    private final ModelRuntimeService modelRuntimeService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    /** 执行结果 */
    public record Reply(String answer, Object detail) {
    }

    /**
     * 运行已发布应用（workflow/agent/chatflow）。
     *
     * @param appId   应用ID（须为已发布 status=1）
     * @param content 用户输入
     */
    public Reply runApp(Long appId, String content) {
        AppAgent app = appAgentService.getById(appId);
        if (app == null) {
            throw new BizException("应用不存在: " + appId);
        }
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new BizException("应用未发布，无法执行: " + app.getName());
        }
        if (content == null || content.isBlank()) {
            throw new BizException("请输入消息内容");
        }
        String answer;
        Object detail;
        if ("workflow".equals(app.getType())) {
            try {
                String dsl = appAgentService.getPublishedWorkflow(appId);
                WorkflowGraph graph = objectMapper.readValue(dsl, WorkflowGraph.class);
                RunResult result = workflowEngine.run(graph, content, appId);
                answer = result.getAnswer();
                detail = result.getTrace();
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.warn("工作流执行失败 appId={}", appId, e);
                throw new BizException("工作流执行失败: " + e.getMessage());
            }
        } else if ("agent".equals(app.getType())) {
            Long modelId = modelService.defaultChatModelId();
            if (modelId == null) {
                throw new BizException("尚未配置可用的对话模型");
            }
            AgentChatVO result = appAgentService.chat(appId, modelId, null, List.of(), null);
            answer = result.getAnswer();
            detail = result.getSteps();
        } else {
            Long modelId = modelService.defaultChatModelId();
            if (modelId == null) {
                throw new BizException("尚未配置可用的对话模型");
            }
            ChatModel model = modelRuntimeService.chatModelOf(modelId);
            ChatResponse response = model.call(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(content)))
                    .build());
            answer = response == null ? "" : response.getContent();
            detail = null;
        }
        return new Reply(answer == null ? "" : answer, detail);
    }

    /** 直连模型执行（评测“模型模式”使用） */
    public Reply runModel(Long modelId, String content) {
        if (modelId == null) {
            throw new BizException("请选择被测模型");
        }
        ChatModel model = modelRuntimeService.chatModelOf(modelId);
        ChatResponse response = model.call(ChatRequest.builder()
                .messages(List.of(ChatMessage.user(content == null ? "" : content)))
                .build());
        String answer = response == null ? "" : response.getContent();
        return new Reply(answer == null ? "" : answer, null);
    }
}
