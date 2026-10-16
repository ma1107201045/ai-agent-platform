/** 提示词分类元信息（卡片封面 / 下拉选项共用） */

export const categoryOptions = [
  { value: 'general', label: '通用', desc: '通用助手、综合场景' },
  { value: 'system', label: '系统', desc: '平台内置、系统级模板' },
  { value: 'business', label: '业务', desc: '业务专用模板' },
  { value: 'custom', label: '自定义', desc: '个人自定义模板' }
]

const categoryLabels: Record<string, string> = Object.fromEntries(
  categoryOptions.map((c) => [c.value, c.label])
)

/** 分类封面渐变色（与智能体应用卡片同套封面表达） */
const coverGradients: Record<string, [string, string]> = {
  general: ['#64748b', '#94a3b8'],
  system: ['#d97706', '#f59e0b'],
  business: ['#059669', '#34d399'],
  custom: ['#4f46e5', '#8b5cf6']
}

export function coverGradientOf(c?: string): string {
  const g = (c && coverGradients[c]) || coverGradients.general
  return `linear-gradient(135deg, ${g[0]} 0%, ${g[1]} 100%)`
}

export function categoryLabelOf(c?: string): string {
  return c ? categoryLabels[c] || c : ''
}

/** {{ 与 }} 字面量：避免与 Vue 插值冲突 */
export const VAR_L = '{{'
export const VAR_R = '}}'
export const varSample = (n: string) => VAR_L + n + VAR_R

/** 变量定义 JSON 输入提示：内容含双引号，抽为常量避免在模板双引号属性内转义出错 */
export const VARS_PLACEHOLDER = '[{"name":"role","desc":"角色描述"}]'
