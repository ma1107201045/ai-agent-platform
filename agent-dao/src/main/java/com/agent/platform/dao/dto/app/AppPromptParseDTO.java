package com.agent.platform.dao.dto.app;

import lombok.Data;

/**
 * 解析变量定义 JSON 入参
 */
@Data
public class AppPromptParseDTO {
    /** 变量定义 JSON（name→desc 映射） */
    private String variables;
}
