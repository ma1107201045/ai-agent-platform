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

/** 当前登录用户信息（不含密码） */
export interface UserProfile {
  id: number
  tenantId: number
  username: string
  nickname?: string
  email?: string
  avatar?: string
  status: number
}

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  user: UserProfile
}

export type AppAgentType = 'chatflow' | 'workflow' | 'agent'

export interface AppAgent {
  id: number
  tenantId: number
  name: string
  description?: string
  type: AppAgentType
  icon?: string
  welcomeMessage?: string
  openingQuestions?: string
  status: number
  workflowJson?: string
  toolIds?: string
  datasetIds?: string
  publishedVersionId?: number
  createTime?: string
  updateTime?: string
}

/** Agent 工具 */
export interface AppAgentTool {
  id: number
  tenantId: number
  name: string
  description?: string
  type: 'http' | 'code'
  url?: string
  method?: string
  headers?: string
  authType?: 'none' | 'bearer' | 'basic'
  authToken?: string
  authUsername?: string
  authPassword?: string
  parameters?: string
  code?: string
  status: number
  createTime?: string
  updateTime?: string
}

/** Agent 工具调用步骤 */
export interface AgentStep {
  toolName: string
  arguments: string
  result: string
  costMs: number
}

/** Agent 执行结果 */
export interface AgentResult {
  answer: string
  steps: AgentStep[]
  /** 输入 Token（含工具调用轮次累计） */
  promptTokens?: number
  /** 输出 Token（含工具调用轮次累计） */
  completionTokens?: number
  /** 全流程累计 Token 总量 */
  totalTokens?: number
}

/** 编排节点类型 */
export type WorkflowNodeType =
  | 'start'
  | 'end'
  | 'llm'
  | 'agent'
  | 'condition'
  | 'code'
  | 'http'
  | 'template'
  | 'knowledge'

/** DSL 节点定义 */
export interface WorkflowNode {
  id: string
  type: WorkflowNodeType
  label?: string
  position?: { x: number; y: number }
  config?: Record<string, unknown>
}

/** DSL 连线定义 */
export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  /** 连线文字标注 */
  label?: string
}

/** 工作流 DSL */
export interface AppAgentWorkflow {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

export interface ChatModelInfo {
  id: number
  providerName: string
  modelName: string
  contextWindow?: number
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

/** 聊天会话 */
export interface ChatConversation {
  id: number
  tenantId: number
  userId: number
  appId: number
  title?: string
  mode: 'direct' | 'workflow' | 'agent'
  modelId?: number
  status: number
  createTime?: string
  updateTime?: string
}

/** 持久化聊天消息（后端实体） */
export interface ChatMessageRecord {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content?: string
  traceJson?: string
  tokens?: number
  status: number
  createTime?: string
}

/** 工作流节点执行轨迹 */
export interface TraceItem {
  nodeId: string
  nodeType: string
  label: string
  status: 'success' | 'skipped' | 'error'
  input?: string
  output?: string
  costMs: number
  error?: string
}

/** 工作流运行结果 */
export interface RunResult {
  answer: string
  trace: TraceItem[]
}

/** 应用会话统计 */
export interface AppAgentStats {
  conversationCount: number
  messageCount: number
}

export interface AppAgentVersion {
  id: number
  appId: number
  version: number
  workflowJson?: string
  promptConfig?: string
  isPublished: number
  createdBy?: number
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

/** 应用 API 密钥（明文仅创建/轮换时返回一次，落库仅存哈希 + 前缀） */
export interface AppApiKey {
  id: number
  tenantId: number
  /** 关联应用 ID */
  appId: number
  /** 关联应用名称（服务端填充） */
  appName?: string
  /** 密钥名称（用途标识） */
  name: string
  /** 密钥前缀（列表展示用） */
  keyPrefix: string
  /** 状态：0禁用 1启用 */
  status: number
  /** 过期时间（空 = 永不过期） */
  expiresAt?: string
  /** 每分钟请求上限（空 = 不限流） */
  rateLimit?: number
  /** 累计调用次数 */
  usageCount: number
  /** 最近使用时间 */
  lastUsedAt?: string
  remark?: string
  /** 明文密钥（仅创建/轮换接口返回一次） */
  plainKey?: string
  createTime?: string
  updateTime?: string
}

/** 提示词模板（支持 {{var}} 变量占位，版本留痕） */
export interface PromptTemplate {
  id: number
  tenantId: number
  name: string
  description?: string
  /** 分类: general 通用 / system 系统 / business 业务 / custom 自定义 */
  category?: string
  /** 模板正文（支持 {{var}} 占位） */
  content: string
  /** 变量定义（JSON 数组：[{"name":"var","desc":"说明"}]） */
  variables?: string
  /** 当前版本号 */
  version: number
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 提示词模板版本快照（留痕/回退） */
export interface PromptTemplateVersion {
  id: number
  templateId: number
  version: number
  content: string
  variables?: string
  remark?: string
  createdBy?: number
  createTime?: string
}

/** 提示词变量定义项 */
export interface PromptVariableDef {
  name: string
  desc?: string
}

/** 用量统计：按日趋势条目 */
export interface UsageTrendPoint {
  date: string
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
}

/** 用量统计：应用维度 */
export interface UsageAppRow {
  appId: number | null
  appName: string
  conversations: number
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  cost: number
}

/** 用量统计：模型维度 */
export interface UsageModelRow {
  modelId: number | null
  modelName: string
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  cost: number
}

/** 用量统计总览（时间区间） */
export interface UsageSummary {
  conversations: number
  calls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  /** 估算成本（元） */
  cost: number
  /** 查询起始日期 yyyy-MM-dd */
  startDate: string
  /** 查询结束日期 yyyy-MM-dd */
  endDate: string
  /** 按日趋势（连续日期，空值补 0） */
  trend: UsageTrendPoint[]
  /** 应用维度排行 */
  apps: UsageAppRow[]
  /** 模型维度排行 */
  models: UsageModelRow[]
}
