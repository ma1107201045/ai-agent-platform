import axios from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/token'
import { handleUnauthorized } from '@/utils/stream'
import { buildCacheKey, dedup, getCached, setCached } from '@/utils/httpCache'

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  /** 错误时可选的"下一步操作提示"，由后端给出，前端在报错 toast 中一并展示 */
  actionHint?: string
}

/** 拼接错误消息与操作提示（hint 存在时换行追加） */
function withHint(message: string, hint?: string): string {
  return hint ? `${message}\n${hint}` : message
}

/**
 * axios 配置扩展：
 * - silent=true 时跳过全局错误提示，由调用方自行处理
 * - cache=毫秒数 时该 GET 请求启用 TTL 缓存（仅 GET 生效）
 */
declare module 'axios' {
  interface AxiosRequestConfig {
    silent?: boolean
    cache?: number
  }
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

const defaultAdapter = axios.getAdapter(request.defaults.adapter)

// GET 请求：in-flight 去重 + 可选 TTL 缓存（config.cache）
request.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
  if ((config.method || 'get').toLowerCase() !== 'get') {
    return defaultAdapter(config)
  }
  const key = buildCacheKey(config)
  const ttl = config.cache ?? 0
  if (ttl > 0) {
    const hit = getCached(key)
    if (hit !== undefined) {
      return { data: hit, status: 200, statusText: 'OK', headers: {}, config } as AxiosResponse
    }
  }
  // config 用当前调用方的（保证 silent 等选项按各自调用生效）
  return dedup(key, () => defaultAdapter(config)).then((resp) => {
    const r = resp as AxiosResponse
    if (ttl > 0 && r.status >= 200 && r.status < 300) setCached(key, r.data, ttl)
    return r.config === config ? r : { ...r, config }
  })
}

// 请求拦截器：注入 Token
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一解包 Result；错误默认全局提示，config.silent 时交给调用方处理
request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    if (res.code !== 0) {
      if (!response.config.silent) {
        ElMessage({
          type: 'error',
          message: withHint(res.message || '请求失败', res.actionHint),
          duration: res.actionHint ? 6000 : 3000,
          showClose: !!res.actionHint,
          customClass: 'error-with-hint'
        })
      }
      return Promise.reject(new Error(res.message))
    }
    return res.data as never
  },
  (error) => {
    // 401：登录失效，清登录态并跳转登录页（保留 redirect 回跳）
    if (error.response?.status === 401) {
      handleUnauthorized(error.response.data?.message)
      return Promise.reject(error)
    }
    if (!error.config?.silent) {
      const data = error.response?.data as ApiResult | undefined
      const hint = data?.actionHint
      const message = data?.message || error.message || '网络异常，请稍后重试'
      ElMessage({
        type: 'error',
        message: withHint(message, hint),
        duration: hint ? 6000 : 3000,
        showClose: !!hint,
        customClass: 'error-with-hint'
      })
    }
    return Promise.reject(error)
  }
)

export default request
