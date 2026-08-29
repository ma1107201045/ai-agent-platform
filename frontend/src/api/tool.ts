import request from './request'
import type { AppTool, PageResult } from './types'

/** Agent 工具 API */
export const toolApi = {
  page(params: { page?: number; size?: number }) {
    return request.get<never, PageResult<AppTool>>('/app/tools', { params })
  },
  enabled() {
    return request.get<never, AppTool[]>('/app/tools/enabled')
  },
  get(id: number) {
    return request.get<never, AppTool>(`/app/tools/${id}`)
  },
  create(data: Partial<AppTool>) {
    return request.post<never, AppTool>('/app/tools', data)
  },
  update(id: number, data: Partial<AppTool>) {
    return request.put<never, void>(`/app/tools/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/tools/${id}`)
  },
  test(id: number, argumentsStr: string) {
    return request.post<never, string>(`/app/tools/${id}/test`, { arguments: argumentsStr })
  }
}
