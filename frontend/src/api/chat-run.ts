import request from './request'

/** 工作流运行记录（对应后端 agent_run 表） */
export interface AgentRunRecord {
  id: number
  runId: string
  appId: number
  conversationId: number | null
  /** 运行模式: workflow / agent */
  mode: string
  /** 用户输入（本次运行的 userInput） */
  input: string
  /** 最终回答（对用户友好） */
  answer: string
  /** 运行状态: running/success/failed/canceled/timeout */
  status: 'running' | 'success' | 'failed' | 'canceled' | 'timeout'
  /** 技术性错误描述 */
  error: string | null
  /** 节点执行轨迹（JSON 字符串） */
  traceJson: string | null
  /** 总耗时（毫秒） */
  costMs: number | null
  createTime: string
  finishTime: string | null
}

export interface AgentRunQuery {
  appId?: number
  status?: string
  page?: number
  size?: number
}

/** 运行记录分页（按开始时间倒序） */
export function pageRuns(params: AgentRunQuery = {}) {
  return request.get<never, { records: AgentRunRecord[]; total: number }>('/chat/runs', { params })
}

/** 运行详情（含完整轨迹） */
export function getRunDetail(runId: string, appId?: number) {
  return request.get<never, AgentRunRecord>(`/chat/runs/${runId}`, { params: { appId } })
}
