package com.agent.platform.dao.dto.model;

import lombok.Data;

import java.util.List;

/**
 * 模型向量化入参
 */
@Data
public class ModelEmbedDTO {
    private Long modelId;
    private List<String> texts;
}
