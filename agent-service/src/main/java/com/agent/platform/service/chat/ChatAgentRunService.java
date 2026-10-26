package com.agent.platform.service.chat;

import com.agent.platform.dao.entity.chat.ChatAgentRun;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 工作流运行记录查询（运行监控页）
 */
public interface ChatAgentRunService {

    /** 应用运行记录分页（可按状态过滤，按开始时间倒序） */
    Page<ChatAgentRun> page(Long appId, String status, long page, long size);

    /** 按运行标识取详情（含完整轨迹） */
    ChatAgentRun getByRunId(String runId);

    /** 应用级校验运行归属（供详情/重放类接口复用） */
    ChatAgentRun getByRunId(String runId, Long expectedAppId);
}
