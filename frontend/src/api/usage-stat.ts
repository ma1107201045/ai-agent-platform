import request from './request'
import type { UsageOverview } from './types'

/** 用量统计 API（运营侧） */
export const usageApi = {
  /** 最近 N 天用量总览 */
  overview(days = 30) {
    return request.get<never, UsageOverview>('/chat/stats/usage', { params: { days } })
  }
}
