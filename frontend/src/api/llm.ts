import request from './request'
import type { ChatChunk, ChatMessage, ChatModelInfo } from './types'

export const llmApi = {
  /** 可用对话模型列表 */
  chatModels() {
    return request.get<never, ChatModelInfo[]>('/llm/chat-models')
  },

  /**
   * SSE 流式对话
   * @param onChunk 每收到一个增量块回调
   */
  async chatStream(
    params: { modelId: number; messages: ChatMessage[]; temperature?: number; maxTokens?: number },
    onChunk: (chunk: ChatChunk) => void,
    signal?: AbortSignal
  ): Promise<string> {
    const token = localStorage.getItem('agent_platform_token') || ''
    const resp = await fetch('/api/llm/chat-stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify(params),
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
          if (chunk.content) {
            full += chunk.content
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
