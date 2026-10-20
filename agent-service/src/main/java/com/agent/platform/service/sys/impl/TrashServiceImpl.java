package com.agent.platform.service.sys.impl;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.mapper.sys.TrashMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.agent.platform.service.sys.TrashService;

/**
 * TrashService 接口实现。
 */
@Service
@RequiredArgsConstructor
public class TrashServiceImpl implements TrashService {

    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>() {{
        put(TYPE_AGENT, "智能体应用");
        put(TYPE_DATASET, "知识库");
        put(TYPE_TEMPLATE, "应用模板");
        put(TYPE_ANNOUNCEMENT, "系统公告");
        put(TYPE_TEAM, "智能体团队");
        put(TYPE_TOOL, "工具");
        put(TYPE_SKILL, "技能");
        put(TYPE_PROMPT_TEMPLATE, "提示词模板");
        put(TYPE_CONVERSATION, "会话");
        put(TYPE_DATA_TABLE, "数据表");
    }}

;

    private final TrashMapper trashMapper;

    @Override
    public List<Map<String, Object>> list(String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (type == null || type.isBlank()) {
            TYPE_LABELS.keySet().forEach(code -> collect(result, code));
        } else if (TYPE_LABELS.containsKey(type)) {
            collect(result, type);
        } else {
            throw new BizException("不支持的资源类型: " + type);
        }
        return result;
    }

    /** 恢复单个数据（deleted=0，删除时间清空）；仅允许操作当前租户的数据 */
    @Override
    public void restore(String type, Long id) {
        assertOwnedByCurrentTenant(type, id);
        int affected = switch (type) {
            case TYPE_AGENT -> trashMapper.restoreAgent(id);
            case TYPE_DATASET -> trashMapper.restoreDataset(id);
            case TYPE_TEMPLATE -> trashMapper.restoreTemplate(id);
            case TYPE_ANNOUNCEMENT -> trashMapper.restoreAnnouncement(id);
            case TYPE_TEAM -> trashMapper.restoreTeam(id);
            case TYPE_TOOL -> trashMapper.restoreTool(id);
            case TYPE_SKILL -> trashMapper.restoreSkill(id);
            case TYPE_PROMPT_TEMPLATE -> trashMapper.restorePromptTemplate(id);
            case TYPE_CONVERSATION -> trashMapper.restoreConversation(id);
            case TYPE_DATA_TABLE -> trashMapper.restoreDataTable(id);
            default -> throw new BizException("不支持的资源类型: " + type);
        };
        if (affected <= 0) {
            throw new BizException("未找到可恢复的数据");
        }
    }

    /** 彻底删除单个数据（应用/知识库级联清理子数据，不可恢复）；仅允许操作当前租户的数据 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void purge(String type, Long id) {
        assertOwnedByCurrentTenant(type, id);
        switch (type) {
            case TYPE_AGENT -> {
                trashMapper.purgeAgentVersions(id);
                trashMapper.purgeAgentUsage(id);
                trashMapper.purgeAgentMessages(id);
                trashMapper.purgeAgentConversations(id);
                trashMapper.purgeAgent(id);
            }
            case TYPE_DATASET -> {
                trashMapper.purgeDatasetChunks(id);
                trashMapper.purgeDatasetDocuments(id);
                trashMapper.purgeDataset(id);
            }
            case TYPE_TEMPLATE -> trashMapper.purgeTemplate(id);
            case TYPE_ANNOUNCEMENT -> trashMapper.purgeAnnouncement(id);
            case TYPE_TEAM -> {
                trashMapper.purgeTeamMembers(id);
                trashMapper.purgeTeamRuns(id);
                trashMapper.purgeTeam(id);
            }
            case TYPE_TOOL -> trashMapper.purgeTool(id);
            case TYPE_SKILL -> {
                trashMapper.purgeSkillVersions(id);
                trashMapper.purgeSkill(id);
            }
            case TYPE_PROMPT_TEMPLATE -> {
                trashMapper.purgePromptVersions(id);
                trashMapper.purgePromptTemplate(id);
            }
            case TYPE_CONVERSATION -> {
                trashMapper.purgeConversationMessages(id);
                trashMapper.purgeConversationUsage(id);
                trashMapper.purgeConversation(id);
            }
            case TYPE_DATA_TABLE -> {
                trashMapper.purgeDataRecords(id);
                trashMapper.purgeDataTable(id);
            }
            default -> throw new BizException("不支持的资源类型: " + type);
        }
    }

    /**
     * 彻底删除前的"影响预览"：统计将被级联删除的子数据条数。
     * 仅统计非零项；无级联子数据的类型返回空 Map。
     */
    @Override
    public Map<String, Long> impact(String type, Long id) {
        assertOwnedByCurrentTenant(type, id);
        Map<String, Long> result = new LinkedHashMap<>();
        switch (type) {
            case TYPE_AGENT -> {
                putIfPositive(result, "版本快照", trashMapper.countAgentVersions(id));
                putIfPositive(result, "会话", trashMapper.countAgentConversations(id));
                putIfPositive(result, "对话消息", trashMapper.countAgentMessages(id));
                putIfPositive(result, "用量记录", trashMapper.countAgentUsage(id));
            }
            case TYPE_DATASET -> {
                putIfPositive(result, "文档", trashMapper.countDatasetDocuments(id));
                putIfPositive(result, "向量分片", trashMapper.countDatasetChunks(id));
            }
            case TYPE_TEAM -> {
                putIfPositive(result, "成员配置", trashMapper.countTeamMembers(id));
                putIfPositive(result, "运行记录", trashMapper.countTeamRuns(id));
            }
            case TYPE_SKILL -> putIfPositive(result, "技能版本", trashMapper.countSkillVersions(id));
            case TYPE_PROMPT_TEMPLATE -> putIfPositive(result, "模板版本", trashMapper.countPromptVersions(id));
            case TYPE_CONVERSATION -> {
                putIfPositive(result, "对话消息", trashMapper.countConversationMessages(id));
                putIfPositive(result, "用量记录", trashMapper.countConversationUsage(id));
            }
            case TYPE_DATA_TABLE -> putIfPositive(result, "数据记录", trashMapper.countDataRecords(id));
            case TYPE_TEMPLATE, TYPE_ANNOUNCEMENT, TYPE_TOOL -> {
                // 无级联子数据
            }
            default -> throw new BizException("不支持的资源类型: " + type);
        }
        return result;
    }

