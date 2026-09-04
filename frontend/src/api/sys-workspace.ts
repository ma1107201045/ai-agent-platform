import request from './request'

/** 工作空间（当前租户空间） */
export interface Workspace {
  id: number
  name: string
  code: string
  /** free/pro/enterprise */
  plan: string
  status: number
  memberCount: number
  appCount: number
  createTime?: string
  updateTime?: string
}

/** 工作空间 API（/api/sys/workspace） */
export const workspaceApi = {
  getCurrent() {
    return request.get<never, Workspace>('/sys/workspace/current')
  },
  update(data: { name: string; plan: string }) {
    return request.put<never, Workspace>('/sys/workspace/current', data)
  }
}
