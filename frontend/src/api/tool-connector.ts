import request from './request'
import type { AppAgentTool, PageResult, ToolConnector } from './types'

/** 数据集成 - 连接器 API，与后端 ToolConnectorController（/api/tool/connectors）一一对应 */
export const toolConnectorApi = {
  page(params: { page?: number; size?: number; keyword?: string; type?: string; status?: number }) {
    return request.get<never, PageResult<ToolConnector>>('/tool/connectors', { params })
  },
  create(data: Partial<ToolConnector>) {
    return request.post<never, ToolConnector>('/tool/connectors', data)
  },
  update(id: number, data: Partial<ToolConnector>) {
    return request.put<never, void>(`/tool/connectors/${id}`, data)
  },
  /** 启用 / 禁用 */
  setStatus(id: number, status: number) {
    return request.post<never, void>(`/tool/connectors/${id}/status`, { status })
  },
  /** 连通性测试：返回可读结果文本 */
  test(id: number) {
    return request.post<never, string>(`/tool/connectors/${id}/test`)
  },
  /** 一键将 HTTP 连接器生成为 HTTP 工具 */
  asTool(id: number) {
    return request.post<never, AppAgentTool>(`/tool/connectors/${id}/as-tool`)
  },
  remove(id: number) {
    return request.delete<never, void>(`/tool/connectors/${id}`)
  }
}
