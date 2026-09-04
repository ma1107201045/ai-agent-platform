package com.agent.platform.service.chat;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.chat.ChatAgentRun;
import com.agent.platform.dao.mapper.chat.ChatAgentRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 工作流运行记录查询（运行监控页）
 */
@Service
@RequiredArgsConstructor
public class ChatAgentRunService {

    private final ChatAgentRunMapper chatAgentRunMapper;

    /** 应用运行记录分页（可按状态过滤，按开始时间倒序） */
    public Page<ChatAgentRun> page(Long appId, String status, long page, long size) {
        boolean hasStatus = StringUtils.hasText(status);
        LambdaQueryWrapper<ChatAgentRun> qw = new LambdaQueryWrapper<ChatAgentRun>()
                .eq(appId != null, ChatAgentRun::getAppId, appId)
                // 第三个参数会被 LambdaQueryWrapper 立即求值，必须避免 status=null 时调用 trim() 抛 NPE
                .eq(hasStatus, ChatAgentRun::getStatus, hasStatus ? status.trim() : null)
                .orderByDesc(ChatAgentRun::getCreateTime);
        return chatAgentRunMapper.selectPage(new Page<>(page, size), qw);
    }

    /** 按运行标识取详情（含完整轨迹） */
    public ChatAgentRun getByRunId(String runId) {
        ChatAgentRun run = chatAgentRunMapper.selectOne(new LambdaQueryWrapper<ChatAgentRun>()
                .eq(ChatAgentRun::getRunId, runId)
                .last("limit 1"));
        if (run == null) {
            throw new BizException("运行记录不存在: " + runId);
        }
        return run;
    }

    /** 应用级校验运行归属（供详情/重放类接口复用） */
    public ChatAgentRun getByRunId(String runId, Long expectedAppId) {
        ChatAgentRun run = getByRunId(runId);
        if (expectedAppId != null && !Objects.equals(expectedAppId, run.getAppId())) {
            throw new BizException("运行记录不存在: " + runId);
        }
        return run;
    }
}
