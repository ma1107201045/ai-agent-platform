package com.agent.platform.service.sys;

import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.sys.SysOperLog;
import com.agent.platform.dao.mapper.sys.SysOperLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 操作日志服务：切面自动落库 + 审计查询
 */
@Service
@RequiredArgsConstructor
public class SysOperLogService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysOperLogMapper operLogMapper;

    /** 供切面/登录等调用方记录一条操作日志（用户信息取自当前上下文） */
    public void record(String module, String operation, String method, String uri, String ip,
                       boolean success, String errorMsg, long costMs) {
        record(module, operation, method, uri, ip, success, errorMsg, costMs,
                UserContext.getTenantId(), UserContext.getUserId(), UserContext.getUsername());
    }

    /** 显式指定用户信息（如登录场景上下文尚未建立） */
    public void record(String module, String operation, String method, String uri, String ip,
                       boolean success, String errorMsg, long costMs,
                       Long tenantId, Long userId, String username) {
        try {
            SysOperLog log = new SysOperLog();
            log.setTenantId(defaultTenant(tenantId));
            log.setUserId(userId);
            log.setUsername(truncate(username, 64));
            log.setModule(truncate(module, 64));
            log.setOperation(truncate(operation, 128));
            log.setMethod(method);
            log.setUri(truncate(uri, 255));
            log.setIp(truncate(ip, 64));
            log.setSuccess(success ? 1 : 0);
            log.setErrorMsg(truncate(errorMsg, 1000));
            log.setCostMs((int) costMs);
            log.setCreateTime(LocalDateTime.now());
            operLogMapper.insert(log);
        } catch (Exception ignore) {
            // 审计落库失败不影响主流程
        }
    }

    public Page<SysOperLog> page(long page, long size, String keyword, String module,
                                 Integer success, String startTime, String endTime) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .eq(SysOperLog::getTenantId, defaultTenant(UserContext.getTenantId()))
                .orderByDesc(SysOperLog::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysOperLog::getUsername, keyword)
                    .or().like(SysOperLog::getModule, keyword)
                    .or().like(SysOperLog::getOperation, keyword)
                    .or().like(SysOperLog::getUri, keyword));
        }
        if (StringUtils.hasText(module)) {
            wrapper.eq(SysOperLog::getModule, module);
        }
        if (success != null && (success == 0 || success == 1)) {
            wrapper.eq(SysOperLog::getSuccess, success);
        }
        LocalDateTime start = parseTime(startTime, true);
        LocalDateTime end = parseTime(endTime, false);
        if (start != null) {
            wrapper.ge(SysOperLog::getCreateTime, start);
        }
        if (end != null) {
            wrapper.le(SysOperLog::getCreateTime, end);
        }
        return operLogMapper.selectPage(new Page<>(page, size), wrapper);
    }

    private LocalDateTime parseTime(String text, boolean startOfDay) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        try {
            if (value.length() <= 10) {
                LocalDate date = LocalDate.parse(value);
                return startOfDay ? date.atStartOfDay() : date.atTime(LocalTime.MAX);
            }
            if (value.contains("T")) {
                return LocalDateTime.parse(value, ISO);
            }
            return LocalDateTime.parse(value, SPACE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private Long defaultTenant(Long tenantId) {
        return tenantId == null ? 1L : tenantId;
    }
}
