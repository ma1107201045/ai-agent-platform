package com.agent.platform.service.sys;

import com.agent.platform.dao.entity.sys.SysOperLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 操作日志服务：切面自动落库 + 审计查询
 */
public interface SysOperLogService {

    /** 供切面/登录等调用方记录一条操作日志（用户信息取自当前上下文） */
    void record(String module, String operation, String method, String uri, String ip,
                       boolean success, String errorMsg, long costMs);

    /** 显式指定用户信息（如登录场景上下文尚未建立） */
    void record(String module, String operation, String method, String uri, String ip,
                       boolean success, String errorMsg, long costMs,
                       Long tenantId, Long userId, String username);

    Page<SysOperLog> page(long page, long size, String keyword, String module,
                                 Integer success, String startTime, String endTime);
}
