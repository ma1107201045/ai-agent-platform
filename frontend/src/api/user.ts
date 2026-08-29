import request from './request'
import type { PageResult, SysUser } from './types'

export const userApi = {
  page(params: { page?: number; size?: number }) {
    return request.get<never, PageResult<SysUser>>('/sys/users', { params })
  },
  create(data: Partial<SysUser>) {
    return request.post<never, SysUser>('/sys/users', data)
  },
  update(id: number, data: Partial<SysUser>) {
    return request.put<never, void>(`/sys/users/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/sys/users/${id}`)
  }
}
