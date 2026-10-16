/** 用量统计：按日趋势条目 */
export interface UsageTrendPoint {
  date: string
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
}

/** 用量统计：应用维度 */
export interface UsageAppRow {
  appId: number | null
  appName: string
  conversations: number
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  cost: number
}

/** 用量统计：模型维度 */
export interface UsageModelRow {
  modelId: number | null
  modelName: string
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  cost: number
}

/** 用量统计总览（时间区间） */
export interface UsageSummary {
  conversations: number
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  /** 估算成本（元） */
  cost: number
  /** 查询起始日期 yyyy-MM-dd */
  startDate: string
  /** 查询结束日期 yyyy-MM-dd */
  endDate: string
  /** 按日趋势（连续日期，空值补 0） */
  trend: UsageTrendPoint[]
  /** 应用维度排行 */
  apps: UsageAppRow[]
  /** 模型维度排行 */
  models: UsageModelRow[]
}
