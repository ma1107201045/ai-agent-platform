import request from './request'
import type { PageResult, Skill, SkillVersion } from './types'

/** 技能（AppSkill）API，与后端 AppSkillController（/api/app/skills）一一对应 */
export const appSkillApi = {
  page(params: {
    page?: number
    size?: number
    keyword?: string
    category?: string
    type?: string
  }) {
    return request.get<never, PageResult<Skill>>('/app/skills', { params })
  },
  /** 启用技能列表（应用绑定下拉 / 编排技能节点选择） */
  enabled(category?: string, type?: string) {
    return request.get<never, Skill[]>('/app/skills/enabled', { params: { category, type } })
  },
  get(id: number) {
    return request.get<never, Skill>(`/app/skills/${id}`)
  },
  create(data: Partial<Skill>) {
    return request.post<never, Skill>('/app/skills', data)
  },
  update(id: number, data: Partial<Skill>) {
    return request.put<never, Skill>(`/app/skills/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/app/skills/${id}`)
  },
  /** 版本快照列表（按版本号倒序） */
  versions(id: number) {
    return request.get<never, SkillVersion[]>(`/app/skills/${id}/versions`)
  },
  /** 回退到指定历史版本（生成新版本留痕） */
  rollback(id: number, version: number) {
    return request.post<never, Skill>(`/app/skills/${id}/rollback`, { version })
  },
  /** 导出技能（自包含 JSON，可分享/备份） */
  exportSkill(id: number) {
    return request.get<never, Skill>(`/app/skills/${id}/export`)
  },
  /** 导入技能（code 冲突自动追加后缀） */
  importSkill(data: Partial<Skill>) {
    return request.post<never, Skill>('/app/skills/import', data)
  },
  /** 引用该技能的应用列表 */
  apps(id: number) {
    return request.get<never, unknown[]>(`/app/skills/${id}/apps`)
  }
}
