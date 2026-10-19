import request from './request'
import { chatDeltaStream } from '@/utils/stream'
import type { ChatChunk, ChatConversation, ChatMessageRecord, PageResult } from './types'

/** 聊天会话 API */
export const conversationApi = {
  /** 当前用户的会话列表 */
  page(params: { appId?: number; mode?: string; page?: number; size?: number }) {
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
  /** 发送消息（非流式；直连模型 / 工作流通用），支持 AbortSignal 取消 */
  send(id: number, data: { content: string; modelId?: number | null }, signal?: AbortSignal) {
    return request.post<never, ChatMessageRecord>(`/chat/conversations/${id}/messages`, data, {
      signal
    })
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
    return chatDeltaStream<ChatChunk>(
      `/api/chat/conversations/${id}/messages/stream`,
      data,
      onChunk,
      signal
    )
  }
}
