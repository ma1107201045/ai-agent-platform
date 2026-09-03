import request from './request'
import type { AppAgentTool, PageResult } from './types'

/** Agent 工具（ToolInfo）API，与后端 AppAgentToolController（/api/tool/infos）一一对应 */
export const appAgentToolApi = {
  page(params: { page?: number; size?: number }) {
    return request.get<never, PageResult<AppAgentTool>>('/tool/infos', { params })
  },
  enabled() {
    return request.get<never, AppAgentTool[]>('/tool/infos/enabled')
  },
  get(id: number) {
    return request.get<never, AppAgentTool>(`/tool/infos/${id}`)
  },
  create(data: Partial<AppAgentTool>) {
    return request.post<never, AppAgentTool>('/tool/infos', data)
  },
  update(id: number, data: Partial<AppAgentTool>) {
    return request.put<never, void>(`/tool/infos/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/tool/infos/${id}`)
  },
  test(id: number, argumentsStr: string) {
    return request.post<never, string>(`/tool/infos/${id}/test`, { arguments: argumentsStr })
  }
}
