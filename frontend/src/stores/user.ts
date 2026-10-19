import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import type { UserProfile } from '@/api/types'
import { clearToken, getToken, setToken } from '@/utils/token'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const profile = ref<UserProfile | null>(null)

  /** 是否管理员（控制管理类菜单显隐；权限本身由后端强制校验） */
  const isAdmin = computed(() => profile.value?.role === 'admin')

  async function login(params: { username: string; password: string }) {
    const data = await authApi.login(params)
    token.value = data.token
    setToken(data.token)
    profile.value = data.user
  }

  /** 刷新用户信息（页面加载时恢复登录态） */
  async function fetchMe() {
    if (!token.value) return
    profile.value = await authApi.me()
  }

  function logout() {
    token.value = ''
    profile.value = null
    clearToken()
  }

  return { token, profile, isAdmin, login, fetchMe, logout }
})
