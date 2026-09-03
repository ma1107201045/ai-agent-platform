package com.agent.platform.dao.entity.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用-智能体工具注册表
 */
@Data
@TableName("tool_info")
public class ToolInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /**
     * 工具名称（模型调用时使用）
     */
    private String name;

    /**
     * 工具描述（给模型理解用途）
     */
    private String description;

    /**
     * 类型：http / code
     */
    private String type;

    /**
     * HTTP 工具：请求地址
     */
    private String url;

    /**
     * HTTP 工具：请求方式
     */
    private String method;

    /**
     * HTTP 工具：请求头（JSON）
     */
    private String headers;

    /**
     * 鉴权：none / bearer / basic
     */
    private String authType;

    /**
     * Bearer Token
     */
    private String authToken;

    /**
     * 参数 JSON Schema（JSON）
     */
    private String parameters;

    /**
     * 代码工具：MVEL 脚本
     */
    private String code;

    /**
     * 状态：0禁用 1启用
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
