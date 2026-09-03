package com.agent.platform.dao.dto.app;

import lombok.Data;

/**
 * 回退提示词模板到指定历史版本入参
 */
@Data
public class AppPromptRollbackDTO {
    private Integer version;
}
