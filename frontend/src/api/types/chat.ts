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
