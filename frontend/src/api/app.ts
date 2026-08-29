import request from './request'
import type { App, AppVersion, AppStats, ChatMessage, PageResult, RunResult } from './types'

export const appApi = {
  page(params: { page?: number; size?: number; keyword?: string; type?: string }) {
    return request.get<never, PageResult<App>>('/apps', { params })
  },
  get(id: number) {
    return request.get<never, App>(`/apps/${id}`)
  },
  create(data: Partial<App>) {
    return request.post<never, App>('/apps', data)
  },
  update(id: number, data: Partial<App>) {
    return request.put<never, void>(`/apps/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/apps/${id}`)
  },
  publish(id: number, data: { workflowJson?: string; promptConfig?: string }) {
    return request.post<never, AppVersion>(`/apps/${id}/publish`, data)
  },
  published(id: number) {
    return request.get<never, AppVersion>(`/apps/${id}/published`)
  },
  /** 版本列表（按版本号倒序） */
  versions(id: number) {
    return request.get<never, AppVersion[]>(`/apps/${id}/versions`)
  },
  /** 回滚到指定版本（恢复草稿，不自动发布） */
  rollback(id: number, versionId: number) {
    return request.post<never, AppVersion>(`/apps/${id}/versions/${versionId}/rollback`)
  },
  /** 批量获取发布版本，返回 Map<appId, version> */
  publishedBatch(ids: number[]) {
    return request.get<never, Record<number, AppVersion>>('/apps/published/batch', {
      params: { ids: ids.join(',') }
    })
  },
  run(id: number, messages: ChatMessage[]) {
    return request.post<never, RunResult>(`/apps/${id}/run`, { messages })
  },
  /** 批量会话统计（对外访问 / 运营数据） */
  batchStats(ids: number[]) {
    return request.get<never, Record<number, AppStats>>('/apps/stats/batch', {
      params: { ids: ids.join(',') }
    })
  }
}
