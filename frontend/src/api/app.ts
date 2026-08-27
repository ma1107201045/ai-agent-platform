import request from './request'
import type { AgentApp, AgentAppVersion, AppStats, ChatMessage, PageResult, RunResult } from './types'

export const appApi = {
  page(params: { page?: number; size?: number; keyword?: string; type?: string }) {
    return request.get<never, PageResult<AgentApp>>('/apps', { params })
  },
  get(id: number) {
    return request.get<never, AgentApp>(`/apps/${id}`)
  },
  create(data: Partial<AgentApp>) {
    return request.post<never, AgentApp>('/apps', data)
  },
  update(id: number, data: Partial<AgentApp>) {
    return request.put<never, void>(`/apps/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/apps/${id}`)
  },
  publish(id: number, data: { workflowJson?: string; promptConfig?: string }) {
    return request.post<never, AgentAppVersion>(`/apps/${id}/publish`, data)
  },
  published(id: number) {
    return request.get<never, AgentAppVersion>(`/apps/${id}/published`)
  },
  /** 批量获取发布版本，返回 Map<appId, version> */
  publishedBatch(ids: number[]) {
    return request.get<never, Record<number, AgentAppVersion>>('/apps/published/batch', {
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
