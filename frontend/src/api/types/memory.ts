/** 记忆策略（mem_strategy，每个应用一条） */
export interface MemStrategy {
  id?: number
  tenantId: number
  appId: number
  /** 是否启用长期记忆：0否 1是 */
  enabled: number
  /** 对话后自动抽取记忆：0否 1是 */
  autoExtract: number
  /** 自动抽取使用的对话模型ID */
  extractModelId?: number | null
  /** 每次对话注入的记忆条目数 */
  topN: number
  /** 记忆保留天数（空 = 永久保留） */
  keepDays?: number | null
  /** 单应用记忆条目上限 */
  maxItems: number
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 会话变量（mem_variable） */
export interface MemVariable {
  id?: number
  tenantId: number
  appId: number
  /** 作用域：global 全局 / session 指定会话 */
  scope: 'global' | 'session'
  /** 所属会话ID（scope=session 时使用，空 = 该应用全部会话） */
  conversationId?: number | null
  /** 变量名（英文下划线） */
  name: string
  value?: string
  /** 类型：string/number/boolean/json */
  valueType: 'string' | 'number' | 'boolean' | 'json'
  remark?: string
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 长期记忆条目（mem_item） */
export interface MemItem {
  id?: number
  tenantId: number
  appId: number
  /** 作用域：global 全局 / user 用户 */
  scope: 'global' | 'user'
  /** 来源：manual 手动 / auto 自动抽取 */
  source: 'manual' | 'auto'
  /** 类别：preference 偏好 / fact 事实 / event 事件 / summary 摘要 / custom 自定义 */
  category: 'preference' | 'fact' | 'event' | 'summary' | 'custom'
  content: string
  /** 重要度 1-5 */
  importance: number
  /** 命中次数 */
  hitCount: number
  lastHitAt?: string
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}
