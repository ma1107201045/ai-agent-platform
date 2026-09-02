package com.agent.platform.service.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppAgentTool;
import com.agent.platform.dao.entity.app.AppAgentVersion;
import com.agent.platform.dao.entity.chat.ChatConversation;
import com.agent.platform.dao.entity.chat.ChatUsage;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.app.AppAgentVersionMapper;
import com.agent.platform.dao.mapper.chat.ChatConversationMapper;
import com.agent.platform.dao.mapper.chat.ChatMessageMapper;
import com.agent.platform.dao.mapper.chat.ChatUsageMapper;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.llm.model.ChatRequest;
import com.agent.platform.llm.model.ChatResponse;
import com.agent.platform.llm.model.ToolCall;
import com.agent.platform.llm.spi.ChatModel;
import com.agent.platform.service.knowledge.KnowledgeService;
import com.agent.platform.service.model.ModelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能体应用服务：应用生命周期 + 版本发布 + 工作流 DSL + Agent 自主执行
 * <p>
 * 命名遵循「表名 → 实体 → Mapper → Service → Controller → URL」对齐规则：
 * <pre>
 *   表 app_agent          → 实体 AppAgent          → Mapper AppAgentMapper          → 本类 AppAgentService
 *   表 app_agent_version  → 实体 AppAgentVersion   → Mapper AppAgentVersionMapper
 * </pre>
 * 对应 Controller 为 AppAgentController，URL 前缀为 /api/app-agents（kebab-case 复数）。
 * 依赖注入变量统一取「类名首字母小写」（appAgentMapper / appAgentVersionMapper / ...）。
 * <p>
 * 职责分区：
 * <ul>
 *   <li>应用 CRUD：分页查询、详情、创建、更新、级联删除</li>
 *   <li>版本与发布：发布快照、当前发布版本、版本列表、版本回滚</li>
 *   <li>工作流 DSL：公开访问取已发布版本，运行取草稿优先</li>
 *   <li>Agent 执行：规划-工具调用-观察的 ReAct 循环</li>
 * </ul>
 * 注意：本类注入的是 DAO 层 Mapper（而非 ChatConversationService），
 * 避免与「会话服务依赖本服务」形成构造器循环依赖。
 */
@Service
@RequiredArgsConstructor
public class AppAgentService {

    /** Agent 循环默认最大迭代轮数 */
    private static final int DEFAULT_MAX_ITERATIONS = 6;

    private final AppAgentMapper appAgentMapper;
    private final AppAgentVersionMapper appAgentVersionMapper;
    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatUsageMapper chatUsageMapper;
    private final ModelService modelService;
    private final AppAgentToolService appToolService;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    // ==================== 应用 CRUD ====================

    /**
     * 分页查询应用，支持名称模糊搜索与类型过滤
     */
    public Page<AppAgent> page(long page, long size, String keyword, String type) {
        LambdaQueryWrapper<AppAgent> qw = new LambdaQueryWrapper<AppAgent>()
                .orderByDesc(AppAgent::getId);
        if (keyword != null && !keyword.isBlank()) {
            qw.like(AppAgent::getName, keyword.trim());
        }
        if (type != null && !type.isBlank()) {
            qw.eq(AppAgent::getType, type);
        }
        return appAgentMapper.selectPage(new Page<>(page, size), qw);
    }

    public AppAgent getById(Long id) {
        AppAgent app = appAgentMapper.selectById(id);
        if (app == null) {
            throw new BizException("应用不存在: " + id);
        }
        return app;
    }

    public AppAgent create(AppAgent app) {
        LocalDateTime now = LocalDateTime.now();
        app.setId(null);
        app.setStatus(0); // 草稿
        app.setCreateTime(now);
        app.setUpdateTime(now);
        if (app.getType() == null) {
            app.setType("chatflow");
        }
        if (app.getTenantId() == null) {
            app.setTenantId(1L);
        }
        appAgentMapper.insert(app);
        return app;
    }

