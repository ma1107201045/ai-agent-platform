import request from './request'
import type { PageResult } from './types'

/** 智能体团队 */
export interface AgentTeam {
  id: number
  tenantId: number
  name: string
  description?: string
  /** first_match意图匹配 / round_robin轮询 / all并行汇合 */
  routing: string
  status: number
  runCount: number
  createTime?: string
  updateTime?: string
}

/** 团队成员（角色） */
export interface TeamMember {
  id?: number
  teamId?: number
  name: string
  description?: string
  /** 绑定应用ID（须为已发布应用） */
  appId: number
  /** 意图关键词（逗号分隔，first_match 路由用） */
  keywords?: string
  priority: number
  enabled: number
}

/** 成员行（含应用名） */
export interface TeamMemberRow {
  member: TeamMember
  appName: string
  appType?: string
  appStatus?: number
}

/** 执行轨迹节点 */
export interface TeamRunStep {
  memberId: number
  memberName: string
  appId: number
  status: 'success' | 'failed'
  answer?: string
  error?: string
  costMs: number
}

/** 团队运行记录 */
export interface TeamRun {
  id: number
  tenantId: number
  teamId: number
  input?: string
  answer?: string
  routedMember?: string
  traceJson?: string
  status: 'running' | 'success' | 'failed'
  error?: string
  costMs?: number
  createTime?: string
  finishTime?: string
}

export const agentTeamApi = {
  page(params: { page?: number; size?: number; keyword?: string }) {
    return request.get<never, PageResult<AgentTeam>>('/agent-teams', { params })
  },
  detail(id: number) {
    return request.get<never, { team: AgentTeam; members: TeamMemberRow[] }>(`/agent-teams/${id}`)
  },
  create(data: Partial<AgentTeam>) {
    return request.post<never, AgentTeam>('/agent-teams', data)
  },
  update(id: number, data: Partial<AgentTeam>) {
    return request.put<never, AgentTeam>(`/agent-teams/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/agent-teams/${id}`)
  },
  saveMembers(id: number, members: TeamMember[]) {
    return request.put<never, TeamMemberRow[]>(`/agent-teams/${id}/members`, members)
  },
  run(id: number, input: string) {
    return request.post<never, TeamRun>(`/agent-teams/${id}/run`, { input })
  },
  runs(id: number, params: { page?: number; size?: number }) {
    return request.get<never, PageResult<TeamRun>>(`/agent-teams/${id}/runs`, { params })
  }
}
