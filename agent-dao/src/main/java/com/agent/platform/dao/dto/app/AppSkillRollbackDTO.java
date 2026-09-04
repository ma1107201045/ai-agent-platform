package com.agent.platform.dao.dto.app;

import lombok.Data;

/**
 * 技能回退到历史版本请求
 */
@Data
public class AppSkillRollbackDTO {

    /** 目标版本号 */
    private Integer version;
}
