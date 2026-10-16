import request from './request'
import { chatDeltaStream } from '@/utils/stream'
import type {
  ChatChunk,
  ChatMessage,
  ChatModelInfo,
  ChatResponse,
  EmbeddingResult,
  ModelInfo,
  ModelPlaygroundItem,
  ModelProvider,
  PageResult
} from './types'

export const modelApi = {
  providerPage(params: { page?: number; size?: number; keyword?: string }) {
    return request.get<never, PageResult<ModelProvider>>('/model/providers', { params })
  },
  createProvider(data: Partial<ModelProvider>) {
    return request.post<never, ModelProvider>('/model/providers', data)
  },
  updateProvider(id: number, data: Partial<ModelProvider>) {
    return request.put<never, void>(`/model/providers/${id}`, data)
  },
  removeProvider(id: number) {
    return request.delete<never, void>(`/model/providers/${id}`)
  },
  modelsOf(providerId: number) {
    return request.get<never, ModelInfo[]>(`/model/providers/${providerId}/models`)
  },
  createModel(providerId: number, data: Partial<ModelInfo>) {
    return request.post<never, ModelInfo>(`/model/providers/${providerId}/models`, data)
  },
  updateModel(id: number, data: Partial<ModelInfo>) {
    return request.put<never, void>(`/model/models/${id}`, data)
  },
  removeModel(id: number) {
    return request.delete<never, void>(`/model/models/${id}`)
  },
  // ---------- 可用模型列表 ----------
  /** 全部启用模型（含供应商名，模型网关等场景使用） */
  allModels() {
    return request.get<never, ChatModelInfo[]>('/model/models/all')
  },
  /** 可用对话模型列表 */
  chatModels() {
    return request.get<never, ChatModelInfo[]>('/model/models/chat-models')
  },
  /** 可用向量模型列表 */
  embeddingModels() {
    return request.get<never, ChatModelInfo[]>('/model/models/embedding-models')
  },
  /** 可用重排序模型列表 */
  rerankModels() {
    return request.get<never, ChatModelInfo[]>('/model/models/rerank-models')
  },
  /** 模型广场目录（含供应商与模型可用状态） */
  playgroundModels() {
    return request.get<never, ModelPlaygroundItem[]>('/model/models/playground-models')
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
    return request.post<never, ChatResponse>('/model/models/chat', data)
  },
  /** 向量化 */
  embed(data: { modelId: number; texts: string[] }) {
    return request.post<never, EmbeddingResult>('/model/models/embed', data)
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
    return chatDeltaStream<ChatChunk>('/api/model/models/chat-stream', params, onChunk, signal)
  }
}
