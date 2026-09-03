import request from './request'
import type { ChatChunk, ChatConversation, ChatMessageRecord, PageResult } from './types'

/** 聊天会话 API */
export const conversationApi = {
  /** 当前用户的会话列表 */
  page(params: { appId?: number; page?: number; size?: number }) {
    return request.get<never, PageResult<ChatConversation>>('/chat/conversations', { params })
  },
  /** 创建会话 */
  create(data: { appId: number; title?: string; mode?: string; modelId?: number | null }) {
    return request.post<never, ChatConversation>('/chat/conversations', data)
  },
  get(id: number) {
    return request.get<never, ChatConversation>(`/chat/conversations/${id}`)
  },
  rename(id: number, title: string) {
    return request.put<never, void>(`/chat/conversations/${id}`, { title })
  },
  remove(id: number) {
    return request.delete<never, void>(`/chat/conversations/${id}`)
  },
  /** 会话消息列表 */
  messages(id: number) {
    return request.get<never, ChatMessageRecord[]>(`/chat/conversations/${id}/messages`)
  },
  /** 发送消息（非流式；直连模型 / 工作流通用） */
  send(id: number, data: { content: string; modelId?: number | null }) {
    return request.post<never, ChatMessageRecord>(`/chat/conversations/${id}/messages`, data)
  },
  /**
   * 发送消息（SSE 流式，仅直连模型）
   * @param id
   * @param data
   * @param onChunk 每收到一个增量块回调
   * @param signal
   */
  async streamMessage(
    id: number,
    data: { content: string; modelId?: number | null },
    onChunk: (chunk: ChatChunk) => void,
    signal?: AbortSignal
  ): Promise<string> {
    const token = localStorage.getItem('agent_platform_token') || ''
    const resp = await fetch(`/api/chat/conversations/${id}/messages/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify(data),
      signal
    })
    if (!resp.ok || !resp.body) {
      const err = await resp.text().catch(() => '')
      throw new Error(err || `请求失败(${resp.status})`)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let full = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 按空行切分 SSE 事件
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const event of events) {
        const dataLine = event.split('\n').find((l) => l.startsWith('data:'))
        if (!dataLine) continue
        const data = dataLine.slice(5).trim()
        if (data === '[DONE]') return full
        try {
          const chunk = JSON.parse(data) as ChatChunk
          if (chunk.delta) {
            full += chunk.delta
            onChunk(chunk)
          }
        } catch {
          // 忽略无法解析的数据
        }
      }
    }
    return full
  }
}
