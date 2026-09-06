package com.agent.platform.dao.dto.app;

import lombok.Data;

/**
 * 工具执行测试入参
 */
@Data
public class AppToolTestDTO {
    /** 测试参数（通常为 JSON 字符串） */
    private String arguments;
}
