import request from './request'
import type { AgentApp, AgentAppVersion, ChatMessage, PageResult, RunResult } from './types'

export const appApi = {
  page(params: { page?: number; size?: number }) {
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
  run(id: number, messages: ChatMessage[]) {
    return request.post<never, RunResult>(`/apps/${id}/run`, { messages })
  }
}
