import request from './request'
import { getToken } from '@/utils/token'
import type { DataColumn, DataRecordRow, DataTable, PageResult } from './types'

/** 自定义数据表请求体（不含 rowCount/status 等服务端字段） */
export interface DataTablePayload {
  name: string
  label?: string
  description?: string
  columns: DataColumn[]
}

/** 数据存储 API（/api/data-store），与后端 DataStoreController 一一对应 */
export const dataStoreApi = {
  // ---------- 数据表 ----------
  tablePage(params: { page?: number; size?: number; keyword?: string }) {
    return request.get<never, PageResult<DataTable>>('/data-store/tables', { params })
  },
  getTable(id: number) {
    return request.get<never, DataTable>(`/data-store/tables/${id}`)
  },
  createTable(data: DataTablePayload) {
    return request.post<never, DataTable>('/data-store/tables', data)
  },
  updateTable(id: number, data: DataTablePayload) {
    return request.put<never, DataTable>(`/data-store/tables/${id}`, data)
  },
  removeTable(id: number) {
    return request.delete<never, void>(`/data-store/tables/${id}`)
  },

  // ---------- 行记录 ----------
  recordPage(tableId: number, params: { page?: number; size?: number; keyword?: string }) {
    return request.get<never, PageResult<DataRecordRow>>(`/data-store/tables/${tableId}/records`, {
      params
    })
  },
  createRecord(tableId: number, data: Record<string, unknown>) {
    return request.post<never, DataRecordRow>(`/data-store/tables/${tableId}/records`, { data })
  },
  updateRecord(recordId: number, data: Record<string, unknown>) {
    return request.put<never, DataRecordRow>(`/data-store/records/${recordId}`, { data })
  },
  removeRecord(recordId: number) {
    return request.delete<never, void>(`/data-store/records/${recordId}`)
  },

  // ---------- 导入 ----------
  /** JSON 数组导入：[{列key: 值}, ...]，返回导入行数 */
  importJson(tableId: number, rows: Record<string, unknown>[]) {
    return request.post<never, number>(`/data-store/tables/${tableId}/import-json`, rows)
  },
  /** CSV 文件导入（首行为表头：列名或列 key），返回导入行数 */
  importCsv(tableId: number, file: File) {
    const form = new FormData()
    form.append('file', file)
    return request.post<never, number>(`/data-store/tables/${tableId}/import-csv`, form)
  }
}

/** 导出数据表为 CSV（携带登录态直接下载） */
export async function downloadTableCsv(tableId: number, filename: string): Promise<void> {
  const token = getToken()
  const resp = await fetch(`/api/data-store/tables/${tableId}/export-csv`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined
  })
  if (!resp.ok) {
    const err = await resp.text().catch(() => '')
    throw new Error(err || `导出失败(${resp.status})`)
  }
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename.endsWith('.csv') ? filename : `${filename}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
