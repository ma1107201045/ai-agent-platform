import request from './request'
import type { PageResult } from './types'

/** 站内通知 */
export interface SysNotification {
  id: number
  tenantId: number
  userId: number
  /** system 系统 / announcement 公告 / run 任务 / alert 告警 */
  type: string
  title: string
  content?: string
  bizType?: string
  bizId?: number
  /** 是否已读：0未读 1已读 */
  read: number
  readTime?: string
  createTime?: string
}

/** 站内通知 API（/api/sys/notifications） */
export const notificationApi = {
  page(params: { page?: number; size?: number; type?: string; read?: number }) {
    return request.get<never, PageResult<SysNotification>>('/sys/notifications', { params })
  },
  unreadCount() {
    return request.get<never, number>('/sys/notifications/unread-count')
  },
  markRead(id: number) {
    return request.put<never, void>(`/sys/notifications/${id}/read`)
  },
  readAll() {
    return request.put<never, void>('/sys/notifications/read-all')
  },
  remove(id: number) {
    return request.delete<never, void>(`/sys/notifications/${id}`)
  }
}
