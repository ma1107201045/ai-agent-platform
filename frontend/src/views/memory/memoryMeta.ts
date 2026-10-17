/** 记忆管理页共享元信息 */

export const itemCategories = [
  { value: 'preference', label: '用户偏好' },
  { value: 'fact', label: '事实' },
  { value: 'event', label: '事件' },
  { value: 'summary', label: '总结' },
  { value: 'custom', label: '自定义' }
]

export const catText = (v: string): string =>
  itemCategories.find((c) => c.value === v)?.label || '自定义'

export const catTag = (v: string): 'success' | 'primary' | 'warning' | 'danger' | 'info' =>
  (
    ({
      preference: 'success',
      fact: 'primary',
      event: 'warning',
      summary: 'danger',
      custom: 'info'
    }) as Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'>
  )[v] || 'info'

export const typeText = (t: string): string =>
  ({ string: '字符串', number: '数字', boolean: '布尔', json: 'JSON' })[t] || t

export const importanceText = (lv: number): string =>
  ['', '较低', '一般', '重要', '很重要', '极重要'][lv] || ''
