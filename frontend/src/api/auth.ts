import request from './request'
import type { LoginParams, LoginResult, UserProfile } from './types'

export const authApi = {
  login(data: LoginParams) {
    return request.post<never, LoginResult>('/auth/login', data)
  },
  me() {
    return request.get<never, UserProfile>('/auth/me')
  }
}
