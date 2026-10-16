/** 应用 API 密钥（明文仅创建/轮换时返回一次，落库仅存哈希 + 前缀） */
export interface AppApiKey {
  id: number
  tenantId: number
  /** 关联应用 ID */
  appId: number
  /** 关联应用名称（服务端填充） */
  appName?: string
  /** 密钥名称（用途标识） */
  name: string
  /** 密钥前缀（列表展示用） */
  keyPrefix: string
  /** 状态：0禁用 1启用 */
  status: number
  /** 过期时间（空 = 永不过期） */
  expiresAt?: string
  /** 每分钟请求上限（空 = 不限流） */
  rateLimit?: number
  /** 累计调用次数 */
  usageCount: number
  /** 最近使用时间 */
  lastUsedAt?: string
  remark?: string
  /** 明文密钥（仅创建/轮换接口返回一次） */
  plainKey?: string
  createTime?: string
  updateTime?: string
}
