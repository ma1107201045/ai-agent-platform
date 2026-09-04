package com.agent.platform.service.sys;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.mapper.sys.TrashMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回收站：支持被删除的应用、知识库、模板与公告的恢复或彻底删除。
 *
 * <p>这些资源删除时进入软删除（deleted=1），此处直接面向数据库恢复数据；
 * 智能体应用与知识库彻底删除时会级联清理其子数据。</p>
 */
@Service
@RequiredArgsConstructor
public class TrashService {

    public static final String TYPE_AGENT = "agent";
    public static final String TYPE_DATASET = "dataset";
    public static final String TYPE_TEMPLATE = "template";
    public static final String TYPE_ANNOUNCEMENT = "announcement";

    /** 默认清理阈值（天）：删除超过该时长的记录可一键彻底清除 */
    public static final int DEFAULT_RETENTION_DAYS = 30;

    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>() {{
        put(TYPE_AGENT, "智能体应用");
        put(TYPE_DATASET, "知识库");
        put(TYPE_TEMPLATE, "应用模板");
        put(TYPE_ANNOUNCEMENT, "系统公告");
    }};

    private final TrashMapper trashMapper;

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

    /** 恢复单个数据（deleted=0，删除时间清空） */
    public void restore(String type, Long id) {
        int affected = switch (type) {
            case TYPE_AGENT -> trashMapper.restoreAgent(id);
            case TYPE_DATASET -> trashMapper.restoreDataset(id);
            case TYPE_TEMPLATE -> trashMapper.restoreTemplate(id);
            case TYPE_ANNOUNCEMENT -> trashMapper.restoreAnnouncement(id);
            default -> throw new BizException("不支持的资源类型: " + type);
        };
        if (affected <= 0) {
            throw new BizException("未找到可恢复的数据");
        }
    }

    /** 彻底删除单个数据（应用/知识库级联清理子数据，不可恢复） */
    @Transactional(rollbackFor = Exception.class)
    public void purge(String type, Long id) {
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
            default -> throw new BizException("不支持的资源类型: " + type);
        }
    }

    /**
     * 清理过期数据：删除超过 retentionDays 的回收站数据（按各资源类型分别执行）。
     *
     * @return removed 清理条数
     */
    @Transactional(rollbackFor = Exception.class)
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

    private void collect(List<Map<String, Object>> result, String type) {
        List<Map<String, Object>> rows = switch (type) {
            case TYPE_AGENT -> trashMapper.listDeletedAgent();
            case TYPE_DATASET -> trashMapper.listDeletedDataset();
            case TYPE_TEMPLATE -> trashMapper.listDeletedTemplate();
            case TYPE_ANNOUNCEMENT -> trashMapper.listDeletedAnnouncement();
            default -> List.of();
        };
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
