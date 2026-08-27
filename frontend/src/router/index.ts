import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'apps',
        name: 'Apps',
        component: () => import('@/views/apps/index.vue'),
        meta: { title: '应用管理' }
      },
      {
        path: 'apps/:id/edit',
        name: 'AppEdit',
        component: () => import('@/views/apps/edit.vue'),
        meta: { title: '应用编排', hidden: true }
      },
      {
        path: 'models',
        name: 'Models',
        component: () => import('@/views/models/index.vue'),
        meta: { title: '模型管理' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/users/index.vue'),
        meta: { title: '用户管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const WHITE_LIST = ['/login']

router.beforeEach((to) => {
  document.title = `${to.meta.title ? to.meta.title + ' - ' : ''}智能体平台`

  const userStore = useUserStore()
  // 未登录且非白名单 → 登录页
  if (!userStore.token && !WHITE_LIST.includes(to.path)) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 已登录访问登录页 → 首页
  if (userStore.token && to.path === '/login') {
    return '/'
  }
  return true
})

export default router
