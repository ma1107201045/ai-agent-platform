import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearToken, getToken } from '@/utils/token'

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器：注入 Token
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一解包 Result，错误统一提示
request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res.data as never
  },
  (error) => {
    // 401：登录失效，跳转登录页
    if (error.response?.status === 401) {
      clearToken()
      if (!location.pathname.startsWith('/login')) {
        ElMessage.error(error.response.data?.message || '登录已过期，请重新登录')
        location.href = '/login'
      }
      return Promise.reject(error)
    }
    const message =
      error.response?.data?.message || error.message || '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
