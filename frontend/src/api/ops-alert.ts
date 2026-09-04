import request from './request'
import type { PageResult } from './types'

/** 告警规则 */
export interface AlertRule {
  id: number
  name: string
  /** error_rate 错误率 / failures 运行失败数 / latency 平均延迟 / cost 成本 */
  metric: string
  operator: string
  threshold: number
  windowMinutes: number
  /** warning / critical */
  level: string
  /** notification,email,webhook */
  channels: string
  webhookUrl?: string
  enabled: number
  remark?: string
  lastFireTime?: string
  createTime?: string
  updateTime?: string
}

/** 告警事件 */
export interface AlertEvent {
  id: number
  ruleId?: number
  ruleName?: string
  metric?: string
  level: string
  content?: string
  /** open / handled / ignored */
  status: string
  /** manual / auto */
  source: string
  triggerTime?: string
  handledTime?: string
}

export interface AlertEventStats {
  open: number
  today: number
}

/** 告警管理 API（/api/ops/alerts） */
export const alertApi = {
  rulePage(params: { page?: number; size?: number; keyword?: string }) {
    return request.get<never, PageResult<AlertRule>>('/ops/alerts/rules', { params })
  },
  createRule(data: Partial<AlertRule>) {
    return request.post<never, AlertRule>('/ops/alerts/rules', data)
  },
  updateRule(id: number, data: Partial<AlertRule>) {
    return request.put<never, AlertRule>(`/ops/alerts/rules/${id}`, data)
  },
  toggleRule(id: number, enabled: number) {
    return request.put<never, void>(`/ops/alerts/rules/${id}/enabled`, { enabled })
  },
  removeRule(id: number) {
    return request.delete<never, void>(`/ops/alerts/rules/${id}`)
  },
  events(params: { page?: number; size?: number; status?: string; keyword?: string }) {
    return request.get<never, PageResult<AlertEvent>>('/ops/alerts/events', { params })
  },
  eventStats() {
    return request.get<never, AlertEventStats>('/ops/alerts/events/stats')
  },
  fireTest(ruleId: number) {
    return request.post<never, AlertEvent>('/ops/alerts/events/test', { ruleId })
  },
  setEventStatus(id: number, status: string) {
    return request.put<never, void>(`/ops/alerts/events/${id}/status`, { status })
  },
  removeEvent(id: number) {
    return request.delete<never, void>(`/ops/alerts/events/${id}`)
  }
}
