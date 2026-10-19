<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Bell,
  Expand,
  Fold,
  Lock,
  Monitor,
  Search,
  SwitchButton
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { DEFAULT_HOME, menuGroups, menuItems } from '@/config/menu'
import ThemeSwitch from '@/components/ThemeSwitch.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

/** 未读数轮询定时器（顶栏挂载期间每 60s 刷新一次） */
let unreadTimer: number | undefined

/** 侧边栏收起：仅保留 64px 图标导航 */
const collapsed = ref(false)

/** 高亮匹配：精确命中优先，其次子路径前缀命中，最后按路由一级分组命中首个菜单 */
const activeMenu = computed(() => {
  const p = route.path
  return (
    menuItems.find((item) => p === item.path)?.path ||
    menuItems.find((item) => p.startsWith(item.path + '/'))?.path ||
    menuGroups.find((g) => p.startsWith('/' + g.key))?.items[0]?.path ||
    DEFAULT_HOME
  )
})

/** 当前路由所属分组 key：手风琴模式下该分组保持展开 */
const activeGroupKey = computed(() => {
  const p = route.path
  return menuGroups.find((g) => p.startsWith('/' + g.key))?.key ?? menuGroups[0]?.key
})

/** 菜单 key：折叠状态或激活分组变化时强制重渲染，保证展开状态与 collapse 样式正确复位 */
const menuKey = computed(
  () => `${collapsed.value ? 'menu-collapsed' : 'menu'}-${activeGroupKey.value}`
)

/** 按角色过滤后的菜单分组：非管理员不展示 adminOnly 项（如供应商管理、团队与权限） */
const visibleMenuGroups = computed(() =>
  menuGroups
    .map((g) => ({
      ...g,
      items: g.items.filter((item) => !item.adminOnly || userStore.isAdmin)
    }))
    .filter((g) => g.items.length > 0)
)

function onMenuSelect(index: string) {
  router.push(index)
}

function go(path: string) {
  router.push(path)
}

const displayName = computed(
  () => userStore.profile?.nickname || userStore.profile?.username || '用户'
)
const avatarText = computed(() => displayName.value.charAt(0).toUpperCase())

function onCommand(command: string | number | object) {
  if (command === 'logout') {
    userStore.logout()
    router.replace('/login')
  } else if (command === 'account') {
    router.push('/system/security')
  }
}

/** 页面重新可见时立即刷新一次未读数 */
function onVisibilityChange() {
  if (!document.hidden) notificationStore.refresh()
}

