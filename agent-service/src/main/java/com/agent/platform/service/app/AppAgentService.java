package com.agent.platform.service.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppAgentTool;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.model.ToolCall;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.service.knowledge.KnowledgeService;
import com.agent.platform.service.model.ModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 自主智能体服务：规划-工具调用-观察的循环执行
 */
@Service
@RequiredArgsConstructor
public class AppAgentService {

    private static final int DEFAULT_MAX_ITERATIONS = 6;

    private final AppService appService;
    private final ModelService modelService;
    private final AppToolService toolService;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    /**
     * 执行 Agent 循环（应用级入口：工具与知识库来源为应用绑定配置）
     *
     * @param appId        智能体应用 ID
     * @param modelId      对话模型 ID
     * @param systemPrompt 系统提示词（可为 null，使用默认）
     * @param history      历史消息（不含 system）
     * @param maxIterations 最大循环轮数
     */
    public AgentResult chat(Long appId, Long modelId, String systemPrompt,
                            List<ChatMessage> history, Integer maxIterations) {
        AppAgent app = appService.getById(appId);
        return doChat(modelId, systemPrompt, loadTools(app.getToolIds()),
                app.getDatasetIds(), history, maxIterations);
    }

    /**
     * 执行 Agent 循环（工作流 agent 节点入口：工具与知识库显式指定，
     * 未配置时由调用方回退到应用绑定配置）
     *
     * @param modelId        对话模型 ID
     * @param systemPrompt   系统提示词（可为 null，使用默认）
     * @param tools          本次循环可用的工具列表（可为空）
     * @param datasetIdsJson 知识库数据集 ID JSON 数组（可为 null）
     * @param history        历史消息（不含 system）
     * @param maxIterations  最大循环轮数
     */
    public AgentResult chat(Long modelId, String systemPrompt, List<AppAgentTool> tools,
                            String datasetIdsJson, List<ChatMessage> history, Integer maxIterations) {
        return doChat(modelId, systemPrompt, tools, datasetIdsJson, history, maxIterations);
    }

    /** Agent 循环核心实现 */
    private AgentResult doChat(Long modelId, String systemPrompt, List<AppAgentTool> tools,
                               String datasetIdsJson, List<ChatMessage> history, Integer maxIterations) {
        int maxIter = maxIterations == null || maxIterations <= 0 ? DEFAULT_MAX_ITERATIONS : maxIterations;

        ChatModel chatModel = modelService.chatModelOf(modelId);

        // 组装消息
        String finalSystemPrompt = systemPrompt == null || systemPrompt.isBlank()
                ? "你是一个智能助手。请根据对话内容判断是否需要调用工具来完成任务，如果工具结果对回答有帮助，请结合工具结果作答。"
                : systemPrompt;
        String kbContext = buildKnowledgeContext(datasetIdsJson, lastUserInput(history));
        if (!kbContext.isBlank()) {
            finalSystemPrompt += "\n\n" + kbContext;
        }
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(finalSystemPrompt));
        if (history != null) {
            for (ChatMessage m : history) {
                if (m.role() != null && !"system".equals(m.role())
                        && m.content() != null && !m.content().isBlank()) {
                    messages.add(m);
                }
            }
        }

        List<AgentStep> steps = new ArrayList<>();
        String answer = null;
        for (int i = 0; i < maxIter; i++) {
            ChatRequest.ChatRequestBuilder builder = ChatRequest.builder().messages(messages);
            if (!tools.isEmpty()) {
                builder.tools(toolService.toFunctionTools(tools));
            }
            ChatResponse response = chatModel.call(builder.build());
            List<ToolCall> toolCalls = response == null ? null : response.getToolCalls();

            // 无工具调用 → 对话结束
            if (toolCalls == null || toolCalls.isEmpty()) {
                answer = response == null ? "" : response.getContent();
                break;
            }

            // 记录 assistant 工具调用消息并回填工具结果
            messages.add(ChatMessage.assistantToolCalls(response.getContent(), toolCalls));
            for (ToolCall tc : toolCalls) {
                long start = System.currentTimeMillis();
                String result;
                try {
                    AppAgentTool tool = tools.stream()
                            .filter(t -> t.getName().equals(tc.name()))
                            .findFirst()
                            .orElse(null);
                    if (tool == null) {
                        result = "工具不存在: " + tc.name();
                    } else {
                        result = toolService.execute(tool, tc.arguments());
                    }
                } catch (Exception e) {
                    result = "工具执行异常: " + e.getMessage();
                }
                long cost = System.currentTimeMillis() - start;
                steps.add(new AgentStep(tc.name(), tc.arguments(), result, cost));
                messages.add(ChatMessage.tool(tc.id(), result));
            }
        }
        if (answer == null) {
            answer = "已达到最大迭代次数（" + maxIter + "），请调整问题或检查工具配置。";
        }
        return new AgentResult(answer, steps);
    }

    /** 取历史中最后一条用户消息作为知识库检索查询 */
    private String lastUserInput(List<ChatMessage> history) {
        if (history == null) {
            return "";
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if (m.role() != null && "user".equals(m.role())
                    && m.content() != null && !m.content().isBlank()) {
                return m.content();
            }
        }
        return "";
    }

    /** 检索应用绑定的知识库数据集，组装参考资料上下文 */
    private String buildKnowledgeContext(String datasetIdsJson, String query) {
        if (datasetIdsJson == null || datasetIdsJson.isBlank() || query == null || query.isBlank()) {
            return "";
        }
        try {
            List<Long> ids = objectMapper.readValue(datasetIdsJson, new TypeReference<>() {
            });
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (Long id : ids) {
                try {
                    List<KnowledgeService.SearchHit> hits = knowledgeService.search(id, query, 3, null);
                    for (KnowledgeService.SearchHit hit : hits) {
                        sb.append("[").append(idx++).append("] ").append(hit.getContent()).append("\n\n");
                    }
                } catch (Exception ignore) {
                    // 单个数据集检索失败不影响整体
                }
            }
            if (idx == 1) {
                return "";
            }
            return "以下是与用户问题相关的知识库参考资料，请优先依据资料作答：\n\n" + sb;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 解析工具 ID JSON 数组并加载启用的工具（应用绑定或节点级配置均可）
     */
    public List<AppAgentTool> loadTools(String toolIdsJson) {
        if (toolIdsJson == null || toolIdsJson.isBlank() || "null".equals(toolIdsJson.trim())) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(toolIdsJson, new TypeReference<List<Long>>() {
            });
            List<AppAgentTool> result = new ArrayList<>();
            if (ids != null) {
                for (Long id : ids) {
                    AppAgentTool tool = toolService.getById(id);
                    if (tool.getStatus() != null && tool.getStatus() == 1) {
                        result.add(tool);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new BizException("工具配置解析失败: " + e.getMessage());
        }
    }

    /** Agent 执行结果 */
    @Data
    @AllArgsConstructor
    public static class AgentResult {
        /** 最终回答 */
        private String answer;
        /** 工具调用步骤 */
        private List<AgentStep> steps;
    }

    @Data
    @AllArgsConstructor
    public static class AgentStep {
        private String toolName;
        private String arguments;
        private String result;
        private long costMs;
    }
}