    /**
     * 更新应用：仅允许更新业务字段，防止 status / publishedVersionId 等敏感字段被覆盖
     */
    public void update(AppAgent app) {
        getById(app.getId());
        LambdaUpdateWrapper<AppAgent> uw = new LambdaUpdateWrapper<AppAgent>()
                .eq(AppAgent::getId, app.getId())
                .set(AppAgent::getUpdateTime, LocalDateTime.now());
        if (app.getName() != null) uw.set(AppAgent::getName, app.getName());
        if (app.getDescription() != null) uw.set(AppAgent::getDescription, app.getDescription());
        if (app.getType() != null) uw.set(AppAgent::getType, app.getType());
        if (app.getIcon() != null) uw.set(AppAgent::getIcon, app.getIcon());
        if (app.getWelcomeMessage() != null) uw.set(AppAgent::getWelcomeMessage, app.getWelcomeMessage());
        if (app.getOpeningQuestions() != null) uw.set(AppAgent::getOpeningQuestions, app.getOpeningQuestions());
        if (app.getWorkflowJson() != null) uw.set(AppAgent::getWorkflowJson, app.getWorkflowJson());
        if (app.getToolIds() != null) uw.set(AppAgent::getToolIds, app.getToolIds());
        if (app.getDatasetIds() != null) uw.set(AppAgent::getDatasetIds, app.getDatasetIds());
        appAgentMapper.update(null, uw);
    }

    /**
     * 删除应用：级联清理发布版本、会话与消息，避免脏数据残留
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        List<Long> convIds = chatConversationMapper.selectList(
                        new LambdaQueryWrapper<ChatConversation>()
                                .select(ChatConversation::getId)
                                .eq(ChatConversation::getAppId, id))
                .stream()
                .map(ChatConversation::getId)
                .toList();
        if (!convIds.isEmpty()) {
            chatMessageMapper.delete(
                    new LambdaQueryWrapper<com.agent.platform.dao.entity.chat.ChatMessage>()
                            .in(com.agent.platform.dao.entity.chat.ChatMessage::getConversationId, convIds));
        }
        chatConversationMapper.delete(
                new LambdaQueryWrapper<ChatConversation>().eq(ChatConversation::getAppId, id));
        chatUsageMapper.delete(new LambdaQueryWrapper<ChatUsage>().eq(ChatUsage::getAppId, id));
        appAgentMapper.deleteById(id);
        appAgentVersionMapper.delete(
                new LambdaQueryWrapper<AppAgentVersion>().eq(AppAgentVersion::getAppId, id));
    }

    // ==================== 版本与发布 ====================

    /**
     * 发布：创建新版本快照，旧发布版本置为未发布
     */
    @Transactional(rollbackFor = Exception.class)
    public AppAgentVersion publish(Long appId, String workflowJson, String promptConfig, Long operatorId) {
        AppAgent app = getById(appId);
        List<Object> versions = appAgentVersionMapper.selectObjs(
                new LambdaQueryWrapper<AppAgentVersion>()
                        .select(AppAgentVersion::getVersion)
                        .eq(AppAgentVersion::getAppId, appId)
                        .orderByDesc(AppAgentVersion::getVersion)
                        .last("limit 1"));
        int maxVersion = versions.isEmpty() ? 0 : ((Number) versions.get(0)).intValue();

        AppAgentVersion version = new AppAgentVersion();
        version.setAppId(appId);
        version.setVersion(maxVersion + 1);
        version.setWorkflowJson(workflowJson);
        version.setPromptConfig(promptConfig);
        version.setIsPublished(1);
        version.setCreatedBy(operatorId);
        version.setCreateTime(LocalDateTime.now());
        appAgentVersionMapper.insert(version);

        // 旧版本取消发布
        appAgentVersionMapper.update(null, new LambdaUpdateWrapper<AppAgentVersion>()
                .eq(AppAgentVersion::getAppId, appId)
                .eq(AppAgentVersion::getIsPublished, 1)
                .ne(AppAgentVersion::getId, version.getId())
                .set(AppAgentVersion::getIsPublished, 0));

        app.setPublishedVersionId(version.getId());
        app.setStatus(1);
        app.setUpdateTime(LocalDateTime.now());
        appAgentMapper.updateById(app);
        return version;
    }

    public AppAgentVersion getPublishedVersion(Long appId) {
        AppAgent app = getById(appId);
        if (app.getPublishedVersionId() == null) {
            throw new BizException("应用尚未发布: " + appId);
        }
        AppAgentVersion version = appAgentVersionMapper.selectById(app.getPublishedVersionId());
        if (version == null) {
            throw new BizException("应用发布版本不存在");
        }
        return version;
    }

