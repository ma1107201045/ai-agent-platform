/** 技能类型：instruction 指令型 / tool 工具复合型 / knowledge 知识技能型 */
export type SkillType = 'instruction' | 'tool' | 'knowledge'

/** 技能（能力包 = 指令正文 + 变量 + 依赖工具 + 参考数据集，绑定到应用自动注入 Agent 循环） */
export interface Skill {
  id: number
  tenantId: number
  name: string
  /** 技能唯一标识（小写 kebab-case，租户内唯一） */
  code: string
  description?: string
  /** 分类: general 通用 / system 系统 / business 业务 / custom 自定义 */
  category?: string
  type: SkillType
  icon?: string
  /** 技能指令正文（Markdown，支持 {{var}} 占位） */
  content: string
  /** 变量定义（JSON 数组：[{"name":"var","desc":"说明"}]） */
  variables?: string
  /** 依赖工具 ID 列表（JSON 数组字符串） */
  toolIds?: string
  /** 参考数据集 ID 列表（JSON 数组字符串） */
  datasetIds?: string
  /** 当前版本号 */
  version: number
  /** 状态：0禁用 1启用 */
  status: number
  createdBy?: number
  createTime?: string
  updateTime?: string
}

/** 技能版本快照（留痕/回退） */
export interface SkillVersion {
  id: number
  skillId: number
  version: number
  content: string
  variables?: string
  toolIds?: string
  datasetIds?: string
  remark?: string
  createdBy?: number
  createTime?: string
}
