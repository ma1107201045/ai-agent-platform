import request from './request'
import type { AppApiKey, PageResult } from './types'

/** 应用 API 密钥 API，与后端 AppApiKeyController（/api/app/api-keys）一一对应 */
export const appApiKeyApi = {
  page(params: { page?: number; size?: number; keyword?: string; appId?: number; status?: number }) {
    return request.get<never, PageResult<AppApiKey>>('/app/api-keys', { params })
  },
  create(data: { appId: number; name: string; expiresAt?: string; rateLimit?: number; remark?: string }) {
    return request.post<never, AppApiKey>('/app/api-keys', data)
  },
  update(
    id: number,
    data: { name: string; expiresAt?: string; rateLimit?: number; remark?: string }
  ) {
    return request.put<never, AppApiKey>(`/app/api-keys/${id}`, data)
  },
  /** 启用 / 禁用 */
  setStatus(id: number, status: number) {
    return request.post<never, void>(`/app/api-keys/${id}/status`, { status })
  },
  /** 轮换密钥：作废旧值并返回一次新明文 */
  rotate(id: number) {
    return request.post<never, AppApiKey>(`/app/api-keys/${id}/rotate`)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/api-keys/${id}`)
  }
}
