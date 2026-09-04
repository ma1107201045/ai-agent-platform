package com.agent.platform.dao.entity.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（由 Controller 切面自动记录关键写操作）
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 操作人ID */
    private Long userId;

    /** 操作人登录名 */
    private String username;

    /** 所属模块 */
    private String module;

    /** 操作内容 */
    private String operation;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String uri;

    /** 来源IP */
    private String ip;

    /** 是否成功：0失败 1成功 */
    private Integer success;

    /** 错误信息 */
    private String errorMsg;

    /** 耗时(毫秒) */
    private Integer costMs;

    private LocalDateTime createTime;
}
