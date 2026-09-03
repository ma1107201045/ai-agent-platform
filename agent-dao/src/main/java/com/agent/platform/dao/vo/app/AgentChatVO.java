package com.agent.platform.dao.vo.app;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Agent 自主执行结果
 */
@Data
@AllArgsConstructor
public class AgentChatVO {
    /** 最终回答 */
    private String answer;
    /** 工具调用步骤 */
    private List<AgentStepVO> steps;
    /** 输入 Token（含工具调用轮次累计） */
    private long promptTokens;
    /** 输出 Token（含工具调用轮次累计） */
    private long completionTokens;
    /** 全流程累计 Token 总量（含工具调用轮次） */
    private long totalTokens;
}
