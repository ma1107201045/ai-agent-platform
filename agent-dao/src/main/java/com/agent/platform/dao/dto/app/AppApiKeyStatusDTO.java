package com.agent.platform.dao.dto.app;

import lombok.Data;

/**
 * 启用 / 禁用应用 API 密钥入参
 */
@Data
public class AppApiKeyStatusDTO {
    /** 1 启用 / 0 禁用 */
    private Integer status;
}
