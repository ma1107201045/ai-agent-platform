import request from './request'
import type { AgentStep, TraceItem } from './types'

/** 公开应用信息（无需登录获取） */
export interface PublicAppInfo {
  id: number
  name: string
  type: string
  description?: string
  welcomeMessage?: string
  openingQuestions?: string
}

/** 公开对话结果 */
export interface PublicChatResult {
  answer: string
  /** workflow → 节点轨迹 TraceItem[]；agent → 工具步骤 AgentStep[]；chatflow → null */
  detail?: TraceItem[] | AgentStep[] | null
}

/** 公开访问 API（无鉴权） */
export const publicApi = {
  getApp(id: number) {
    return request.get<never, PublicAppInfo>(`/public/apps/${id}`)
  },
  chat(id: number, messages: { role: string; content: string }[]) {
    return request.post<never, PublicChatResult>(`/public/apps/${id}/chat`, { messages })
  }
}
