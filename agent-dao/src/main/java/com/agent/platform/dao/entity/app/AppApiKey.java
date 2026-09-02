package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用 API 密钥（对外调用鉴权）。
 *
 * <p>安全设计：仅存密钥前缀 + SHA-256 哈希，明文只在「创建 / 轮换」时返回一次；
 * 列表仅展示前缀，配合重试轮换即可撤销泄漏密钥。
 */
@Data
@TableName("app_api_key")
public class AppApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 关联应用 ID（app_agent.id） */
    private Long appId;

    /** 密钥名称（用途标识，如：生产环境） */
    private String name;

    /** 密钥前缀（列表展示用，形如 sk-a1b2c3d4…） */
    private String keyPrefix;

    /** 密钥 SHA-256 哈希（十六进制） */
    private String keyHash;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 过期时间（空 = 永不过期） */
    private LocalDateTime expiresAt;

    /** 每分钟请求上限（空 = 不限流） */
    private Integer rateLimit;

    /** 累计调用次数 */
    private Long usageCount;

    /** 最近使用时间 */
    private LocalDateTime lastUsedAt;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 关联应用名称（非表字段，列表展示用） */
    @TableField(exist = false)
    private String appName;

    /** 明文密钥（非表字段，仅创建/轮换后返回一次） */
    @TableField(exist = false)
    private String plainKey;
}
