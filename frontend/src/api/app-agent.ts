import request from './request'
import type {
  AgentResult,
  AppAgent,
  AppAgentStats,
  AppAgentVersion,
  ChatMessage,
  PageResult,
  RunResult
} from './types'

/** 智能体应用（AppAgent）API，与后端 AppAgentController（/api/app/agents）一一对应 */
export const appAgentApi = {
  page(params: { page?: number; size?: number; keyword?: string; type?: string }) {
    return request.get<never, PageResult<AppAgent>>('/app/agents', { params })
  },
  get(id: number) {
    return request.get<never, AppAgent>(`/app/agents/${id}`)
  },
  create(data: Partial<AppAgent>) {
    return request.post<never, AppAgent>('/app/agents', data)
  },
  update(id: number, data: Partial<AppAgent>) {
    return request.put<never, void>(`/app/agents/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/agents/${id}`)
  },
  publish(id: number, data: { workflowJson?: string; promptConfig?: string }) {
    return request.post<never, AppAgentVersion>(`/app/agents/${id}/publish`, data)
  },
  published(id: number) {
    return request.get<never, AppAgentVersion>(`/app/agents/${id}/published`)
  },
  /** 版本列表（按版本号倒序） */
  versions(id: number) {
    return request.get<never, AppAgentVersion[]>(`/app/agents/${id}/versions`)
  },
  /** 回滚到指定版本（恢复草稿，不自动发布） */
  rollback(id: number, versionId: number) {
    return request.post<never, AppAgentVersion>(`/app/agents/${id}/versions/${versionId}/rollback`)
  },
  /** 批量获取发布版本，返回 Map<appId, version> */
  batchPublished(ids: number[]) {
    return request.get<never, Record<number, AppAgentVersion>>('/app/agents/batch/published', {
      params: { ids: ids.join(',') }
    })
  },
  /** 批量会话统计（对外访问 / 运营数据） */
  batchStats(ids: number[]) {
    return request.get<never, Record<number, AppAgentStats>>('/app/agents/batch/stats', {
      params: { ids: ids.join(',') }
    })
  },
  run(id: number, messages: ChatMessage[]) {
    return request.post<never, RunResult>(`/app/agents/${id}/run`, { messages })
  },
  /** Agent 自主对话（非流式）：规划-工具调用-观察循环 */
  agentChat(
    appId: number,
    data: { modelId: number; systemPrompt?: string; messages: ChatMessage[]; maxIterations?: number }
  ) {
    return request.post<never, AgentResult>(`/app/agents/${appId}/agent/chat`, data)
  }
}
