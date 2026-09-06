import request from './request'
import type { AppAgentTool, PageResult } from './types'

/** Agent 工具（ToolTools）API，与后端 AppAgentToolController（/api/app/tools）一一对应 */
export const appAgentToolApi = {
  page(params: { page?: number; size?: number }) {
    return request.get<never, PageResult<AppAgentTool>>('/app/tools', { params })
  },
  enabled() {
    return request.get<never, AppAgentTool[]>('/app/tools/enabled')
  },
  get(id: number) {
    return request.get<never, AppAgentTool>(`/app/tools/${id}`)
  },
  create(data: Partial<AppAgentTool>) {
    return request.post<never, AppAgentTool>('/app/tools', data)
  },
  update(id: number, data: Partial<AppAgentTool>) {
    return request.put<never, void>(`/app/tools/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/tools/${id}`)
  },
  test(id: number, argumentsStr: string) {
    return request.post<never, string>(`/app/tools/${id}/test`, { arguments: argumentsStr })
  }
}
