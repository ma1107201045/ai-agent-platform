package com.agent.platform.controller.portal;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.workflow.RunResult;
import com.agent.platform.workflow.WorkflowEngine;
import com.agent.platform.workflow.WorkflowGraph;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.service.app.AppAgentService;
import com.agent.platform.service.app.AppApiKeyService;
import com.agent.platform.service.model.ModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 公开访问接口（无需登录）：
 * 已发布应用对外分享 / 嵌入访问，支持 workflow / agent / chatflow 三种类型
 */
@RestController
@RequestMapping("/api/portal/public")
@RequiredArgsConstructor
public class PortalPublicController {

    private final AppAgentService appAgentService;
    private final AppApiKeyService appApiKeyService;
    private final ModelService modelService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    /** 公开应用信息（仅已发布） */
    @GetMapping("/app-agents/{id}")
    public Result<PublicAppInfo> appInfo(@PathVariable Long id) {
        AppAgent app = requirePublished(id);
        PublicAppInfo info = new PublicAppInfo();
        info.setId(app.getId());
        info.setName(app.getName());
        info.setType(app.getType());
        info.setDescription(app.getDescription());
        info.setWelcomeMessage(app.getWelcomeMessage());
        info.setOpeningQuestions(app.getOpeningQuestions());
        return Result.ok(info);
    }

    /**
     * 公开对话：
     * 请求体 messages 为完整对话历史（最后一条必须是 user 消息，作为本次输入）。
     * workflow → 运行已发布版本 DSL；agent → 自主工具调用；chatflow → 默认模型直连。
     *
     * <p>鉴权策略：默认免鉴权开放（供官网/嵌入分享）；若携带
     * {@code Authorization: Bearer <api-key>} 或 {@code X-API-Key: <api-key>}，
     * 则按「应用 API 密钥」校验（启用 / 未过期 / 限流），无鉴权信息时行为不变。
     */
    @PostMapping("/app-agents/{id}/chat")
    public Result<PublicChatResult> chat(@PathVariable Long id,
                                         @RequestHeader(value = "Authorization", required = false) String authorization,
                                         @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
                                         @RequestBody PublicChatReq req) {
        AppAgent app = requirePublished(id);
        String presentedKey = resolveApiKey(authorization, apiKeyHeader);
        if (presentedKey != null) {
            appApiKeyService.authenticate(app.getId(), presentedKey);
        }
        List<ChatMessage> messages = req.getMessages();
        if (messages == null || messages.isEmpty()) {
            throw new BizException("请输入消息");
        }
        ChatMessage last = messages.get(messages.size() - 1);
        if (!"user".equals(last.role()) || last.content() == null || last.content().isBlank()) {
            throw new BizException("最后一条消息必须是用户输入");
        }
        List<ChatMessage> history = new ArrayList<>(messages.subList(0, messages.size() - 1));

        String answer;
        Object detail = null;
        if ("workflow".equals(app.getType())) {
            try {
                String dsl = appAgentService.getPublishedWorkflow(id);
                WorkflowGraph graph = objectMapper.readValue(dsl, WorkflowGraph.class);
                RunResult result = workflowEngine.run(graph, last.content(), id);
                answer = result.getAnswer();
                detail = result.getTrace();
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException("工作流执行失败: " + e.getMessage());
            }
        } else if ("agent".equals(app.getType())) {
            Long modelId = modelService.defaultChatModelId();
            if (modelId == null) {
                throw new BizException("尚未配置可用的对话模型");
            }
            AppAgentService.AgentResult result = appAgentService.chat(id, modelId, null, history, null);
            answer = result.getAnswer();
            detail = result.getSteps();
        } else {
            // chatflow / direct：默认模型直连
            Long modelId = modelService.defaultChatModelId();
            if (modelId == null) {
                throw new BizException("尚未配置可用的对话模型");
            }
            ChatModel model = modelService.chatModelOf(modelId);
            List<ChatMessage> all = new ArrayList<>(history);
            all.add(last);
            ChatResponse response = model.call(ChatRequest.builder().messages(all).build());
            answer = response == null ? "" : response.getContent();
        }

        PublicChatResult result = new PublicChatResult();
        result.setAnswer(answer);
        result.setDetail(detail);
        return Result.ok(result);
    }

    private AppAgent requirePublished(Long id) {
        AppAgent app = appAgentService.getById(id);
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new BizException("应用未发布，无法对外访问");
        }
        return app;
    }

    /**
     * 从请求头解析 API Key：
     * 优先取 {@code X-API-Key}，其次取 {@code Authorization: Bearer <key>}；均未携带返回 null（视为匿名公开访问）。
     */
    private String resolveApiKey(String authorization, String apiKeyHeader) {
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return apiKeyHeader.trim();
        }
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }

    @Data
    public static class PublicAppInfo {
        private Long id;
        private String name;
        private String type;
        private String description;
        private String welcomeMessage;
        private String openingQuestions;
    }

    @Data
    public static class PublicChatReq {
        private List<ChatMessage> messages;
    }

    @Data
    public static class PublicChatResult {
        private String answer;
        /** workflow → 节点轨迹 Trace[]；agent → 工具步骤 AgentStep[]；chatflow → null */
        private Object detail;
    }
}
