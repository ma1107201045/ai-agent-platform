package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppPromptTemplate;
import com.agent.platform.dao.entity.app.AppPromptVersion;
import com.agent.platform.dao.mapper.app.AppPromptTemplateMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * 提示词模板（AppPromptTemplate）服务：模板 CRUD + 版本快照（留痕/回退）+ 变量渲染。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service」对齐规则：
 * 表 app_prompt_template → 实体 AppPromptTemplate → Mapper AppPromptTemplateMapper
 *                        → 本类 AppPromptTemplateService。
 *
 * <p>版本策略：
 * <ul>
 *   <li>创建模板即生成 v1 快照；</li>
 *   <li>更新时若正文/变量定义有变化，则自动基于历史最大版本号 +1 生成新快照（纯基础信息变更不升版）；</li>
 *   <li>回退操作将目标版本内容写回主表，并生成一条新版本快照（内容与原目标一致）用于留痕审计。</li>
 * </ul>
 */
public interface AppPromptTemplateService {

    public Page<AppPromptTemplate> page(long page, long size, String keyword, String category);

    public List<AppPromptTemplate> listEnabled(String category);

    public AppPromptTemplate getById(Long id);

    /** 创建模板：初始版本 v1 并写入快照 */
    public AppPromptTemplate create(AppPromptTemplate template);

    /**
     * 更新模板：基础信息（名称/描述/分类/状态）直接更新；
     * 正文或变量定义有变化时自动生成新版本快照。
     */
    public AppPromptTemplate update(AppPromptTemplate template);

    /** 删除模板：软删除进回收站（版本快照保留，恢复时还原） */
    public void delete(Long id);

    public List<AppPromptVersion> versions(Long templateId);

    public AppPromptVersion getVersion(Long templateId, Integer version);

    /** 回退到指定历史版本：目标内容写回主表并生成新版本快照留痕 */
    public AppPromptTemplate rollback(Long id, Integer version);

    /**
     * 渲染模板正文：将 {{var}} 占位替换为变量值。
     * 返回渲染后文本；未提供的变量保持原样以便前端提示。
     */
    public String render(String content, Map<String, Object> variables);

    /** 从正文提取占位变量名列表 */
    public List<String> extractVariables(String content);

    /** 解析变量定义 JSON（宽松解析，失败返回空 map） */
    public Map<String, String> parseVariableDefs(String variablesJson);
}
