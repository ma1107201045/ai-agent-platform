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
  skillIds?: string
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
