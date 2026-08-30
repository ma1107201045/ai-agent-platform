<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Bell,
  Fold,
  Lock,
  Monitor,
  Search,
  SwitchButton
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { DEFAULT_HOME, menuGroups, menuItems } from '@/config/menu'
import ThemeSwitch from '@/components/ThemeSwitch.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

/** 高亮匹配：精确命中优先，其次子路径前缀命中（如 /apps/1/edit 高亮“智能体”） */
const activeMenu = computed(() => {
  const p = route.path
  return (
    menuItems.find((item) => p === item.path)?.path ||
    menuItems.find((item) => p.startsWith(item.path + '/'))?.path ||
    DEFAULT_HOME
  )
})

/** 默认展开全部分组 */
const openedGroups = menuGroups.map((g) => g.key)
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

onMounted(() => {
  if (userStore.token && !userStore.profile) {
    userStore.fetchMe().catch(() => {})
  }
})

/** 开发环境下校验：菜单项必须存在对应路由，且路径前缀与分组 key 一致 */
if (import.meta.env.DEV) {
  const registered = new Set(router.getRoutes().map((r) => r.path))
  menuGroups.forEach((group) => {
    group.items.forEach((item) => {
      if (!registered.has(item.path)) {
        console.warn(`[menu] 菜单「${item.title}」的 path ${item.path} 未匹配到已注册的路由`)
      } else if (!item.path.startsWith(`/${group.key}`)) {
        console.warn(`[menu] 菜单「${item.title}」的 path ${item.path} 与分组前缀 /${group.key} 不一致`)
      }
    })
  })
}
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '72px' : '228px'" class="aside">
      <div class="logo" @click="router.push(DEFAULT_HOME)">
        <div class="logo-badge">
          <el-icon :size="20"><Monitor /></el-icon>
        </div>
        <transition name="fade">
          <span v-if="!collapsed" class="logo-text">AgentForge</span>
        </transition>
      </div>

      <div class="menu-wrap">
        <el-menu
          :default-active="activeMenu"
          :default-openeds="openedGroups"
          router
          :collapse="collapsed"
          :collapse-transition="false"
          class="menu"
        >
          <el-sub-menu v-for="g in menuGroups" :key="g.key" :index="g.key" class="menu-group">
            <template #title>
              <el-icon class="group-icon"><component :is="g.icon" /></el-icon>
              <span class="group-title">{{ g.title }}</span>
            </template>
            <el-menu-item v-for="item in g.items" :key="item.path" :index="item.path">
              <el-icon class="item-icon" :size="16"><component :is="item.icon" /></el-icon>
              <template #title>
                <span class="menu-item-title">{{ item.title }}</span>
                <span v-if="item.planned" class="planned-badge">规划</span>
              </template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>

      <div class="aside-footer">
        <el-tooltip :content="collapsed ? '展开菜单' : '收起菜单'" placement="right">
          <div class="collapse-btn" @click="collapsed = !collapsed">
            <el-icon :size="16"><Fold /></el-icon>
            <span v-if="!collapsed">收起菜单</span>
          </div>
        </el-tooltip>
      </div>
    </el-aside>

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
          <el-tooltip content="通知中心" placement="bottom">
            <button class="header-icon-btn" @click="router.push('/workbench/notifications')">
              <el-icon :size="17"><Bell /></el-icon>
            </button>
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
                  <span class="dropdown-user-sub">{{ userStore.profile?.email || '未绑定邮箱' }}</span>
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

/* ---------- 侧边栏 ---------- */
.aside {
  background: var(--bg-card);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease;
  overflow: hidden;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  cursor: pointer;
  flex-shrink: 0;
  user-select: none;
}
.logo-badge {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--brand-1);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
}

.menu-wrap {
  flex: 1;
  padding: 8px 12px 20px;
  overflow-y: auto;
  overflow-x: hidden;
}
.menu {
  border-right: none;
  background: transparent;
  --el-menu-item-height: 36px;
}

/* ---------- 一级分类：分组标题（克制简洁） ---------- */
.menu :deep(.el-sub-menu) {
  margin-top: 12px;
}
.menu :deep(.el-sub-menu:first-child) {
  margin-top: 2px;
}
.menu :deep(.el-sub-menu__title) {
  height: 36px;
  margin: 0;
  padding: 0 8px !important;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: var(--text-secondary);
  gap: 6px;
  transition: color 0.15s ease;
}
.menu :deep(.el-sub-menu__title .group-icon) {
  font-size: 14px;
  color: var(--text-secondary);
  transition: color 0.15s ease;
}
.menu :deep(.el-sub-menu__icon-arrow) {
  display: none;
}
.menu :deep(.el-sub-menu__title:hover) {
  background: transparent;
  color: var(--text-secondary);
}
.menu :deep(.el-sub-menu__title:hover .group-icon) {
  color: var(--text-secondary);
}
.menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--text-secondary);
}
.menu :deep(.el-sub-menu.is-active > .el-sub-menu__title .group-icon) {
  color: var(--text-secondary);
}
/* 折叠态：分组标题仅保留图标 */
.menu.el-menu--collapse :deep(.el-sub-menu__title) {
  padding: 0 !important;
  justify-content: center;
}

/* ---------- 二级菜单项（克制简洁） ---------- */
.menu :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  height: 36px;
  margin: 1px 0;
  padding-left: 20px !important;
  border-radius: 6px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-secondary);
  transition: background-color 0.15s ease, color 0.15s ease;
}
.menu :deep(.el-sub-menu .el-menu-item .item-icon) {
  margin-right: 8px;
  color: var(--text-secondary);
  transition: color 0.15s ease;
}
.menu :deep(.el-sub-menu .el-menu-item:hover) {
  background: var(--fill-light);
  color: var(--text-primary);
}
.menu :deep(.el-sub-menu .el-menu-item:hover .item-icon) {
  color: var(--text-primary);
}
.menu :deep(.el-sub-menu .el-menu-item.is-active) {
  background: var(--fill-light);
  color: var(--brand-1);
  font-weight: 600;
}
.menu :deep(.el-sub-menu .el-menu-item.is-active .item-icon) {
  color: var(--brand-1);
}
.menu:not(.el-menu--collapse) :deep(.el-sub-menu .el-menu-item.is-active) {
  position: relative;
}
.menu:not(.el-menu--collapse) :deep(.el-sub-menu .el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 16px;
  border-radius: 2px;
  background: var(--brand-1);
}

.menu-item-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.planned-badge {
  flex-shrink: 0;
  margin-left: 8px;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 10px;
  line-height: 1.4;
  letter-spacing: 0.5px;
  color: var(--text-tertiary);
  background: var(--fill-light);
}

.aside-footer {
  padding: 12px;
  border-top: 1px solid var(--border-color);
}
.collapse-btn {
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.collapse-btn:hover {
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
}

/* ---------- 顶栏 ---------- */
.header {
  height: 64px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 12px;
  background: var(--bg-page);
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
  transition: background-color 0.2s, color 0.2s;
}

.header-icon-btn:hover {
  background: var(--hover-bg);
  color: var(--text-primary);
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
  background: var(--bg-page);
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