onMounted(() => {
  if (userStore.token && !userStore.profile) {
    userStore.fetchMe().catch(() => {})
  }
  notificationStore.refresh()
  // 后台标签页不轮询，避免无谓请求
  unreadTimer = window.setInterval(() => {
    if (!document.hidden) notificationStore.refresh()
  }, 60000)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  if (unreadTimer) window.clearInterval(unreadTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

/** 开发环境下校验：菜单项必须存在对应路由，且路径前缀与分组 key 一致 */
if (import.meta.env.DEV) {
  const registered = new Set(router.getRoutes().map((r) => r.path))
  menuGroups.forEach((group) => {
    group.items.forEach((item) => {
      if (!registered.has(item.path)) {
        console.warn(`[menu] 菜单「${item.title}」的 path ${item.path} 未匹配到已注册的路由`)
      } else if (!item.path.startsWith(`/${group.key}`)) {
        console.warn(
          `[menu] 菜单「${item.title}」的 path ${item.path} 与分组前缀 /${group.key} 不一致`
        )
      }
    })
  })
}
</script>

<template>
  <el-container class="layout">
    <!-- 侧边导航：手风琴菜单（分组同一时间仅展开一个，整体折叠仅留图标） -->
    <div class="sidebar" :class="{ collapsed }">
      <div class="logo" @click="go(DEFAULT_HOME)">
        <div class="logo-badge">
          <el-icon :size="19"><Monitor /></el-icon>
        </div>
        <span v-show="!collapsed" class="logo-text">AgentForge</span>
      </div>

      <div class="menu-scroll">
        <el-menu
          :key="menuKey"
          :default-active="activeMenu"
          :default-openeds="[activeGroupKey]"
          :collapse="collapsed"
          :collapse-transition="false"
          unique-opened
          class="sidebar-menu"
          @select="onMenuSelect"
        >
          <el-sub-menu v-for="g in visibleMenuGroups" :key="g.key" :index="g.key">
            <template #title>
              <el-icon :size="18"><component :is="g.icon" /></el-icon>
              <span class="group-label">{{ g.title }}</span>
            </template>
            <el-menu-item v-for="item in g.items" :key="item.path" :index="item.path">
              <el-icon :size="18"><component :is="item.icon" /></el-icon>
              <template #title>
                <span class="item-label">{{ item.title }}</span>
                <span v-if="item.planned" class="planned-badge">规划</span>
              </template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>

      <div class="sidebar-footer">
        <el-tooltip
          :content="collapsed ? '展开菜单' : '收起菜单'"
          placement="right"
          :show-after="200"
        >
          <button class="fold-btn" @click="collapsed = !collapsed">
            <el-icon :size="16"><Expand v-if="collapsed" /><Fold v-else /></el-icon>
            <span v-show="!collapsed" class="fold-text">收起菜单</span>
          </button>
        </el-tooltip>
      </div>
    </div>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="page-title">{{ route.meta.title }}</span>
        </div>
        <div class="header-right">
          <div class="search-box">
            <el-icon class="search-icon"><Search /></el-icon>
            <input class="search-input" placeholder="搜索应用、知识库、模型…" />
            <kbd class="search-kbd">⌘K</kbd>
          </div>
          <el-tooltip
            :content="
              notificationStore.unread > 0
                ? `通知中心（未读 ${notificationStore.unread} 条）`
                : '通知中心'
            "
            placement="bottom"
          >
            <el-badge
              :value="notificationStore.unread"
              :max="99"
              :hidden="notificationStore.unread === 0"
              class="notif-badge"
            >
              <button class="header-icon-btn" @click="router.push('/workbench/notifications')">
                <el-icon :size="17"><Bell /></el-icon>
              </button>
            </el-badge>
          </el-tooltip>
          <ThemeSwitch />
          <el-dropdown class="user-dropdown" @command="onCommand">
            <span class="user-trigger">
              <div class="avatar-ring">
                <el-avatar :size="30" class="avatar">{{ avatarText }}</el-avatar>
              </div>
              <span class="user-name">{{ displayName }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled class="dropdown-user">
                  <span class="dropdown-user-name">{{ displayName }}</span>
                  <span class="dropdown-user-sub">{{
                    userStore.profile?.email || '未绑定邮箱'
                  }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="account">
                  <el-icon><Lock /></el-icon>账号与安全
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
  background: var(--bg-page);
}

/* ============================================================
   侧边导航（一体式折叠菜单）
   ============================================================ */
.sidebar {
  width: 240px;
  height: 100%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #000;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
  transition: width 0.25s ease;
}
.sidebar.collapsed {
  width: 64px;
}

/* ---------- Logo ---------- */
.logo {
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
  overflow: hidden;
}
.sidebar.collapsed .logo {
  justify-content: center;
  padding: 0;
}
.logo-badge {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--brand-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-card);
  transition: transform 0.2s ease;
}
.logo:hover .logo-badge {
  transform: scale(1.06);
}
.logo-text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.2px;
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

/* ---------- 菜单滚动区 ---------- */
.menu-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px;
}
.sidebar.collapsed .menu-scroll {
  padding: 10px 0;
}
.menu-scroll :deep(.sidebar-menu) {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgba(255, 255, 255, 0.65);
  --el-menu-hover-text-color: #fff;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #fff;
  --el-menu-item-height: 40px;
  --el-menu-sub-item-height: 40px;
  border-right: none;
}

/* 手风琴分组标题（可点击展开/收起） */
.menu-scroll :deep(.el-sub-menu__title) {
  height: 40px;
  margin-bottom: 2px;
  border-radius: 10px;
  font-size: 13.5px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.65);
  background: transparent;
}
.menu-scroll :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.menu-scroll :deep(.el-sub-menu__title .el-icon) {
  flex-shrink: 0;
  margin-right: 12px;
  color: inherit;
}
.menu-scroll :deep(.el-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
}
.menu-scroll :deep(.el-sub-menu__title:hover .el-sub-menu__icon-arrow) {
  color: #fff;
}
/* 子项命中当前路由时，分组标题一并高亮 */
.menu-scroll :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #fff;
}

/* 分组子项：收起分组的上下间距 */
.menu-scroll :deep(.el-sub-menu .el-menu) {
  background: transparent;
}

