import request from './request'
import type {
  AgentResult,
  AppAgent,
  AppAgentStats,
  AppAgentVersion,
  ChatMessage,
  PageResult,
  RunResult,
  TraceItem
} from './types'

/** SSE 运行事件帧（data 行为 JSON 自描述） */
interface RunStreamFrame {
  type: string
  data: unknown
}

/** 智能体应用（AppAgent）API，与后端 AppAgentController（/api/app/agents）一一对应 */
export const appAgentApi = {
  page(params: { page?: number; size?: number; keyword?: string; type?: string }) {
    return request.get<never, PageResult<AppAgent>>('/app/agents', { params })
  },
  get(id: number) {
    return request.get<never, AppAgent>(`/app/agents/${id}`)
  },
  create(data: Partial<AppAgent>) {
    return request.post<never, AppAgent>('/app/agents', data)
  },
  update(id: number, data: Partial<AppAgent>) {
    return request.put<never, void>(`/app/agents/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/agents/${id}`)
  },
  publish(id: number, data: { workflowJson?: string; promptConfig?: string }) {
    return request.post<never, AppAgentVersion>(`/app/agents/${id}/publish`, data)
  },
  published(id: number) {
    return request.get<never, AppAgentVersion>(`/app/agents/${id}/published`)
  },
  /** 版本列表（按版本号倒序） */
  versions(id: number) {
    return request.get<never, AppAgentVersion[]>(`/app/agents/${id}/versions`)
  },
  /** 回滚到指定版本（恢复草稿，不自动发布） */
  rollback(id: number, versionId: number) {
    return request.post<never, AppAgentVersion>(`/app/agents/${id}/versions/${versionId}/rollback`)
  },
  /** 批量获取发布版本，返回 Map<appId, version> */
  batchPublished(ids: number[]) {
    return request.get<never, Record<number, AppAgentVersion>>('/app/agents/batch/published', {
      params: { ids: ids.join(',') }
    })
  },
  /** 批量会话统计（对外访问 / 运营数据） */
  batchStats(ids: number[]) {
    return request.get<never, Record<number, AppAgentStats>>('/app/agents/batch/stats', {
      params: { ids: ids.join(',') }
    })
  },
  run(id: number, messages: ChatMessage[]) {
    return request.post<never, RunResult>(`/app/agents/${id}/run`, { messages })
  },
  /**
   * 流式运行工作流（SSE，画布实时监控）：
   * 逐帧回调，帧 data 自描述：
   * - run-started: {runId}
   * - node-started: {nodeId}（节点进入执行，画布点亮运行中）
   * - node-finished: TraceItem
   * - done: RunResult（随后连接关闭）
   * - run-error: {message}
   */
  async runStream(
    id: number,
    messages: ChatMessage[],
    handlers: {
      onRunStarted?: (runId: string) => void
      onNodeStarted?: (nodeId: string) => void
      onNodeFinished?: (item: TraceItem) => void
      onDone?: (result: RunResult) => void
      onError?: (message: string) => void
    },
    signal?: AbortSignal
  ): Promise<void> {
    const token = localStorage.getItem('agent_platform_token') || ''
    const resp = await fetch(`/api/app/agents/${id}/run-stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ messages }),
      signal
    })
    if (!resp.ok || !resp.body) {
      const err = await resp.text().catch(() => '')
      handlers.onError?.(err || `请求失败(${resp.status})`)
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const event of events) {
        const dataLine = event.split('\n').find((l) => l.startsWith('data:'))
        if (!dataLine) continue
        const raw = dataLine.slice(5).trim()
        if (!raw) continue
        let frame: RunStreamFrame
        try {
          frame = JSON.parse(raw) as RunStreamFrame
        } catch {
          continue
        }
        const data = frame.data as Record<string, unknown> | null
        if (frame.type === 'run-started') handlers.onRunStarted?.(String(data?.runId || ''))
        else if (frame.type === 'node-started') handlers.onNodeStarted?.(String(data?.nodeId || ''))
        else if (frame.type === 'node-finished') handlers.onNodeFinished?.(frame.data as TraceItem)
        else if (frame.type === 'done') handlers.onDone?.(frame.data as RunResult)
        else if (frame.type === 'run-error')
          handlers.onError?.((data?.message as string) || '工作流运行失败')
      }
    }
  },
  /** 取消一次运行中的工作流 */
  cancelRun(runId: string) {
    return request.post<never, boolean>(`/agent-runs/${runId}/cancel`)
  },
  /** Agent 自主对话（非流式）：规划-工具调用-观察循环 */
  agentChat(
    appId: number,
    data: { modelId: number; systemPrompt?: string; messages: ChatMessage[]; maxIterations?: number }
  ) {
    return request.post<never, AgentResult>(`/app/agents/${appId}/agent/chat`, data)
  }
}
