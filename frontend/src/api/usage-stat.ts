import request from './request'
import type { UsageSummary } from './types'

/** 用量统计查询参数 */
export interface UsageQuery {
  /** 应用过滤（空 = 全部） */
  appId?: number
  /** 起始日期 yyyy-MM-dd（空 = 最近 7 天） */
  startDate?: string
  /** 结束日期 yyyy-MM-dd（空 = 今天） */
  endDate?: string
}

export const usageApi = {
  /**
   * 区间用量总览：汇总指标 + 按日趋势 + 应用/模型维度排行
   */
  summary(params: UsageQuery = {}) {
    return request.get<never, UsageSummary>('/chat/stats/usage', { params })
  },
}
