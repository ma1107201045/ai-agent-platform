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
  workflowJson?: string
  publishedVersionId?: number
  createTime?: string
  updateTime?: string
}

/** 编排节点类型 */
export type WorkflowNodeType = 'start' | 'end' | 'llm' | 'condition' | 'code' | 'http'

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
}

/** 工作流 DSL */
export interface AppWorkflow {
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

/** SSE 流式块 */
export interface ChatChunk {
  index: number
  content?: string
  finishReason?: string
  usage?: { promptTokens: number; completionTokens: number; totalTokens: number }
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
