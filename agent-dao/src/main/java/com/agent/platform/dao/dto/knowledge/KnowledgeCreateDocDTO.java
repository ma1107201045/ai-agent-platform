package com.agent.platform.dao.dto.knowledge;

import lombok.Data;

/**
 * 创建知识库文档（文本直建）入参
 */
@Data
public class KnowledgeCreateDocDTO {
    private String name;
    private String content;
}
