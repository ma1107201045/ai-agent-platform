import request from './request'
import type { PageResult } from './types'

/** 内容安全规则 */
export interface GuardRule {
  id: number
  name: string
  description?: string
  /** input输入/output输出 */
  direction: 'input' | 'output'
  /** keyword关键词/regex正则/prompt_injection注入检测 */
  matchType: string
  ruleContent: string
  /** block拦截/mask打码/replace替换 */
  action: string
  replaceText?: string
  riskLevel: number
  enabled: number
  priority: number
  hitCount?: number
  createTime?: string
  updateTime?: string
}

/** 应用内容安全绑定 */
export interface GuardAppBindVO {
  appId: number
  appName: string
  appType: string
  bindId?: number
  bindEnabled?: number
  bindMode?: string
  ruleIds?: string
  ruleCount?: number
}

/** 命中测试结果 */
export interface GuardTestResult {
  blocked: boolean
  hits: {
    ruleId: number
    name: string
    matchType: string
    direction: string
    action: string
    riskLevel: number
    matched: string[]
  }[]
  hitCount: number
  output: string
  changed: boolean
}

export const guardApi = {
  rulePage(params: {
    page?: number
    size?: number
    direction?: string
    matchType?: string
    action?: string
    enabled?: number
    keyword?: string
  }) {
    return request.get<never, PageResult<GuardRule>>('/guard/rules', { params })
  },
  createRule(data: Partial<GuardRule>) {
    return request.post<never, GuardRule>('/guard/rules', data)
  },
  updateRule(id: number, data: Partial<GuardRule>) {
    return request.put<never, GuardRule>(`/guard/rules/${id}`, data)
  },
  removeRule(id: number) {
    return request.delete<never, void>(`/guard/rules/${id}`)
  },
  test(data: { ruleIds?: number[]; text: string }) {
    return request.post<never, GuardTestResult>('/guard/rules/test', data)
  },
  binds() {
    return request.get<never, GuardAppBindVO[]>('/guard/binds')
  },
  saveBind(appId: number, data: { ruleIds: number[]; mode: string; enabled: number }) {
    return request.put<never, GuardAppBindVO>(`/guard/binds/${appId}`, data)
  },
  removeBind(appId: number) {
    return request.delete<never, void>(`/guard/binds/${appId}`)
  }
}
