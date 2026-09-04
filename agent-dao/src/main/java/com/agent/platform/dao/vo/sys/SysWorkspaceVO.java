package com.agent.platform.dao.vo.sys;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作空间（当前租户空间）视图
 */
@Data
public class SysWorkspaceVO {

    private Long id;

    /** 空间名称 */
    private String name;

    /** 空间编码（唯一） */
    private String code;

    /** 套餐：free/pro/enterprise */
    private String plan;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 成员数 */
    private Long memberCount;

    /** 应用数 */
    private Long appCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
