package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.vo.sys.SysProfileVO;
import com.agent.platform.service.sys.SysProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 账号与安全 / 个人中心接口
 *
 * <p>URL：/api/sys/profile</p>
 */
@RestController
@RequestMapping("/api/sys/profile")
@RequiredArgsConstructor
public class SysProfileController {

    private final SysProfileService profileService;

    /** 个人资料 + 账号安全聚合信息 */
    @GetMapping
    public Result<SysProfileVO> get() {
        return Result.ok(profileService.getProfile());
    }

    /** 更新资料：nickname/email/avatar/phone */
    @PutMapping
    public Result<SysProfileVO> update(@RequestBody Map<String, String> body) {
        return Result.ok(profileService.update(body));
    }

    /** 修改密码 */
    @PutMapping("/password")
    public Result<Void> password(@RequestBody Map<String, String> body) {
        profileService.changePassword(body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }

    /** MFA 初始化（需密码），返回 {secret, otpauthUrl} */
    @PostMapping("/mfa/init")
    public Result<Map<String, String>> mfaInit(@RequestBody Map<String, String> body) {
        return Result.ok(profileService.mfaInit(body.get("password")));
    }

    /** MFA 启用确认（需动态口令） */
    @PostMapping("/mfa/confirm")
    public Result<Void> mfaConfirm(@RequestBody Map<String, String> body) {
        profileService.mfaConfirm(body.get("code"));
        return Result.ok();
    }

    /** MFA 关闭（需密码 + 动态口令） */
    @PostMapping("/mfa/disable")
    public Result<Void> mfaDisable(@RequestBody Map<String, String> body) {
        profileService.mfaDisable(body.get("password"), body.get("code"));
        return Result.ok();
    }
}
