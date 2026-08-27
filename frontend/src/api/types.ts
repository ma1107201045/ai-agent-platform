/** 通用分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface SysUser {
  id: number
  tenantId: number
  username: string
  nickname?: string
  email?: string
  status: number
  createTime?: string
}

export type AppType = 'chatflow' | 'workflow' | 'agent'

export interface AgentApp {
  id: number
  tenantId: number
  name: string
  description?: string
  type: AppType
  icon?: string
  welcomeMessage?: string
  openingQuestions?: string
  status: number
  publishedVersionId?: number
  createTime?: string
  updateTime?: string
}

export interface AgentAppVersion {
  id: number
  appId: number
  version: number
  workflowJson?: string
  promptConfig?: string
  isPublished: number
  createTime?: string
}

export interface ModelProvider {
  id: number
  name: string
  type: string
  baseUrl?: string
  apiKey?: string
  status: number
  createTime?: string
}

export type ModelType = 'llm' | 'embedding' | 'rerank' | 'tts' | 'asr' | 'image'

export interface ModelInfo {
  id: number
  providerId: number
  name: string
  modelType: ModelType
  contextWindow?: number
  maxTokens?: number
  capabilities?: string
  status: number
}