/* 菜单项 */
.menu-scroll :deep(.el-menu-item) {
  margin-bottom: 2px;
  border-radius: 10px;
  font-size: 13.5px;
  color: rgba(255, 255, 255, 0.65);
}
.menu-scroll :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.menu-scroll :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(91, 108, 255, 0.35), rgba(139, 92, 246, 0.35));
  color: #fff;
  font-weight: 600;
}
.menu-scroll :deep(.el-menu-item .el-icon) {
  flex-shrink: 0;
  margin-right: 12px;
  color: inherit;
}
.menu-scroll :deep(.el-menu-item .item-label) {
  color: inherit;
}

/* 折叠状态：分组标题只留图标，箭头与文字隐藏（Element Plus 自动处理），子菜单走 hover 浮层 */

/* ---------- 底部折叠按钮 ---------- */
.sidebar-footer {
  flex-shrink: 0;
  padding: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.sidebar.collapsed .sidebar-footer {
  padding: 10px 0;
}
.fold-btn {
  width: 100%;
  height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    color 0.18s ease;
}
.sidebar.collapsed .fold-btn {
  justify-content: center;
  padding: 0;
}
.fold-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.fold-text {
  white-space: nowrap;
}

/* ============================================================
   顶栏
   ============================================================ */
.header {
  height: 64px;
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 顶栏随主题切换，头部按钮颜色直接继承全局主题变量 */
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 12px;
  background: var(--fill-light);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  width: 260px;
  transition: all 0.2s ease;
}
.search-box:focus-within {
  border-color: var(--brand-1);
  box-shadow: 0 0 0 3px rgba(91, 108, 255, 0.12);
  background: var(--bg-card);
}
.search-icon {
  color: var(--text-tertiary);
}
.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: var(--text-primary);
}
.search-input::placeholder {
  color: var(--text-tertiary);
}
.search-kbd {
  font-size: 11px;
  padding: 2px 6px;
  border: 1px solid var(--border-color);
  border-radius: 5px;
  background: var(--bg-elevated);
  color: var(--text-tertiary);
}

.header-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background-color 0.2s,
    color 0.2s;
}

.header-icon-btn:hover {
  background: var(--hover-bg);
  color: var(--text-primary);
}

/* 顶栏通知未读徽标 */
.notif-badge {
  display: inline-flex;
  line-height: 1;
}
.notif-badge :deep(.el-badge__content) {
  font-weight: 600;
}
.notif-badge:hover :deep(.el-badge__content) {
  background: var(--el-color-danger);
}

.user-dropdown {
  cursor: pointer;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
  outline: none;
  padding: 4px 8px;
  border-radius: 10px;
  transition: background-color 0.2s ease;
}
.user-trigger:hover {
  background: var(--hover-bg);
}
.avatar-ring {
  padding: 2px;
  border-radius: 50%;
  border: 1px solid var(--border-color);
}
.avatar {
  background: var(--bg-card);
  color: var(--brand-1);
  font-size: 13px;
  font-weight: 600;
}
.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}
.user-arrow {
  color: var(--text-tertiary);
  font-size: 12px;
}

.dropdown-user {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 180px;
  border-bottom: 1px solid var(--border-color);
  border-radius: 0;
}

.dropdown-user:hover {
  background: transparent !important;
  cursor: default;
}

.dropdown-user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.dropdown-user-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-secondary);
  word-break: break-all;
}

.main {
  padding: 0;
  overflow-y: auto;
  background: var(--bg-page);
}

/* 折叠过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

<style>
/* ---------- 规划徽章（含折叠浮层内的菜单项） ---------- */
.planned-badge {
  flex-shrink: 0;
  margin-left: 4px;
  padding: 1px 6px;
  border-radius: 6px;
  font-size: 10px;
  line-height: 1.5;
  letter-spacing: 0.5px;
  color: rgba(255, 255, 255, 0.55);
  background: rgba(255, 255, 255, 0.12);
}

/* ---------- 折叠状态 hover 弹出的子菜单浮层 ---------- */
.el-menu--popup {
  --el-menu-bg-color: #17181f;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #fff;
  --el-menu-text-color: rgba(255, 255, 255, 0.65);
  padding: 4px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  box-shadow: var(--shadow-pop);
}
.el-menu--popup .el-menu-item {
  height: 36px;
  border-radius: 6px;
  margin-bottom: 1px;
  font-size: 13px;
}
.el-menu--popup .el-menu-item.is-active {
  background: linear-gradient(135deg, rgba(91, 108, 255, 0.35), rgba(139, 92, 246, 0.35));
  color: #fff;
  font-weight: 600;
}
.el-menu--popup .el-menu-item.is-active .planned-badge {
  color: #fff;
}
</style>
