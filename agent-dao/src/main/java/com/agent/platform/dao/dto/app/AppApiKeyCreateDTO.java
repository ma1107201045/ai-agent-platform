package com.agent.platform.dao.dto.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建应用 API 密钥入参
 */
@Data
public class AppApiKeyCreateDTO {
    /** 关联应用 ID（必填） */
    private Long appId;
    /** 密钥名称（用途标识，必填） */
    private String name;
    /** 过期时间（空 = 永不过期） */
    private LocalDateTime expiresAt;
    /** 每分钟请求上限（空 = 不限流） */
    private Integer rateLimit;
    private String remark;
}
