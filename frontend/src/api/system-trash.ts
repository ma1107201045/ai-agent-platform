import request from './request'

export type TrashType =
  | 'agent'
  | 'dataset'
  | 'template'
  | 'announcement'
  | 'team'
  | 'tool'
  | 'skill'
  | 'prompt_template'
  | 'conversation'
  | 'data_table'

export interface TrashItem {
  id: number
  name: string
  /** type 对应的中文名（由后端下发） */
  typeLabel?: string
  type: string
  deletedTime?: string
}

export interface CleanupResult {
  removed: number
  days: number
}

/** 回收站 API（/api/sys/trash） */
export const trashApi = {
  list(type?: string) {
    return request.get<never, TrashItem[]>('/sys/trash', { params: type ? { type } : {} })
  },
  restore(type: TrashType, id: number) {
    return request.post<never, void>('/sys/trash/restore', { type, id })
  },
  purge(type: TrashType, id: number) {
    return request.post<never, void>('/sys/trash/purge', { type, id })
  },
  /** 彻底删除前的影响预览：将被级联删除的子数据计数（label → 条数） */
  impact(type: TrashType, id: number) {
    return request.get<never, Record<string, number>>('/sys/trash/impact', { params: { type, id } })
  },
  cleanup(days = 30) {
    return request.post<never, CleanupResult>('/sys/trash/cleanup', { days })
  }
}
