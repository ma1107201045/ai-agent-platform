package com.agent.platform.controller;

import com.agent.platform.common.result.Result;
import com.agent.platform.llm.model.ChatMessage;
import com.agent.platform.service.AgentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 智能体对话接口（自主模式：规划-工具调用-观察）
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** Agent 对话（非流式） */
    @PostMapping("/{appId}/chat")
    public Result<AgentService.AgentResult> chat(@PathVariable Long appId, @RequestBody ChatReq req) {
        if (req.getModelId() == null) {
            throw new com.agent.platform.common.exception.BizException("请选择对话模型");
        }
        return Result.ok(agentService.chat(appId, req.getModelId(), req.getSystemPrompt(),
                req.getMessages(), req.getMaxIterations()));
    }

    @Data
    public static class ChatReq {
        private Long modelId;
        private String systemPrompt;
        private List<ChatMessage> messages;
        private Integer maxIterations;
    }
}
