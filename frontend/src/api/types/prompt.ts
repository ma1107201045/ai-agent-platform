/** 提示词模板（支持 {{var}} 变量占位，版本留痕） */
export interface PromptTemplate {
  id: number
  tenantId: number
  name: string
  description?: string
  /** 分类: general 通用 / system 系统 / business 业务 / custom 自定义 */
  category?: string
  /** 模板正文（支持 {{var}} 占位） */
  content: string
  /** 变量定义（JSON 数组：[{"name":"var","desc":"说明"}]） */
  variables?: string
  /** 当前版本号 */
  version: number
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 提示词模板版本快照（留痕/回退） */
export interface PromptTemplateVersion {
  id: number
  templateId: number
  version: number
  content: string
  variables?: string
  remark?: string
  createdBy?: number
  createTime?: string
}

/** 提示词变量定义项 */
export interface PromptVariableDef {
  name: string
  desc?: string
}
