export interface ChatModelInfo {
  id: number
  providerName: string
  modelName: string
  contextWindow?: number
}

/** 模型广场目录项 */
export interface ModelPlaygroundItem {
  modelId: number
  modelName: string
  /** llm / embedding / rerank / tts / asr / image */
  modelType: string
  contextWindow?: number
  maxTokens?: number
  /** 能力标签：function_call / vision / stream / json 等 */
  capabilities?: string[]
  /** 模型状态：0禁用 1启用 */
  modelStatus: number
  providerId: number
  providerName: string
  /** 供应商类型：openai-compatible / anthropic 等 */
  providerType?: string
  /** 供应商状态：0禁用 1启用 */
  providerStatus: number
}

/** 对话消息（与后端 LLM 模型一致） */
export interface ChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

/** Token 用量统计 */
export interface Usage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

/** 工具调用 */
export interface ToolCall {
  id: string
  name: string
  arguments: string
}

/** 非流式对话响应 */
export interface ChatResponse {
  content?: string
  toolCalls?: ToolCall[]
  finishReason?: string
  usage?: Usage
  model?: string
}

/** 向量化结果 */
export interface EmbeddingResult {
  vectors: number[][]
  usage?: Usage
}

/** SSE 流式块（后端 ChatChunk：delta 为内容增量） */
export interface ChatChunk {
  delta?: string
  finishReason?: string
  usage?: { promptTokens: number; completionTokens: number; totalTokens: number }
}

export interface ModelProvider {
  id: number
  tenantId?: number
  /** 供应商名称（展示用） */
  name: string
  /** 供应商类型：openai-compatible / anthropic / ... */
  type: string
  /** API 基础地址 */
  baseUrl?: string
  /** API Key */
  apiKey?: string
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

export type ModelType = 'llm' | 'embedding' | 'rerank' | 'tts' | 'asr' | 'image'

export interface ModelInfo {
  id: number
  providerId: number
  /** 模型名（调用 API 时使用） */
  name: string
  modelType: ModelType
  /** 上下文窗口 */
  contextWindow?: number
  /** 最大输出 Token */
  maxTokens?: number
  /** 能力标签（JSON 数组字符串，如 ["function_call","vision"]） */
  capabilities?: string
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}
