import request from './request'
import type { AppAgentTool, ToolTemplate } from './types'

/** 插件市场 API，与后端 ToolMarketplaceController（/api/app/tool-marketplace）一一对应 */
export const toolMarketplaceApi = {
  /** 模板目录（含已安装标记） */
  templates(params?: { category?: string; keyword?: string }) {
    return request.get<never, ToolTemplate[]>('/app/tool-marketplace/templates', { params })
  },
  /** 一键安装模板：以模板内容创建真实工具 */
  install(key: string) {
    return request.post<never, AppAgentTool>(`/app/tool-marketplace/templates/${key}/install`)
  }
}