    private static void putIfPositive(Map<String, Long> map, String label, long count) {
        if (count > 0) {
            map.put(label, count);
        }
    }

    /**
     *
     * @return removed 清理条数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int cleanup(int retentionDays) {
        int days = retentionDays <= 0 ? DEFAULT_RETENTION_DAYS : retentionDays;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<Map<String, Object>> all = list(null);
        int removed = 0;
        for (Map<String, Object> item : all) {
            Object time = item.get("deletedTime");
            if (!(time instanceof LocalDateTime deletedTime)) {
                continue;
            }
            if (!deletedTime.isBefore(cutoff)) {
                continue;
            }
            purge((String) item.get("type"), ((Number) item.get("id")).longValue());
            removed++;
        }
        return removed;
    }

    /**
     * 归属校验：登录场景下只能操作本租户的回收站数据；无登录上下文（如定时清理）时跳过校验。
     * 父级资源归属确认后，级联删除子表数据是安全的。
     */
    private void assertOwnedByCurrentTenant(String type, Long id) {
        Long tenantId = UserContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        boolean owned = rowsOf(type, tenantId).stream()
                .anyMatch(row -> row.get("id") != null && ((Number) row.get("id")).longValue() == id);
        if (!owned) {
            throw new BizException("未找到可操作的回收站数据，或不属于当前租户");
        }
    }

    private List<Map<String, Object>> rowsOf(String type, Long tenantId) {
        return switch (type) {
            case TYPE_AGENT -> trashMapper.listDeletedAgent(tenantId);
            case TYPE_DATASET -> trashMapper.listDeletedDataset(tenantId);
            case TYPE_TEMPLATE -> trashMapper.listDeletedTemplate(tenantId);
            case TYPE_ANNOUNCEMENT -> trashMapper.listDeletedAnnouncement(tenantId);
            case TYPE_TEAM -> trashMapper.listDeletedTeam(tenantId);
            case TYPE_TOOL -> trashMapper.listDeletedTool(tenantId);
            case TYPE_SKILL -> trashMapper.listDeletedSkill(tenantId);
            case TYPE_PROMPT_TEMPLATE -> trashMapper.listDeletedPromptTemplate(tenantId);
            case TYPE_CONVERSATION -> trashMapper.listDeletedConversation(tenantId);
            case TYPE_DATA_TABLE -> trashMapper.listDeletedDataTable(tenantId);
            default -> List.of();
        };
    }

    private void collect(List<Map<String, Object>> result, String type) {
        List<Map<String, Object>> rows = rowsOf(type, UserContext.getTenantId());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("name", row.get("name"));
            Object rawTime = row.get("deleted_time");
            item.put("deletedTime", rawTime instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : rawTime);
            item.put("type", type);
            item.put("typeLabel", TYPE_LABELS.get(type));
            result.add(item);
        }
    }
}
