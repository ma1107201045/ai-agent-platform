import type { DataColumn, DataTable } from '@/api/types'

/** 数据存储页共享工具 */

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AnyRow = Record<string, any>

/** 列编辑器行（含选项文本输入） */
export interface ColumnEditRow extends DataColumn {
  optionsText?: string
}

/** 解析数据表的列定义 JSON */
export function tableColumns(t: DataTable | null): DataColumn[] {
  if (!t?.columnsJson) return []
  try {
    const list = JSON.parse(t.columnsJson) as DataColumn[]
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

export function cellText(v: unknown): string {
  if (v === null || v === undefined) return '—'
  return String(v)
}

export function newColumn(): ColumnEditRow {
  return { key: '', label: '', type: 'text', options: [], optionsText: '' }
}

/** 按列类型生成空白行数据 */
export function blankRow(cols: DataColumn[]): AnyRow {
  const data: AnyRow = {}
  for (const c of cols) {
    if (c.type === 'boolean') data[c.key] = false
    else if (c.type === 'number') data[c.key] = undefined
    else data[c.key] = ''
  }
  return data
}
