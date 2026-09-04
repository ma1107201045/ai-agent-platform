import request from './request'

export type TrashType = 'agent' | 'dataset' | 'template' | 'announcement'

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

/** 回收站 API（/api/system/trash） */
export const trashApi = {
  list(type?: string) {
    return request.get<never, TrashItem[]>('/system/trash', { params: type ? { type } : {} })
  },
  restore(type: TrashType, id: number) {
    return request.post<never, void>('/system/trash/restore', { type, id })
  },
  purge(type: TrashType, id: number) {
    return request.post<never, void>('/system/trash/purge', { type, id })
  },
  cleanup(days = 30) {
    return request.post<never, CleanupResult>('/system/trash/cleanup', { days })
  }
}
