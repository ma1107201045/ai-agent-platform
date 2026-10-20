package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppTool;
import com.agent.platform.dao.entity.app.AppAgentVersion;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.app.AppAgentVersionMapper;
import com.agent.platform.dao.vo.app.AppAgentChatVO;
import com.agent.platform.llm.model.ChatMessage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
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
public interface AppAgentService {

    /**
     * 分页查询应用，支持名称模糊搜索与类型过滤
     */
    public Page<AppAgent> page(long page, long size, String keyword, String type);

    public AppAgent getById(Long id);

    public AppAgent create(AppAgent app);

    /**
     * 更新应用：仅允许更新业务字段，防止 status / publishedVersionId 等敏感字段被覆盖
     */
    public void update(AppAgent app);

    /**
     * 删除应用：软删除移入回收站，保留版本、会话与消息等子数据，
     * 恢复后可完整还原；彻底删除由回收站级联清理
     */
    public void delete(Long id);

    /**
     * 发布：创建新版本快照，旧发布版本置为未发布
     */
    public AppAgentVersion publish(Long appId, String workflowJson, String promptConfig, Long operatorId);

    public AppAgentVersion getPublishedVersion(Long appId);

    /**
     * 批量获取已发布版本：单次 IN 查询，避免逐应用查询的 N+1；
     * 未发布或版本缺失的应用不出现在返回 Map 中
     */
    public java.util.Map<Long, AppAgentVersion> getPublishedVersions(java.util.Collection<Long> appIds);

    /**
     * 版本列表（按版本号倒序）
     */
    public List<AppAgentVersion> listVersions(Long appId);

    /**
     * 回滚到指定版本：将版本快照恢复到应用草稿（不自动发布，用户确认后再发布）
     */
    public AppAgentVersion rollback(Long appId, Long versionId);

    /**
     * 获取已发布版本的工作流 DSL（公开访问只用线上版本，不用草稿）
     */
    public String getPublishedWorkflow(Long appId);

    /**
     * 获取运行用 DSL：草稿优先，否则取已发布版本
     */
    public String getRunWorkflow(Long appId);

    /**
     * 执行 Agent 循环（应用级入口：工具与知识库来源为应用绑定配置）
     *
     * @param appId         智能体应用 ID
     * @param modelId       对话模型 ID
     * @param systemPrompt  系统提示词（可为 null，使用默认）
     * @param history       历史消息（不含 system）
     * @param maxIterations 最大循环轮数
     */
    public AppAgentChatVO chat(Long appId, Long modelId, String systemPrompt,
                               List<ChatMessage> history, Integer maxIterations);

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
    public AppAgentChatVO chat(Long modelId, String systemPrompt, List<AppTool> tools,
                               String datasetIdsJson, List<ChatMessage> history, Integer maxIterations);

    /**
     * 解析工具 ID JSON 数组并加载启用的工具（应用绑定或节点级配置均可）
     */
    public List<AppTool> loadTools(String toolIdsJson);
}
