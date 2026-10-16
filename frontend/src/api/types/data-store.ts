/** 数据表列定义 */
export interface DataColumn {
  key: string
  label: string
  /** text/number/boolean/date/select */
  type: 'text' | 'number' | 'boolean' | 'date' | 'select'
  /** select 类型的选项 */
  options?: string[]
}

/** 自定义数据表（data_table） */
export interface DataTable {
  id: number
  tenantId: number
  /** 数据表名（租户内唯一） */
  name: string
  /** 显示名称 */
  label?: string
  description?: string
  /** 列定义 JSON 字符串（[{key,label,type,options?}]） */
  columnsJson?: string
  /** 行记录数 */
  rowCount: number
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 数据记录行视图（data_record 解析后的行） */
export interface DataRecordRow {
  id: number
  tableId: number
  /** 列键值对象 */
  data: Record<string, unknown>
  createTime?: string
  updateTime?: string
}
