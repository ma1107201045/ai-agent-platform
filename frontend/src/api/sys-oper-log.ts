import request from './request'
import type { PageResult } from './types'

/** 操作日志 */
export interface OperLog {
  id: number
  tenantId: number
  userId?: number
  username?: string
  module?: string
  operation?: string
  method?: string
  uri?: string
  ip?: string
  /** 0失败 1成功 */
  success: number
  errorMsg?: string
  costMs?: number
  createTime?: string
}

export interface OperLogQuery {
  page?: number
  size?: number
  keyword?: string
  module?: string
  success?: number
  startTime?: string
  endTime?: string
}

/** 操作日志 API（/api/sys/oper-logs） */
export const operLogApi = {
  page(params: OperLogQuery) {
    return request.get<never, PageResult<OperLog>>('/sys/oper-logs', { params })
  }
}
