import request from './request'

/** 个人中心 / 账号安全聚合信息 */
export interface SysProfile {
  id: number
  tenantId: number
  username: string
  nickname?: string
  email?: string
  avatar?: string
  status: number
  phone?: string
  mfaEnabled: number
  mfaBoundAt?: string
  lastLoginAt?: string
  lastLoginIp?: string
  loginCount: number
}

/** 账号与安全 API（对应后端 /api/sys/profile） */
export const userSecurityApi = {
  get() {
    return request.get<never, SysProfile>('/sys/profile')
  },
  update(data: { nickname?: string; email?: string; avatar?: string; phone?: string }) {
    return request.put<never, SysProfile>('/sys/profile', data)
  },
  changePassword(data: { oldPassword: string; newPassword: string }) {
    return request.put<never, void>('/sys/profile/password', data)
  },
  /** MFA 初始化（需当前登录密码），返回 {secret, otpauthUrl} */
  mfaInit(data: { password: string }) {
    return request.post<never, { secret: string; otpauthUrl: string }>('/sys/profile/mfa/init', data)
  },
  /** MFA 启用确认（动态口令） */
  mfaConfirm(data: { code: string }) {
    return request.post<never, void>('/sys/profile/mfa/confirm', data)
  },
  /** MFA 关闭（密码 + 动态口令） */
  mfaDisable(data: { password: string; code: string }) {
    return request.post<never, void>('/sys/profile/mfa/disable', data)
  }
}
