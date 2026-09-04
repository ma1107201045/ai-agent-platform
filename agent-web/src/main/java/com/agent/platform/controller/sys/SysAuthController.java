package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.sys.SysAuthLoginDTO;
import com.agent.platform.dao.vo.sys.SysAuthLoginVO;
import com.agent.platform.dao.vo.sys.UserProfileVO;
import com.agent.platform.service.sys.SysAuthService;
import com.agent.platform.service.sys.SysOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 认证接口：登录 / 当前用户
 */
@RestController
@RequestMapping("/api/sys/auth")
@RequiredArgsConstructor
public class SysAuthController {

    private final SysAuthService authService;
    private final SysOperLogService operLogService;

    /** 登录（成功与失败均写入审计日志） */
    @PostMapping("/login")
    public Result<SysAuthLoginVO> login(@RequestBody @Valid SysAuthLoginDTO req) {
        long start = System.currentTimeMillis();
        try {
            SysAuthLoginVO vo = authService.login(req.getUsername(), req.getPassword());
            record(req.getUsername(), true, null, start);
            return Result.ok(vo);
        } catch (RuntimeException e) {
            record(req.getUsername(), false, e.getMessage(), start);
            throw e;
        }
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        return Result.ok(authService.me());
    }

    private void record(String username, boolean success, String errorMsg, long start) {
        operLogService.record("认证", success ? "登录成功" : "登录失败", "POST", "/api/sys/auth/login",
                clientIp(), success, errorMsg, System.currentTimeMillis() - start, null, null, username);
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "unknown";
    }
}
