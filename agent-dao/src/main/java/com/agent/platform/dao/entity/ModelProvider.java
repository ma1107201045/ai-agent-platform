package com.agent.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型供应商
 */
@Data
@TableName("model_provider")
public class ModelProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 供应商名称（展示用） */
    private String name;

    /** 供应商类型：openai-compatible / anthropic / ... */
    private String type;

    /** API 基础地址 */
    private String baseUrl;

    /** API Key（需加密存储） */
    private String apiKey;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
