package com.agent.platform.dao.dto.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新应用 API 密钥入参（expiresAt/rateLimit/remark 为空即清除）
 */
@Data
public class AppApiKeyUpdateDTO {
    private String name;
    private LocalDateTime expiresAt;
    private Integer rateLimit;
    private String remark;
}
