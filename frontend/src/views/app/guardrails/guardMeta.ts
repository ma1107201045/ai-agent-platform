/** 内容安全页共享元信息与映射函数 */

export const DIRS: { value: string; label: string; tag: 'danger' | 'primary' }[] = [
  { value: 'input', label: '输入侧', tag: 'danger' },
  { value: 'output', label: '输出侧', tag: 'primary' }
]

export const MATCH_TYPES: { value: string; label: string; tag: 'warning' | 'danger' | 'info' }[] = [
  { value: 'keyword', label: '关键词', tag: 'warning' },
  { value: 'regex', label: '正则表达式', tag: 'danger' },
  { value: 'prompt_injection', label: '注入检测', tag: 'info' }
]

export const ACTIONS: {
  value: string
  label: string
  desc: string
  tag: 'danger' | 'warning' | 'info'
}[] = [
  { value: 'block', label: '拦截', desc: '命中后直接拒绝本次请求', tag: 'danger' },
  { value: 'mask', label: '打码', desc: '将命中的内容替换为占位符', tag: 'warning' },
  { value: 'replace', label: '替换', desc: '替换为自定义文本继续处理', tag: 'info' }
]

export const META = {
  dir: Object.fromEntries(DIRS.map((d) => [d.value, d])),
  match: Object.fromEntries(MATCH_TYPES.map((m) => [m.value, m])),
  action: Object.fromEntries(ACTIONS.map((a) => [a.value, a]))
}

export const RISK_LEVELS = [
  { min: 1, max: 2, label: '低风险', color: '#67c23a' },
  { min: 3, max: 3, label: '中风险', color: '#e6a23c' },
  { min: 4, max: 5, label: '高风险', color: '#f56c6c' }
]

export function riskInfo(level?: number) {
  const lv = level ?? 3
  const hit = RISK_LEVELS.find((r) => lv >= r.min && lv <= r.max) || RISK_LEVELS[1]
  return { ...hit, lv }
}

export function dirLabel(v?: string) {
  return v ? (META.dir[v]?.label ?? v) : '-'
}
export function dirTag(v?: string) {
  return (v ? META.dir[v]?.tag : undefined) || 'info'
}
export function matchLabel(v?: string) {
  return v ? (META.match[v]?.label ?? v) : '-'
}
export function actionLabel(v?: string) {
  return v ? (META.action[v]?.label ?? v) : '-'
}
export function actionTag(v?: string) {
  return (v ? META.action[v]?.tag : undefined) || 'info'
}
export function actionDesc(v?: string) {
  return v ? (META.action[v]?.desc ?? '') : ''
}
