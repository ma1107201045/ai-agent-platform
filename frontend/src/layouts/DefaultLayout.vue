<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Bell, Expand, Fold, Lock, SwitchButton } from '@element-plus/icons-vue'
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
const narrowScreen = window.matchMedia('(max-width: 760px)')
const compact = ref(narrowScreen.matches)
const folded = ref(false)
const collapsed = computed(() => compact.value || folded.value)
function syncWidth() {
  compact.value = narrowScreen.matches
}

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
  narrowScreen.addEventListener('change', syncWidth)
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
  narrowScreen.removeEventListener('change', syncWidth)
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
      <button class="logo" aria-label="AgentForge 工作台" @click="go(DEFAULT_HOME)">
        <img src="/favicon.svg" width="32" height="32" alt="" />
        <span v-show="!collapsed" class="logo-text">AgentForge</span>
      </button>

      <div class="menu-scroll">
        <nav v-if="compact" class="mobile-nav" aria-label="主导航">
          <el-dropdown
            v-for="group in visibleMenuGroups"
            :key="group.key"
            placement="right-start"
            trigger="click"
            @command="onMenuSelect"
          >
            <button
              class="group-button"
              :class="{ current: group.key === activeGroupKey }"
              :aria-label="group.title"
            >
              <el-icon :size="18"><component :is="group.icon" /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="item in group.items"
                  :key="item.path"
                  :command="item.path"
                  :aria-current="activeMenu === item.path ? 'page' : undefined"
                >
                  {{ item.title }}<span v-if="item.planned" class="planned-badge">规划</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </nav>
        <el-menu
          v-else
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

      <div v-if="!compact" class="sidebar-footer">
        <el-tooltip
          :content="collapsed ? '展开菜单' : '收起菜单'"
          placement="right"
          :show-after="200"
        >
          <button
            class="fold-btn"
            :aria-label="collapsed ? '展开菜单' : '收起菜单'"
            @click="folded = !folded"
          >
            <el-icon :size="16"><Expand v-if="collapsed" /><Fold v-else /></el-icon>
            <span v-show="!collapsed" class="fold-text">收起菜单</span>
          </button>
        </el-tooltip>
      </div>
    </div>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="breadcrumb">{{
            menuGroups.find((g) => g.key === activeGroupKey)?.title
          }}</span>
          <span class="breadcrumb" aria-hidden="true">/</span>
          <span class="page-title">{{ route.meta.title }}</span>
        </div>
        <div class="header-right">
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
              <button
                class="header-icon-btn"
                aria-label="通知中心"
                @click="router.push('/workbench/notifications')"
              >
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
.sidebar {
  width: 216px;
  flex: none;
  display: flex;
  flex-direction: column;
  background: var(--bg-aside);
  border-right: 1px solid var(--border-color);
  overflow: hidden;
}
.sidebar.collapsed {
  width: 64px;
}
.logo {
  height: 64px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border: 0;
  border-bottom: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
  font: inherit;
}
.logo-text {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.8px;
}
.sidebar.collapsed .logo,
.sidebar.collapsed .fold-btn {
  justify-content: center;
  padding: 0;
}
.menu-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 14px 10px;
}
.sidebar.collapsed .menu-scroll {
  padding: 14px 0;
}
.menu-scroll :deep(.sidebar-menu) {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--text-secondary);
  --el-menu-hover-bg-color: var(--hover-bg);
  --el-menu-active-color: var(--brand-1);
  --el-menu-item-height: 39px;
  --el-menu-sub-item-height: 35px;
  --el-menu-base-level-padding: 12px;
  border: 0;
}
.sidebar.collapsed :deep(.sidebar-menu) {
  --el-menu-base-level-padding: 20px;
}
.menu-scroll :deep(.el-sub-menu__title),
.menu-scroll :deep(.el-menu-item) {
  font-size: 13px;
  border-radius: 5px;
  margin: 2px 0;
}
.menu-scroll :deep(.el-sub-menu__title) {
  font-weight: 550;
}
.menu-scroll :deep(.el-menu-item.is-active) {
  background: var(--accent-surface);
  color: var(--brand-1);
  font-weight: 650;
  box-shadow: inset 3px 0 var(--brand-1);
}
.menu-scroll :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--text-primary);
}
.mobile-nav {
  display: grid;
  justify-content: center;
  gap: 2px;
}
.group-button {
  width: 44px;
  height: 40px;
  display: grid;
  place-items: center;
  color: var(--text-secondary);
  border: 0;
  border-radius: 5px;
  background: transparent;
  cursor: pointer;
}
.group-button.current,
.group-button:hover {
  color: var(--brand-1);
  background: var(--accent-surface);
}
.sidebar-footer {
  padding: 10px;
  border-top: 1px solid var(--border-color);
}
.fold-btn {
  width: 100%;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.fold-btn:hover {
  background: var(--hover-bg);
}
.header {
  height: 64px;
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 clamp(16px, 2.5vw, 40px);
  gap: 8px;
}
.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-title,
.breadcrumb {
  font-size: 13px;
}
.page-title {
  font-weight: 550;
}
.breadcrumb {
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

  padding: 4px 8px;
  border-radius: 10px;
  transition: background-color 0.2s ease;
}
.user-trigger:hover {
  background: var(--hover-bg);
}
.avatar-ring {
  display: flex;
}
.avatar {
  background: var(--accent-surface);
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

@media (max-width: 760px) {
  .header {
    height: 52px;
    padding: 0 10px;
  }
  .header-right {
    gap: 2px;
  }
  .breadcrumb,
  .user-name,
  .user-arrow {
    display: none;
  }
  .logo {
    height: 52px;
  }
}
</style>

<style>
.planned-badge {
  margin-left: 5px;
  padding: 1px 4px;
  font-size: 10px;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  border-radius: 3px;
}
.el-menu--popup .el-menu-item.is-active {
  background: var(--accent-surface);
  font-weight: 600;
}
</style>
