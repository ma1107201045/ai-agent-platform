package com.agent.platform.dao.vo.portal;

import lombok.Data;

/**
 * 公开应用信息（仅已发布应用对外暴露）
 */
@Data
public class PortalAppInfoVO {
    private Long id;
    private String name;
    private String type;
    private String description;
    private String welcomeMessage;
    private String openingQuestions;
}
