package com.agent.platform.service.orchestrator;

import com.agent.platform.dao.entity.chat.ChatAgentRun;
import com.agent.platform.dao.mapper.chat.ChatAgentRunMapper;
import com.agent.platform.orchestrator.RunResult;
import com.agent.platform.orchestrator.WorkflowEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流运行记录持久化监听器（agent_run 表）
 * <p>
 * 通过引擎事件 SPI 实现：
 * <ul>
 *   <li>{@code onFlowStarted} → 插入 running 状态行（输入可查，监控页实时可见「运行中」）；</li>
 *   <li>{@code onFlowFinished} → 更新终态（status / answer / error / trace / cost / finish_time）。</li>
 * </ul>
 * 持久化失败只记日志，绝不影响工作流主流程（引擎已隔离监听器异常）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunPersistenceListener implements WorkflowEventListener {

    private final ChatAgentRunMapper chatAgentRunMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void onFlowStarted(FlowStarted e) {
        try {
            ChatAgentRun run = new ChatAgentRun();
            run.setRunId(e.runId());
            run.setAppId(e.appId());
            run.setMode("workflow");
            run.setInput(e.userInput());
            run.setStatus("running");
            run.setCostMs(0L);
            run.setCreateTime(LocalDateTime.now());
            // run_id 唯一；并发重复开始由调用方保证（若已存在则跳过插入，等待终态更新）
            chatAgentRunMapper.insert(run);
        } catch (Exception ex) {
            log.warn("写入 agent_run 开始记录失败 runId={}: {}", e.runId(), ex.getMessage());
        }
    }

    @Override
    public void onFlowFinished(FlowFinished e) {
        try {
            RunResult result = e.result();
            if (result.getRunId() == null) {
                return;
            }
            ChatAgentRun update = new ChatAgentRun();
            update.setRunId(result.getRunId());
            update.setAppId(result.getAppId());
            update.setAnswer(result.getAnswer());
            update.setStatus(result.getStatus() == null ? "success" : result.getStatus().getCode());
            update.setError(result.getError());
            update.setCostMs(result.getCostMs());
            update.setFinishTime(LocalDateTime.now());
            if (result.getTrace() != null) {
                update.setTraceJson(objectMapper.writeValueAsString(result.getTrace()));
            }
            LambdaUpdateWrapper<ChatAgentRun> uw = new LambdaUpdateWrapper<ChatAgentRun>()
                    .eq(ChatAgentRun::getRunId, result.getRunId());
            if (chatAgentRunMapper.update(update, uw) == 0) {
                // 兜底：若开始事件丢失（如空图直接结束），此处补齐整行
                ChatAgentRun existing = chatAgentRunMapper.selectOne(
                        new LambdaQueryWrapper<ChatAgentRun>().eq(ChatAgentRun::getRunId, result.getRunId()).last("limit 1"));
                if (existing == null) {
                    ChatAgentRun full = new ChatAgentRun();
                    full.setRunId(result.getRunId());
                    full.setAppId(result.getAppId());
                    full.setMode("workflow");
                    full.setStatus(result.getStatus() == null ? "success" : result.getStatus().getCode());
                    full.setAnswer(result.getAnswer());
                    full.setError(result.getError());
                    full.setCostMs(result.getCostMs());
                    full.setTraceJson(update.getTraceJson());
                    full.setFinishTime(LocalDateTime.now());
                    full.setCreateTime(result.getStartedAt() == null ? LocalDateTime.now() : result.getStartedAt());
                    chatAgentRunMapper.insert(full);
                }
            }
        } catch (Exception ex) {
            log.warn("更新 agent_run 结束记录失败 runId={}: {}", e.result().getRunId(), ex.getMessage());
        }
    }

    /** 查询某次运行是否已存在（供外部调用前幂等判断） */
    public boolean existsRunId(String runId) {
        return chatAgentRunMapper.selectCount(new LambdaQueryWrapper<ChatAgentRun>()
                .eq(ChatAgentRun::getRunId, runId)) > 0;
    }

    /** 最近一次同 runId 记录（透传给 service 层做幂等/诊断） */
    public ChatAgentRun findByRunId(String runId) {
        List<ChatAgentRun> list = chatAgentRunMapper.selectList(new LambdaQueryWrapper<ChatAgentRun>()
                .eq(ChatAgentRun::getRunId, runId).orderByDesc(ChatAgentRun::getCreateTime).last("limit 1"));
        return list.isEmpty() ? null : list.getFirst();
    }
}
