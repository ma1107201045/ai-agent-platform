package com.agent.platform.dao.vo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 可用模型信息（供前端下拉 / 默认模型选择）
 */
@Data
@AllArgsConstructor
public class ModelInfoVO {
    private Long id;
    private String providerName;
    private String modelName;
    private Integer contextWindow;
}
