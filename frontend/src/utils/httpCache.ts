/**
 * 轻量 HTTP 缓存：
 * 1. in-flight 去重——相同 GET 并发进行时只真正发一次请求，其余复用同一 Promise
 * 2. 可选 TTL 缓存——调用方通过 axios config.cache = 毫秒数 开启
 * 仅作用于 GET 请求；非 GET 自动绕过。
 */

interface CacheEntry {
  /** 过期时间戳（ms） */
  expiry: number
  /** 缓存的响应体（axios response.data，即后端 Result 包装） */
  data: unknown
}

const cache = new Map<string, CacheEntry>()
const inflight = new Map<string, Promise<unknown>>()

/** 由 axios config 生成缓存键（method + url + params 序列化） */
export function buildCacheKey(config: {
  method?: string
  baseURL?: string
  url?: string
  params?: unknown
}): string {
  const method = (config.method || 'get').toLowerCase()
  const params = config.params ? stableStringify(config.params) : ''
  return `${method}:${config.baseURL || ''}${config.url || ''}?${params}`
}

/** 对象键排序后序列化，保证参数顺序不同但语义相同的请求命中同一键 */
function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value) ?? ''
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`
  const obj = value as Record<string, unknown>
  const keys = Object.keys(obj).sort()
  return `{${keys.map((k) => `${JSON.stringify(k)}:${stableStringify(obj[k])}`).join(',')}}`
}

/** 读取未过期的缓存项；过期即删并返回 undefined */
export function getCached(key: string): unknown {
  const entry = cache.get(key)
  if (!entry) return undefined
  if (Date.now() > entry.expiry) {
    cache.delete(key)
    return undefined
  }
  return entry.data
}

export function setCached(key: string, data: unknown, ttl: number): void {
  cache.set(key, { expiry: Date.now() + ttl, data })
}

/** 使指定键失效（写操作后调用方按需清理） */
export function invalidate(key: string): void {
  cache.delete(key)
}

/** 按 URL 片段批量失效（写操作后清理相关列表缓存，如 invalidatePrefix('/model/models')） */
export function invalidatePrefix(urlFragment: string): void {
  for (const key of cache.keys()) {
    if (key.includes(urlFragment)) cache.delete(key)
  }
}

/** 清空全部缓存（退出登录 / 切换租户时调用） */
export function clearHttpCache(): void {
  cache.clear()
}

/**
 * in-flight 去重：同 key 的并发调用共享首个 Promise。
 * 注意：共享的是原始 Promise，错误也会同步给所有等待方。
 */
export function dedup<T>(key: string, fn: () => Promise<T>): Promise<T> {
  const existing = inflight.get(key)
  if (existing) return existing as Promise<T>
  const promise = fn().finally(() => inflight.delete(key))
  inflight.set(key, promise)
  return promise
}
