import request from './request'
import type {
  KnowledgeChunk,
  KnowledgeDataset,
  KnowledgeDocument,
  PageResult,
  SearchHit
} from './types'

/** 知识库 API */
export const knowledgeApi = {
  // ---------- 数据集 ----------
  datasetPage(params: { page?: number; size?: number }) {
    return request.get<never, PageResult<KnowledgeDataset>>('/knowledge/datasets', { params })
  },
  getDataset(id: number) {
    return request.get<never, KnowledgeDataset>(`/knowledge/datasets/${id}`)
  },
  createDataset(data: Partial<KnowledgeDataset>) {
    return request.post<never, KnowledgeDataset>('/knowledge/datasets', data)
  },
  updateDataset(id: number, data: Partial<KnowledgeDataset>) {
    return request.put<never, void>(`/knowledge/datasets/${id}`, data)
  },
  removeDataset(id: number) {
    return request.delete<never, void>(`/knowledge/datasets/${id}`)
  },

  // ---------- 文档 ----------
  documentPage(datasetId: number, params: { page?: number; size?: number }) {
    return request.get<never, PageResult<KnowledgeDocument>>(
      `/knowledge/datasets/${datasetId}/documents`,
      { params }
    )
  },
  createDocument(datasetId: number, data: { name: string; content: string }) {
    return request.post<never, KnowledgeDocument>(
      `/knowledge/datasets/${datasetId}/documents`,
      data
    )
  },
  /** 上传文件并解析（txt / md / pdf / docx），自动分块向量化 */
  uploadDocument(datasetId: number, file: File) {
    const form = new FormData()
    form.append('file', file)
    return request.post<never, KnowledgeDocument>(
      `/knowledge/datasets/${datasetId}/documents/upload`,
      form
    )
  },
  reindexDocument(id: number) {
    return request.post<never, KnowledgeDocument>(`/knowledge/documents/${id}/reindex`)
  },
  removeDocument(id: number) {
    return request.delete<never, void>(`/knowledge/documents/${id}`)
  },

  // ---------- 分块 ----------
  chunks(documentId: number) {
    return request.get<never, KnowledgeChunk[]>(`/knowledge/documents/${documentId}/chunks`)
  },

  // ---------- 检索 ----------
  search(
    datasetId: number,
    data: { query: string; topK?: number; rerankModelId?: number | null }
  ) {
    return request.post<never, SearchHit[]>(`/knowledge/datasets/${datasetId}/search`, data)
  }
}