    /**
     * 版本列表（按版本号倒序）
     */
    public List<AppAgentVersion> listVersions(Long appId) {
        getById(appId);
        return appAgentVersionMapper.selectList(new LambdaQueryWrapper<AppAgentVersion>()
                .eq(AppAgentVersion::getAppId, appId)
                .orderByDesc(AppAgentVersion::getVersion));
    }

    /**
     * 回滚到指定版本：将版本快照恢复到应用草稿（不自动发布，用户确认后再发布）
     */
    @Transactional(rollbackFor = Exception.class)
    public AppAgentVersion rollback(Long appId, Long versionId) {
        getById(appId);
        AppAgentVersion version = appAgentVersionMapper.selectById(versionId);
        if (version == null || !appId.equals(version.getAppId())) {
            throw new BizException("版本不存在: " + versionId);
        }
        appAgentMapper.update(null, new LambdaUpdateWrapper<AppAgent>()
                .eq(AppAgent::getId, appId)
                .set(AppAgent::getWorkflowJson, version.getWorkflowJson())
                .set(AppAgent::getUpdateTime, LocalDateTime.now()));
        return version;
    }

    // ==================== 工作流 DSL ====================

    /**
     * 获取已发布版本的工作流 DSL（公开访问只用线上版本，不用草稿）
     */
    public String getPublishedWorkflow(Long appId) {
        AppAgentVersion version = getPublishedVersion(appId);
        if (version.getWorkflowJson() == null || version.getWorkflowJson().isBlank()) {
            throw new BizException("应用发布版本未编排工作流");
        }
        return version.getWorkflowJson();
    }

    /**
     * 获取运行用 DSL：草稿优先，否则取已发布版本
     */
    public String getRunWorkflow(Long appId) {
        AppAgent app = getById(appId);
        if (app.getWorkflowJson() != null && !app.getWorkflowJson().isBlank()) {
            return app.getWorkflowJson();
        }
        if (app.getPublishedVersionId() != null) {
            AppAgentVersion version = appAgentVersionMapper.selectById(app.getPublishedVersionId());
            if (version != null && version.getWorkflowJson() != null && !version.getWorkflowJson().isBlank()) {
                return version.getWorkflowJson();
            }
        }
        return null;
    }

    // ==================== Agent 自主执行 ====================

    /**
     * 执行 Agent 循环（应用级入口：工具与知识库来源为应用绑定配置）
     *
     * @param appId         智能体应用 ID
     * @param modelId       对话模型 ID
     * @param systemPrompt  系统提示词（可为 null，使用默认）
     * @param history       历史消息（不含 system）
     * @param maxIterations 最大循环轮数
     */
    public AgentResult chat(Long appId, Long modelId, String systemPrompt,
                            List<ChatMessage> history, Integer maxIterations) {
        AppAgent app = getById(appId);
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
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;
        for (int i = 0; i < maxIter; i++) {
            ChatRequest.ChatRequestBuilder builder = ChatRequest.builder().messages(messages);
            if (!tools.isEmpty()) {
                builder.tools(appToolService.toFunctionTools(tools));
            }
            ChatResponse response = chatModel.call(builder.build());
            // 累加每一轮（含工具调用轮次）的输入/输出/总 Token，供消息落库与用量统计
            if (response != null && response.getUsage() != null) {
                promptTokens += response.getUsage().promptTokens();
                completionTokens += response.getUsage().completionTokens();
                totalTokens += response.getUsage().totalTokens();
            }
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
                        result = appToolService.execute(tool, tc.arguments());
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
        return new AgentResult(answer, steps, promptTokens, completionTokens, totalTokens);
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
                    AppAgentTool tool = appToolService.getById(id);
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

    // ==================== 内部类 ====================

    /** Agent 执行结果 */
    @Data
    @AllArgsConstructor
    public static class AgentResult {
        /** 最终回答 */
        private String answer;
        /** 工具调用步骤 */
        private List<AgentStep> steps;
        /** 输入 Token（含工具调用轮次累计） */
        private long promptTokens;
        /** 输出 Token（含工具调用轮次累计） */
        private long completionTokens;
        /** 全流程累计 Token 总量（含工具调用轮次） */
        private long totalTokens;
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
