package com.agent.platform.dao.entity.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据集成 - 外部连接器
 *
 * <p>命名遵循「表名 → 实体」对齐规则：表 tool_connector → 本类 ToolConnector。
 *
 * <p>类型说明：
 * <ul>
 *   <li>http：HTTP API 连接器，记录基础地址、方法、请求头与鉴权，可一键生成可被智能体调用的 HTTP 工具；</li>
 *   <li>mysql：MySQL 数据库连接器，记录 JDBC URL 与账号，用于连通性校验与后续数据源能力扩展。</li>
 * </ul>
 */
@Data
@TableName("tool_connector")
public class ToolConnector {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 连接器名称（英文标识符，生成 HTTP 工具时作为工具名） */
    private String name;

    /** 描述（用途说明） */
    private String description;

    /** 类型：http / mysql */
    private String type;

    /** http：API 基础地址；mysql：JDBC URL（jdbc:mysql://host:port/db） */
    private String url;

    /** http：请求方式 */
    private String method;

    /** http：额外请求头（JSON 对象） */
    private String headers;

    /** 鉴权：none / bearer / basic（mysql 类型使用 username/password） */
    private String authType;

    /** Bearer Token */
    private String authToken;

    /** 用户名（basic / mysql） */
    private String authUsername;

    /** 密码（basic / mysql） */
    private String authPassword;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
