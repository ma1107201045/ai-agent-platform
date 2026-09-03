import request from './request'
import type { MemItem, MemStrategy, MemVariable } from './types'

/** 记忆管理 API（/api/memory），与后端 MemoryController 一一对应 */
export const memoryApi = {
  // ---------- 记忆策略 ----------
  /** 获取应用记忆策略（不存在时后端返回默认策略） */
  strategy(appId: number) {
    return request.get<never, MemStrategy>('/memory/strategy', { params: { appId } })
  },
  /** 保存应用记忆策略（按 appId upsert） */
  saveStrategy(data: Partial<MemStrategy>) {
    return request.put<never, MemStrategy>('/memory/strategy', data)
  },

  // ---------- 会话变量 ----------
  variables(appId: number, params?: { scope?: string; keyword?: string }) {
    return request.get<never, MemVariable[]>(`/memory/apps/${appId}/variables`, { params })
  },
  createVariable(appId: number, data: Partial<MemVariable>) {
    return request.post<never, MemVariable>(`/memory/apps/${appId}/variables`, data)
  },
  updateVariable(id: number, data: Partial<MemVariable>) {
    return request.put<never, void>(`/memory/variables/${id}`, data)
  },
  removeVariable(id: number) {
    return request.delete<never, void>(`/memory/variables/${id}`)
  },

  // ---------- 长期记忆条目 ----------
  items(appId: number, params?: { category?: string; scope?: string; keyword?: string }) {
    return request.get<never, MemItem[]>(`/memory/apps/${appId}/items`, { params })
  },
  createItem(appId: number, data: Partial<MemItem>) {
    return request.post<never, MemItem>(`/memory/apps/${appId}/items`, data)
  },
  updateItem(id: number, data: Partial<MemItem>) {
    return request.put<never, void>(`/memory/items/${id}`, data)
  },
  removeItem(id: number) {
    return request.delete<never, void>(`/memory/items/${id}`)
  }
}
