package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.sys.SysAuthLoginDTO;
import com.agent.platform.dao.vo.sys.SysAuthLoginVO;
import com.agent.platform.dao.vo.sys.UserProfileVO;
import com.agent.platform.service.sys.SysAuthService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/sys/auth")
@RequiredArgsConstructor
public class SysAuthController {

    private final SysAuthService authService;

    /** 登录 */
    @PostMapping("/login")
    public Result<SysAuthLoginVO> login(@RequestBody @Valid SysAuthLoginDTO req) {
        return Result.ok(authService.login(req.getUsername(), req.getPassword()));
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        return Result.ok(authService.me());
    }
}
