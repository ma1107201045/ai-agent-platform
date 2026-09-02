import request from './request'
import type { PageResult, PromptTemplate, PromptTemplateVersion } from './types'

/** 提示词模板（AppPromptTemplate）API，与后端 AppPromptTemplateController（/api/app/prompts）一一对应 */
export const appPromptApi = {
  page(params: { page?: number; size?: number; keyword?: string; category?: string }) {
    return request.get<never, PageResult<PromptTemplate>>('/app/prompts', { params })
  },
  /** 启用模板列表（试跑/编排下拉用） */
  enabled(category?: string) {
    return request.get<never, PromptTemplate[]>('/app/prompts/enabled', { params: { category } })
  },
  get(id: number) {
    return request.get<never, PromptTemplate>(`/app/prompts/${id}`)
  },
  create(data: Partial<PromptTemplate>) {
    return request.post<never, PromptTemplate>('/app/prompts', data)
  },
  update(id: number, data: Partial<PromptTemplate>) {
    return request.put<never, PromptTemplate>(`/app/prompts/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/prompts/${id}`)
  },
  /** 版本快照列表（按版本号倒序） */
  versions(id: number) {
    return request.get<never, PromptTemplateVersion[]>(`/app/prompts/${id}/versions`)
  },
  /** 回退到指定历史版本（生成新版本留痕） */
  rollback(id: number, version: number) {
    return request.post<never, PromptTemplate>(`/app/prompts/${id}/rollback`, { version })
  },
  /** 渲染模板正文（{{var}} 占位替换），用于在线调试 */
  render(content: string, variables: Record<string, string>) {
    return request.post<never, string>('/app/prompts/render', { content, variables })
  },
  /** 提取正文中的变量占位名 */
  extractVariables(content: string) {
    return request.post<never, string[]>('/app/prompts/extract-variables', { content })
  },
  /** 解析变量定义 JSON 为 name → desc 映射（供试跑表单展示说明） */
  parseVariables(variables: string) {
    return request.post<never, Record<string, string>>('/app/prompts/parse-variables', { variables })
  }
}
