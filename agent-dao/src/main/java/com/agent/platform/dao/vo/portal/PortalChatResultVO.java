package com.agent.platform.dao.vo.portal;

import lombok.Data;

/**
 * 公开对话结果
 */
@Data
public class PortalChatResultVO {
    private String answer;
    /** workflow → 节点轨迹 Trace[]；agent → 工具步骤 AgentStep[]；chatflow → null */
    private Object detail;
}
