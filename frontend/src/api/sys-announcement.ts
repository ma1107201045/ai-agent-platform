import request from './request'
import type { PageResult } from './types'

/** 平台公告 */
export interface SysAnnouncement {
  id: number
  tenantId: number
  title: string
  content?: string
  /** all 全部用户 */
  scope: string
  /** 状态：0草稿 1发布中 2已下线 */
  status: number
  /** 是否置顶：0否 1是 */
  pinned: number
  publishTime?: string
  offlineTime?: string
  publisher?: number
  createTime?: string
  updateTime?: string
}

export interface AnnouncementPayload {
  title: string
  content?: string
  scope?: string
  status?: number
  pinned?: number
}

/** 平台公告 API（/api/sys/announcements） */
export const announcementApi = {
  page(params: { page?: number; size?: number; keyword?: string; status?: number }) {
    return request.get<never, PageResult<SysAnnouncement>>('/sys/announcements', { params })
  },
  get(id: number) {
    return request.get<never, SysAnnouncement>(`/sys/announcements/${id}`)
  },
  create(data: AnnouncementPayload) {
    return request.post<never, SysAnnouncement>('/sys/announcements', data)
  },
  update(id: number, data: AnnouncementPayload) {
    return request.put<never, void>(`/sys/announcements/${id}`, data)
  },
  publish(id: number) {
    return request.post<never, void>(`/sys/announcements/${id}/publish`)
  },
  offline(id: number) {
    return request.post<never, void>(`/sys/announcements/${id}/offline`)
  },
  remove(id: number) {
    return request.delete<never, void>(`/sys/announcements/${id}`)
  }
}
