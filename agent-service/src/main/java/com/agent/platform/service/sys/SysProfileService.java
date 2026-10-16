package com.agent.platform.service.sys;

import com.agent.platform.dao.vo.sys.SysProfileVO;
import java.util.Map;

/**
 * 账号与安全：个人资料 / 修改密码 / MFA 二次验证
 */
public interface SysProfileService {

    /** 聚合资料 + 安全信息 */
    public SysProfileVO getProfile();

    /** 更新资料（昵称/邮箱/头像/手机号；username 不可改） */
    public SysProfileVO update(Map<String, String> body);

    /** 修改密码 */
    public void changePassword(String oldPassword, String newPassword);

    /**
     * MFA 初始化：校验密码后生成并暂存密钥（尚未启用），返回 secret 与 otpauth URL。
     */
    public Map<String, String> mfaInit(String password);

    /** MFA 启用确认：用动态口令校验密钥后正式开启 */
    public void mfaConfirm(String code);

    /** MFA 关闭：需密码 + 动态口令 */
    public void mfaDisable(String password, String code);

    /** 登录成功后记录登录痕迹（由 SysAuthService 调用） */
    public void recordLogin(Long userId, Long tenantId, String ip);
}
