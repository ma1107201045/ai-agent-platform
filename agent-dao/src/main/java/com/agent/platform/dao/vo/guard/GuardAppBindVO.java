package com.agent.platform.dao.vo.guard;

import lombok.Data;

/**
 * 内容安全-应用绑定展示模型
 */
@Data
public class GuardAppBindVO {

    /** 应用ID */
    private Long appId;

    private String appName;

    /** 应用类型: chatflow/workflow/agent */
    private String appType;

    /** 绑定ID（未绑定为null） */
    private Long bindId;

    /** 绑定状态：0未启用 1启用 */
    private Integer bindEnabled;

    /** 模式: enforce强制/log仅记录 */
    private String bindMode;

    /** 绑定规则ID列表 */
    private String ruleIds;

    /** 绑定规则数 */
    private Integer ruleCount;
}
