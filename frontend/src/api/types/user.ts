export interface SysUser {
  id: number
  tenantId: number
  username: string
  nickname?: string
  email?: string
  status: number
  createTime?: string
}

/** 当前登录用户信息（不含密码） */
export interface UserProfile {
  id: number
  tenantId: number
  username: string
  nickname?: string
  email?: string
  avatar?: string
  status: number
}

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  user: UserProfile
}
