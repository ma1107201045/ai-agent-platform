package com.agent.platform.dao.entity.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型信息
 */
@Data
@TableName("model_info")
public class ModelInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long providerId;

    /** 模型名（调用 API 时使用） */
    private String name;

    /** 模型类型：llm / embedding / rerank / tts / asr / image */
    private String modelType;

    /** 上下文窗口 */
    private Integer contextWindow;

    /** 最大输出 Token */
    private Integer maxTokens;

    /** 能力标签（JSON 数组字符串，如 ["function_call","vision","stream"]） */
    private String capabilities;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
