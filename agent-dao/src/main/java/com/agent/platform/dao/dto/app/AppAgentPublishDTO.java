package com.agent.platform.dao.dto.app;

import lombok.Data;

/**
 * 发布应用（保存版本快照）入参
 */
@Data
public class AppAgentPublishDTO {
    /** 工作流 DSL JSON */
    private String workflowJson;
    /** 提示词配置 JSON */
    private String promptConfig;
}
