import request from './request'
import type { AppAgent, PageResult } from './types'

/** 应用市场条目 */
export interface MarketItem {
  id: number
  tenantId?: number | null
  name: string
  description?: string
  /** general/customer_service/translate/writing/office/analysis/other */
  category: string
  icon?: string
  /** chatflow/workflow/agent */
  type: string
  workflowJson?: string
  configJson?: string
  author?: string
  installCount: number
  status: number
  createTime?: string
  updateTime?: string
}

export interface MarketStats {
  total: number
  totalInstall: number
  byCategory: Record<string, number>
}

export const marketApi = {
  page(params: { page?: number; size?: number; category?: string; type?: string; keyword?: string }) {
    return request.get<never, PageResult<MarketItem>>('/app-market', { params })
  },
  stats() {
    return request.get<never, MarketStats>('/app-market/stats')
  },
  get(id: number) {
    return request.get<never, MarketItem>(`/app-market/${id}`)
  },
  install(id: number) {
    return request.post<never, AppAgent>(`/app-market/${id}/install`)
  }
}
