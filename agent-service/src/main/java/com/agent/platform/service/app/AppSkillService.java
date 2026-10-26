package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppSkill;
import com.agent.platform.dao.entity.app.AppSkillVersion;
import com.agent.platform.dao.mapper.app.AppSkillMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 技能（AppSkill）服务：技能 CRUD + 版本快照（留痕/回退）+ 导入导出 + 运行期能力解析。
 *
 * <p>技能 = 指令正文 + 变量 + 依赖工具 + 参考数据集的可复用能力包，
 * 绑定到应用（app_agent.skill_ids）后由 {@link AppAgentService} / {@code AgentRunnerImpl} 注入 Agent 循环：
 * <ul>
 *   <li>指令正文拼入系统提示词（技能过多时按与用户问题的相关度轻量打分择优）；</li>
 *   <li>tool 型技能依赖的工具并入本次工具白名单；</li>
 *   <li>knowledge 型技能参考的数据集并入本次知识库检索范围。</li>
 * </ul>
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service」对齐规则：
 * 表 app_skill → 实体 AppSkill → Mapper AppSkillMapper → 本类 AppSkillService。
 * 版本策略与提示词模板一致：创建即 v1；内容/依赖变更自动升版；回退生成留痕新版本。
 */
public interface AppSkillService {

    Page<AppSkill> page(long page, long size, String keyword, String category, String type);

    /** 启用技能列表（应用绑定下拉 / Agent 节点选择） */
    List<AppSkill> listEnabled(String category, String type);

    AppSkill getById(Long id);

    /** 创建技能：初始版本 v1 并写入快照 */
    AppSkill create(AppSkill skill);

    /**
     * 更新技能：基础信息（名称/描述/分类/类型/状态）直接更新；
     * 指令正文、变量定义、依赖工具或参考数据集变化时自动升版快照。
     */
    AppSkill update(AppSkill skill);

    /** 删除技能：若已被应用绑定则拒绝（需先解绑），软删除进回收站（版本快照保留，恢复时还原） */
    void delete(Long id);

    /** 引用该技能的应用数量（app_agent.skill_ids 为 JSON 数组） */
    long referencingAppCount(Long skillId);

    /** 引用该技能的应用（供前端提示哪些应用在用） */
    List<AppAgent> referencingApps(Long skillId);

    List<AppSkillVersion> versions(Long skillId);

    AppSkillVersion getVersion(Long skillId, Integer version);

    /** 回退到指定历史版本：目标内容写回主表并生成新版本快照留痕 */
    AppSkill rollback(Long skillId, Integer version);

    /** 导出技能：返回当前版本自包含数据（含元信息 + 指令 + 依赖），供分享/备份 */
    AppSkill exportSkill(Long skillId);

    /** 导入技能：按外部 JSON 新建技能（id 清零重新落库），code 冲突时自动追加后缀 */
    AppSkill importSkill(AppSkill skill);

    // ---------- 运行期能力解析（Agent 注入） ----------

    /** 运行期能力解析结果 */
    record SkillRuntimeBO(String instructions, List<Long> toolIds, List<Long> datasetIds) {
    }

    /**
     * 解析绑定技能（JSON 数组）为运行期注入信息：指令 / 依赖工具 / 参考数据集。
     * 忽略已禁用技能；技能数量超过阈值时按与用户问题的相关度择优。
     */
    SkillRuntimeBO resolveRuntime(String skillIdsJson, String query);

    SkillRuntimeBO resolveRuntime(List<Long> skillIds, String query);
}
