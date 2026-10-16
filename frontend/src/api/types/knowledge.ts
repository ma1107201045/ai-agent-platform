/** 知识库数据集 */
export interface KnowledgeDataset {
  id: number
  tenantId: number
  name: string
  description?: string
  embeddingModel?: number
  chunkSize?: number
  chunkOverlap?: number
  status: number
  createTime?: string
  updateTime?: string
}

/** 知识库文档 */
export interface KnowledgeDocument {
  id: number
  datasetId: number
  name: string
  content?: string
  charCount?: number
  chunkCount?: number
  status: 'pending' | 'indexing' | 'ready' | 'failed'
  errorMsg?: string
  createTime?: string
  updateTime?: string
}

/** 知识库分块 */
export interface KnowledgeChunk {
  id: number
  datasetId: number
  documentId: number
  chunkIndex: number
  content: string
  charCount?: number
  createTime?: string
}

/** 知识库检索命中 */
export interface SearchHit {
  id: number
  documentId: number
  chunkIndex: number
  content: string
  score: number
}
