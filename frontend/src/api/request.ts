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
      if (!response.config.silent) ElMessage.error(res.message || '请求失败')
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
      const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
