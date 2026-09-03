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

/** 节点配置字段控件类型（与后端 orchestrator NodeField.type 一致） */
export type NodeFieldType =
  | 'text'
  | 'textarea'
  | 'code'
  | 'password'
  | 'number'
  | 'boolean'
  | 'select'
  | 'model'
  | 'knowledge'
  | 'tools'
  | 'datasets'
  | 'json'
  | 'branches'

/** select 下拉选项 */
export interface NodeFieldOption {
  label: string
  value: string
}

/** 节点配置字段描述（后端 orchestrator NodeField） */
export interface NodeFieldSchema {
  key: string
  label: string
  type: NodeFieldType
  description?: string
  defaultValue?: unknown
  required?: boolean
  placeholder?: string
  options?: NodeFieldOption[]
}

/** 节点类型 Schema（后端 /api/orchestrator/node-types 返回） */
export interface NodeTypeSchema {
  code: WorkflowNodeType
  label: string
  branch: boolean
  /** 该类型节点是否允许出边 */
  source: boolean
  /** 该类型节点是否允许入边 */
  target: boolean
  fields: NodeFieldSchema[]
}

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
  status: 'success' | 'skipped' | 'error' | 'canceled'
  input?: string
  output?: string
  costMs: number
  error?: string
}

/** 工作流运行结果 */
export interface RunResult {
  runId?: string
  appId?: number
  /** running / success / failed / canceled / timeout */
  status?: string
  startedAt?: string
  finishedAt?: string
  costMs?: number
  error?: string
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

// ==================== 记忆管理（/data/memory） ====================

/** 记忆策略（mem_strategy，每个应用一条） */
export interface MemStrategy {
  id?: number
  tenantId: number
  appId: number
  /** 是否启用长期记忆：0否 1是 */
  enabled: number
  /** 对话后自动抽取记忆：0否 1是 */
  autoExtract: number
  /** 自动抽取使用的对话模型ID */
  extractModelId?: number | null
  /** 每次对话注入的记忆条目数 */
  topN: number
  /** 记忆保留天数（空 = 永久保留） */
  keepDays?: number | null
  /** 单应用记忆条目上限 */
  maxItems: number
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 会话变量（mem_variable） */
export interface MemVariable {
  id?: number
  tenantId: number
  appId: number
  /** 作用域：global 全局 / session 指定会话 */
  scope: 'global' | 'session'
  /** 所属会话ID（scope=session 时使用，空 = 该应用全部会话） */
  conversationId?: number | null
  /** 变量名（英文下划线） */
  name: string
  value?: string
  /** 类型：string/number/boolean/json */
  valueType: 'string' | 'number' | 'boolean' | 'json'
  remark?: string
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 长期记忆条目（mem_item） */
export interface MemItem {
  id?: number
  tenantId: number
  appId: number
  /** 作用域：global 全局 / user 用户 */
  scope: 'global' | 'user'
  /** 来源：manual 手动 / auto 自动抽取 */
  source: 'manual' | 'auto'
  /** 类别：preference 偏好 / fact 事实 / event 事件 / summary 摘要 / custom 自定义 */
  category: 'preference' | 'fact' | 'event' | 'summary' | 'custom'
  content: string
  /** 重要度 1-5 */
  importance: number
  /** 命中次数 */
  hitCount: number
  lastHitAt?: string
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

// ==================== 数据存储（/data/storage） ====================

/** 数据表列定义 */
export interface DataColumn {
  key: string
  label: string
  /** text/number/boolean/date/select */
  type: 'text' | 'number' | 'boolean' | 'date' | 'select'
  /** select 类型的选项 */
  options?: string[]
}

/** 自定义数据表（data_table） */
export interface DataTable {
  id: number
  tenantId: number
  /** 数据表名（租户内唯一） */
  name: string
  /** 显示名称 */
  label?: string
  description?: string
  /** 列定义 JSON 字符串（[{key,label,type,options?}]） */
  columnsJson?: string
  /** 行记录数 */
  rowCount: number
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 数据记录行视图（data_record 解析后的行） */
export interface DataRecordRow {
  id: number
  tableId: number
  /** 列键值对象 */
  data: Record<string, unknown>
  createTime?: string
  updateTime?: string
}

// ==================== 素材管理（/data/assets） ====================

export type AssetCategory = 'image' | 'document' | 'audio' | 'video' | 'other'

/** 素材文件（asset_file） */
export interface AssetFile {
  id: number
  tenantId: number
  /** 素材名称（展示用） */
  name: string
  originalName?: string
  ext?: string
  contentType?: string
  /** 文件大小（字节） */
  size: number
  category: AssetCategory
  status: number
  createdBy?: number
  createTime?: string
  updateTime?: string
}

// ==================== 数据集成（/tools/integrations） ====================

/** 外部连接器（tool_connector）：http=HTTP API / mysql=MySQL 数据库 */
export interface ToolConnector {
  id: number
  tenantId: number
  /** 连接器名称（英文标识符，生成 HTTP 工具时作为工具名） */
  name: string
  description?: string
  /** http / mysql */
  type: 'http' | 'mysql'
  /** http：API 地址；mysql：JDBC URL */
  url?: string
  /** http：请求方式 */
  method?: string
  /** http：额外请求头（JSON 对象） */
  headers?: string
  /** 鉴权：none / bearer / basic */
  authType?: 'none' | 'bearer' | 'basic'
  authToken?: string
  authUsername?: string
  authPassword?: string
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

// ==================== 插件市场（/tools/marketplace） ====================

/** 插件市场工具模板 */
export interface ToolTemplate {
  key: string
  /** 工具名称（英文标识符，安装后为 app_agent_tool.name） */
  name: string
  description: string
  /** basic 通用 / text 文本处理 / web 网络数据 */
  category: string
  /** http / code */
  type: 'http' | 'code'
  method?: string
  url?: string
  code?: string
  /** 参数 JSON Schema */
  parameters?: string
  /** 是否已安装 */
  installed: boolean
}
