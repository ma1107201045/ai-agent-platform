import type { ModelType } from '@/api/types'

/** 模型类型元信息：值 -> 中文名 / tag 颜色 */
export const MODEL_TYPES: {
  value: ModelType
  label: string
  tag: '' | 'success' | 'warning' | 'info' | 'danger' | 'primary'
}[] = [
  { value: 'llm', label: '对话 LLM', tag: 'primary' },
  { value: 'embedding', label: '向量 Embedding', tag: 'success' },
  { value: 'rerank', label: '重排 Rerank', tag: 'warning' },
  { value: 'tts', label: '语音合成 TTS', tag: 'danger' },
  { value: 'asr', label: '语音识别 ASR', tag: 'info' },
  { value: 'image', label: '图像 Image', tag: '' }
]
const MODEL_TYPE_MAP = Object.fromEntries(MODEL_TYPES.map((t) => [t.value, t]))

/** 能力标签候选 */
export const CAP_OPTIONS: { value: string; label: string }[] = [
  { value: 'function_call', label: '函数调用' },
  { value: 'vision', label: '视觉理解' },
  { value: 'stream', label: '流式输出' },
  { value: 'json_mode', label: 'JSON 输出' },
  { value: 'reasoning', label: '深度推理' },
  { value: 'image', label: '图像生成' }
]
const CAP_LABELS = Object.fromEntries(CAP_OPTIONS.map((c) => [c.value, c.label]))

export function modelTypeLabel(t?: string): string {
  if (!t) return '-'
  return MODEL_TYPE_MAP[t]?.label ?? t
}

export function modelTypeTag(t?: string): 'info' | 'success' | 'warning' | 'danger' | 'primary' {
  if (!t) return 'info'
  return (MODEL_TYPE_MAP[t]?.tag || 'info') as 'info' | 'success' | 'warning' | 'danger' | 'primary'
}

export function providerTypeLabel(t?: string): string {
  if (t === 'openai-compatible') return 'OpenAI 兼容'
  return t || '-'
}

export function capLabel(c: string): string {
  return CAP_LABELS[c] ?? c
}

export function fmtTime(t?: string): string {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

export function fmtK(v?: number): string {
  if (v == null) return '-'
  return v >= 1000 ? `${Math.round(v / 1000).toLocaleString('zh-CN')}K` : String(v)
}

export function fmtNum(v?: number): string {
  return v == null ? '-' : v.toLocaleString('zh-CN')
}

export function maskKey(k?: string): string {
  if (!k) return ''
  if (k.length <= 8) return '••••••••'
  return `${k.slice(0, 3)}••••••••${k.slice(-4)}`
}

export function parseCaps(c?: string): string[] {
  if (!c) return []
  try {
    const arr = JSON.parse(c)
    if (Array.isArray(arr)) return arr.map(String)
  } catch {
    /* 非 JSON 则按逗号分隔兜底 */
  }
  return c
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}
