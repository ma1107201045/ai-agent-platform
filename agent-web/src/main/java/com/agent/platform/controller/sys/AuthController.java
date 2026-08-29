package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.service.sys.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录 / 当前用户
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录 */
    @PostMapping("/login")
    public Result<AuthService.LoginResult> login(@RequestBody @Valid LoginReq req) {
        return Result.ok(authService.login(req.getUsername(), req.getPassword()));
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<AuthService.UserProfile> me() {
        return Result.ok(authService.me());
    }

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
