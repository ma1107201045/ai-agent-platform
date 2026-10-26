package com.agent.platform.service.sys;

import java.util.List;
import java.util.Map;

/**
 * 回收站：支持被删除的应用、知识库、模板与公告的恢复或彻底删除。
 *
 * <p>这些资源删除时进入软删除（deleted=1），此处直接面向数据库恢复数据；
 * 智能体应用与知识库彻底删除时会级联清理其子数据。</p>
 */
public interface TrashService {

    static final String TYPE_AGENT = "agent";

    static final String TYPE_DATASET = "dataset";

    static final String TYPE_TEMPLATE = "template";

    static final String TYPE_ANNOUNCEMENT = "announcement";

    static final String TYPE_TEAM = "team";

    static final String TYPE_TOOL = "tool";

    static final String TYPE_SKILL = "skill";

    static final String TYPE_PROMPT_TEMPLATE = "prompt_template";

    static final String TYPE_CONVERSATION = "conversation";

    static final String TYPE_DATA_TABLE = "data_table";

    /** 默认清理阈值（天）：删除超过该时长的记录可一键彻底清除 */
    static final int DEFAULT_RETENTION_DAYS = 30;

    List<Map<String, Object>> list(String type);

    /** 恢复单个数据（deleted=0，删除时间清空） */
    void restore(String type, Long id);

    /** 彻底删除单个数据（应用/知识库级联清理子数据，不可恢复） */
    void purge(String type, Long id);

    /**
     * 彻底删除前的"影响预览"：返回将被级联删除的子数据计数（如 会话/消息/用量），
     * 供前端确认框展示，让用户知道会丢什么。key 为子数据中文标签，value 为条数。
     */
    Map<String, Long> impact(String type, Long id);

    /**
     * 清理过期数据：删除超过 retentionDays 的回收站数据（按各资源类型分别执行）。
     *
     * @return removed 清理条数
     */
    int cleanup(int retentionDays);
}
