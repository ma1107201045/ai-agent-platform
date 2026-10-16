import { ElMessage } from 'element-plus'
import { clearToken, getToken } from '@/utils/token'
import { clearHttpCache } from '@/utils/httpCache'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'

/** 将 /api 开头的路径解析为实际 baseURL（直连后端场景下与 axios baseURL 保持一致） */
export function apiUrl(path: string): string {
  if (API_BASE !== '/api' && path.startsWith('/api')) return API_BASE + path.slice(4)
  return path
}

let handling401 = false

/**
 * 401 统一处理：清登录态并跳转登录页（保留 redirect 回跳参数）。
 * 动态 import router / store，避免与 router、api 层产生循环依赖。
 */
export async function handleUnauthorized(message?: string): Promise<void> {
  if (handling401) return
  handling401 = true
  try {
    clearToken()
    clearHttpCache()
    if (!location.pathname.startsWith('/login')) {
      ElMessage.error(message || '登录已过期，请重新登录')
      const [{ default: router }, { useUserStore }] = await Promise.all([
        import('@/router'),
        import('@/stores/user')
      ])
      useUserStore().logout()
      await router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  } finally {
    handling401 = false
  }
}

/**
 * 携带登录态的 fetch：自动注入 Bearer Token，401 时与 axios 拦截器行为一致。
 * 其余错误不自动提示，由调用方决定（抛错或自定义处理）。
 */
export async function authFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken()
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const resp = await fetch(apiUrl(url), { ...init, headers })
  if (resp.status === 401) {
    await handleUnauthorized()
    throw new Error('登录已过期，请重新登录')
  }
  return resp
}

export interface StreamOptions {
  method?: string
  /** 请求体（自动 JSON 序列化并补 Content-Type）；GET 下载类请求可不传 */
  body?: unknown
  signal?: AbortSignal
}

/**
 * SSE 流式请求：
 * - 按 SSE 协议解析（`\n\n` 分帧、取 `data:` 行），逐帧回调原始 data 字符串
 * - onEvent 返回 false 可提前中断（底层取消 reader）
 * - HTTP 非 2xx 抛错，消息取响应体文本
 */
export async function streamEvents(
  url: string,
  options: StreamOptions = {},
  onEvent: (raw: string) => void | boolean
): Promise<void> {
  const resp = await authFetch(url, {
    method: options.method ?? 'POST',
    headers: options.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal
  })
  if (!resp.ok || !resp.body) {
    const err = await resp.text().catch(() => '')
    throw new Error(err || `请求失败(${resp.status})`)
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let stopped = false
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''
    for (const event of events) {
      const dataLine = event.split('\n').find((l) => l.startsWith('data:'))
      if (!dataLine) continue
      const raw = dataLine.slice(5).trim()
      if (!raw) continue
      if (onEvent(raw) === false) {
        stopped = true
        break
      }
    }
    if (stopped) break
  }
  if (stopped) {
    await reader.cancel().catch(() => {})
  }
}

/**
 * OpenAI 风格的增量对话流（`[DONE]` 终止、`delta` 字段累加）。
 * @returns 完整拼接的回复文本
 */
export async function chatDeltaStream<C extends { delta?: string }>(
  url: string,
  body: unknown,
  onChunk: (chunk: C) => void,
  signal?: AbortSignal
): Promise<string> {
  let full = ''
  await streamEvents(url, { body, signal }, (raw) => {
    if (raw === '[DONE]') return false
    try {
      const chunk = JSON.parse(raw) as C
      if (chunk.delta) {
        full += chunk.delta
        onChunk(chunk)
      }
    } catch {
      // 忽略无法解析的数据帧
    }
    return undefined
  })
  return full
}

/** 触发浏览器下载 Blob 文件 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
