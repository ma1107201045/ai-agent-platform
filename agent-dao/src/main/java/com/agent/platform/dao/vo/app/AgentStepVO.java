package com.agent.platform.dao.vo.app;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Agent 工具调用步骤
 */
@Data
@AllArgsConstructor
public class AgentStepVO {
    private String toolName;
    private String arguments;
    private String result;
    private long costMs;
}
