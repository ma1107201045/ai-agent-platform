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

    public static final String TYPE_AGENT = "agent";

    public static final String TYPE_DATASET = "dataset";

    public static final String TYPE_TEMPLATE = "template";

    public static final String TYPE_ANNOUNCEMENT = "announcement";

    public static final String TYPE_TEAM = "team";

    public static final String TYPE_TOOL = "tool";

    public static final String TYPE_SKILL = "skill";

    public static final String TYPE_PROMPT_TEMPLATE = "prompt_template";

    public static final String TYPE_CONVERSATION = "conversation";

    public static final String TYPE_DATA_TABLE = "data_table";

    /** 默认清理阈值（天）：删除超过该时长的记录可一键彻底清除 */
    public static final int DEFAULT_RETENTION_DAYS = 30;

    public List<Map<String, Object>> list(String type);

    /** 恢复单个数据（deleted=0，删除时间清空） */
    public void restore(String type, Long id);

    /** 彻底删除单个数据（应用/知识库级联清理子数据，不可恢复） */
    public void purge(String type, Long id);

    /**
     * 清理过期数据：删除超过 retentionDays 的回收站数据（按各资源类型分别执行）。
     *
     * @return removed 清理条数
     */
    public int cleanup(int retentionDays);
}
