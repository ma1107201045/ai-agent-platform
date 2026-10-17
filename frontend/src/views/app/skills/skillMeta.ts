/** 技能页共享元信息与纯函数 */

export const categoryOptions = [
  { value: 'general', label: '通用', desc: '通用能力、综合场景' },
  { value: 'system', label: '系统', desc: '平台内置、系统级能力' },
  { value: 'business', label: '业务', desc: '业务专用能力' },
  { value: 'custom', label: '自定义', desc: '个人自定义能力' }
]

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
  const it = categoryOptions.find((x) => x.value === c)
  return it ? it.label : c || ''
}

export const typeMeta: Record<
  string,
  { label: string; type: 'primary' | 'success' | 'warning'; icon: string }
> = {
  instruction: { label: '指令型', type: 'primary', icon: '🧠' },
  tool: { label: '工具复合', type: 'warning', icon: '🛠️' },
  knowledge: { label: '知识技能', type: 'success', icon: '📚' }
}

export function typeLabelOf(t?: string): string {
  return t ? typeMeta[t]?.label || t : ''
}

/** 解析 JSON 字符串形式的 id 列表（toolIds / datasetIds 字段） */
export function parseIdList(s?: string): number[] {
  if (!s) return []
  try {
    const arr = JSON.parse(s)
    return Array.isArray(arr) ? arr.map(Number).filter((n) => Number.isFinite(n)) : []
  } catch {
    return []
  }
}
