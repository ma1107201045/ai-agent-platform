import request from './request'

export interface BillingSummary {
  month: string
  totalCost: number
  todayCost: number
  totalTokens: number
  promptTokens: number
  completionTokens: number
  callCount: number
  budget: number
  notifyEnabled: number
  budgetUsedPct?: number
}

export interface CostPoint {
  date: string
  cost: number
}

export interface CostBreakdown {
  appId?: number
  appName?: string
  appType?: string
  modelId?: number
  modelName?: string
  cost: number
  tokens: number
  calls: number
}

/** 费用账单 API（/api/ops/billing） */
export const billingApi = {
  summary(month?: string) {
    return request.get<never, BillingSummary>('/ops/billing', { params: { month } })
  },
  trend(month?: string) {
    return request.get<never, CostPoint[]>('/ops/billing/trend', { params: { month } })
  },
  byApp(month?: string) {
    return request.get<never, CostBreakdown[]>('/ops/billing/by-app', { params: { month } })
  },
  byModel(month?: string) {
    return request.get<never, CostBreakdown[]>('/ops/billing/by-model', { params: { month } })
  },
  setBudget(data: { month: string; budget: number; notifyEnabled: number }) {
    return request.put<never, void>('/ops/billing/budget', data)
  }
}
