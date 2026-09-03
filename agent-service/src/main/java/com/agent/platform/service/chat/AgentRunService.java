package com.agent.platform.service.chat;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.chat.AgentRun;
import com.agent.platform.dao.mapper.chat.AgentRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 工作流运行记录查询（运行监控页）
 */
@Service
@RequiredArgsConstructor
public class AgentRunService {

    private final AgentRunMapper agentRunMapper;

    /** 应用运行记录分页（可按状态过滤，按开始时间倒序） */
    public Page<AgentRun> page(Long appId, String status, long page, long size) {
        LambdaQueryWrapper<AgentRun> qw = new LambdaQueryWrapper<AgentRun>()
                .eq(appId != null, AgentRun::getAppId, appId)
                .eq(status != null && !status.isBlank(), AgentRun::getStatus, status.trim())
                .orderByDesc(AgentRun::getCreateTime);
        return agentRunMapper.selectPage(new Page<>(page, size), qw);
    }

    /** 按运行标识取详情（含完整轨迹） */
    public AgentRun getByRunId(String runId) {
        AgentRun run = agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getRunId, runId)
                .last("limit 1"));
        if (run == null) {
            throw new BizException("运行记录不存在: " + runId);
        }
        return run;
    }

    /** 应用级校验运行归属（供详情/重放类接口复用） */
    public AgentRun getByRunId(String runId, Long expectedAppId) {
        AgentRun run = getByRunId(runId);
        if (expectedAppId != null && !Objects.equals(expectedAppId, run.getAppId())) {
            throw new BizException("运行记录不存在: " + runId);
        }
        return run;
    }
}
