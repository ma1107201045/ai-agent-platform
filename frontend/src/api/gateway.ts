import request from './request'
import type { PageResult } from './types'

/** 模型网关路由 */
export interface GatewayRoute {
  id: number
  name: string
  description?: string
  routeType: 'priority' | 'failover' | 'round_robin'
  targetsJson: string
  isDefault: number
  enabled: number
  callCount: number
  createTime?: string
}

/** 路由目标 */
export interface RouteTarget {
  modelId: number
  weight: number
  priority: number
}

export interface SimulateResult {
  routeId: number
  routeName: string
  strategy: string
  selected: RouteTarget & { modelName?: string }
  callIndex: number
}

/** 模型网关 API（/api/model/gateway） */
export const gatewayApi = {
  page(params: { page?: number; size?: number; keyword?: string; enabled?: number }) {
    return request.get<never, PageResult<GatewayRoute>>('/model/gateway', { params })
  },
  create(data: { route: Partial<GatewayRoute>; targets: RouteTarget[] }) {
    return request.post<never, GatewayRoute>('/model/gateway', data)
  },
  update(id: number, data: { route: Partial<GatewayRoute>; targets: RouteTarget[] }) {
    return request.put<never, void>(`/model/gateway/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/model/gateway/${id}`)
  },
  simulate(id: number) {
    return request.post<never, SimulateResult>(`/model/gateway/${id}/simulate`)
  }
}
