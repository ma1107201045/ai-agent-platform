import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi } from '@/api/sys-notification'
import { getToken } from '@/utils/token'

/** 站内通知未读数：顶栏徽标与通知中心页面共用，保证两端一致 */
export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)

  /** 拉取当前登录用户的未读消息数（未登录时归零并跳过请求） */
  async function refresh() {
    if (!getToken()) {
      unread.value = 0
      return
    }
    unread.value = await notificationApi.unreadCount().catch(() => 0)
  }

  return { unread, refresh }
})
