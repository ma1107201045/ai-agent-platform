import request from './request'
import type { AppAgent, PageResult } from './types'

/** 应用模板 */
export interface AppTemplate {
  id: number
  tenantId: number
  name: string
  /** customer-service/translate/content/data-analysis/marketing/coding/custom */
  category?: string
  /** chatflow/workflow/agent */
  appType: string
  /** 图标（emoji） */
  icon?: string
  description?: string
  useCase?: string
  welcomeMessage?: string
  /** 是否平台内置：0否 1是 */
  builtin: number
  usageCount: number
  /** 状态：0停用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

export interface TemplatePayload {
  name: string
  category?: string
  appType?: string
  icon?: string
  description?: string
  useCase?: string
  welcomeMessage?: string
  status?: number
}

/** 应用模板 API（/api/app/templates） */
export const appTemplateApi = {
  page(params: { page?: number; size?: number; keyword?: string; category?: string; appType?: string }) {
    return request.get<never, PageResult<AppTemplate>>('/app/templates', { params })
  },
  create(data: TemplatePayload) {
    return request.post<never, AppTemplate>('/app/templates', data)
  },
  update(id: number, data: TemplatePayload) {
    return request.put<never, void>(`/app/templates/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/templates/${id}`)
  },
  /** 从模板一键创建应用草稿 */
  instantiate(id: number, name?: string) {
    return request.post<never, AppAgent>(`/app/templates/${id}/instantiate`, name ? { name } : {})
  }
}
