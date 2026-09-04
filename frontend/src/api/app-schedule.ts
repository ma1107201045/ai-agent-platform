import request from './request'
import type { PageResult } from './types'

/** 定时任务 */
export interface AppSchedule {
  id: number
  tenantId: number
  name: string
  appId: number
  appName?: string
  /** interval间隔 / daily每天 / weekly每周 */
  triggerType: string
  /** 间隔分钟 */
  intervalMinutes?: number
  /** HH:mm */
  runTime?: string
  /** 1-7 周一~周日 */
  runWeekday?: number
  /** 触发输入 */
  inputMessage?: string
  enabled: number
  lastRunTime?: string
  nextRunTime?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 执行记录 */
export interface AppScheduleLog {
  id: number
  scheduleId: number
  scheduleName?: string
  appId?: number
  appName?: string
  /** scheduled/manual */
  triggerBy: string
  /** success/failed */
  status: string
  message?: string
  costMs?: number
  createTime?: string
}

export interface SchedulePayload {
  name: string
  appId?: number
  triggerType?: string
  intervalMinutes?: number
  runTime?: string
  runWeekday?: number
  inputMessage?: string
  enabled?: number
  remark?: string
}

export interface RunOutcome {
  status: string
  message: string
  costMs: string
}

/** 定时任务 API（/api/app/schedules） */
export const scheduleApi = {
  page(params: { page?: number; size?: number; keyword?: string; enabled?: number }) {
    return request.get<never, PageResult<AppSchedule>>('/app/schedules', { params })
  },
  get(id: number) {
    return request.get<never, AppSchedule>(`/app/schedules/${id}`)
  },
  create(data: SchedulePayload) {
    return request.post<never, AppSchedule>('/app/schedules', data)
  },
  update(id: number, data: SchedulePayload) {
    return request.put<never, void>(`/app/schedules/${id}`, data)
  },
  setEnabled(id: number, enabled: boolean) {
    return request.put<never, void>(`/app/schedules/${id}/enabled`, null, { params: { enabled } })
  },
  run(id: number) {
    return request.post<never, RunOutcome>(`/app/schedules/${id}/run`)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/schedules/${id}`)
  },
  logs(id: number, params: { page?: number; size?: number }) {
    return request.get<never, PageResult<AppScheduleLog>>(`/app/schedules/${id}/logs`, { params })
  }
}
