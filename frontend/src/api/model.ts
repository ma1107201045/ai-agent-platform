import request from './request'
import type { ChatModelInfo, ModelInfo, ModelProvider, PageResult } from './types'

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
  /** 可用向量模型列表 */
  embeddingModels() {
    return request.get<never, ChatModelInfo[]>('/embedding-models')
  },
  /** 可用重排序模型列表 */
  rerankModels() {
    return request.get<never, ChatModelInfo[]>('/rerank-models')
  }
}

