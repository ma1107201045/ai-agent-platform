import request from './request'
import type {
  ChatChunk,
  ChatMessage,
  ChatModelInfo,
  ChatResponse,
  EmbeddingResult,
  ModelInfo,
  ModelProvider,
  PageResult
} from './types'

export const modelApi = {
  providerPage(params: { page?: number; size?: number }) {
    return request.get<never, PageResult<ModelProvider>>('/providers', { params })
  },
  createProvider(data: Partial<ModelProvider>) {
    return request.post<never, ModelProvider>('/providers', data)
  },
  updateProvider(id: number, data: Partial<ModelProvider>) {
    return request.put<never, void>(`/providers/${id}`, data)
  },
  removeProvider(id: number) {
    return request.delete<never, void>(`/providers/${id}`)
  },
  modelsOf(providerId: number) {
    return request.get<never, ModelInfo[]>(`/providers/${providerId}/models`)
  },
  createModel(providerId: number, data: Partial<ModelInfo>) {
    return request.post<never, ModelInfo>(`/providers/${providerId}/models`, data)
  },
  removeModel(id: number) {
    return request.delete<never, void>(`/models/${id}`)
  },
  // ---------- 可用模型列表 ----------
  /** 可用对话模型列表 */
  chatModels() {
    return request.get<never, ChatModelInfo[]>('/models/chat-models')
  },
  /** 可用向量模型列表 */
  embeddingModels() {
    return request.get<never, ChatModelInfo[]>('/models/embedding-models')
  },
  /** 可用重排序模型列表 */
  rerankModels() {
    return request.get<never, ChatModelInfo[]>('/models/rerank-models')
  },

  // ---------- 模型调用 ----------
  /** 非流式对话 */
  chat(data: {
    modelId: number
    model?: string
    systemPrompt?: string
    prompt?: string
    messages?: ChatMessage[]
    temperature?: number
    maxTokens?: number
  }) {
    return request.post<never, ChatResponse>('/models/chat', data)
  },
  /** 向量化 */
  embed(data: { modelId: number; texts: string[] }) {
    return request.post<never, EmbeddingResult>('/models/embed', data)
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
    const resp = await fetch('/api/models/chat-stream', {
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

