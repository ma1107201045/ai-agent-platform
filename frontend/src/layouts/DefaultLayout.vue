<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
import { DEFAULT_HOME, menuGroups, menuItems } from '@/config/menu'
import ThemeSwitch from '@/components/ThemeSwitch.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 面板收起：仅保留左侧 64px 图标导航条 */
const collapsed = ref(false)
/** 当前展开的分组 key（点击图标条后右侧弹出该分组的菜单面板） */
const currentGroupKey = ref<string | null>(null)

/** 高亮匹配：精确命中优先，其次子路径前缀命中（如 /app/agents/1/edit 高亮“智能体”） */
const activeMenu = computed(() => {
  const p = route.path
  return (
    menuItems.find((item) => p === item.path)?.path ||
    menuItems.find((item) => p.startsWith(item.path + '/'))?.path ||
    DEFAULT_HOME
  )
})

/** 图标条主体导航（帮助与文档独立放到底部） */
const navGroups = computed(() => menuGroups.filter((g) => g.key !== 'support'))
const footGroups = computed(() => menuGroups.filter((g) => g.key === 'support'))

const currentGroup = computed(
  () => menuGroups.find((g) => g.key === currentGroupKey.value) ?? null
)

/** 路由变化时，若激活菜单跨分组则自动切换面板，保证图标条与菜单高亮同步 */
watch(
  activeMenu,
  (path) => {
    const group = menuGroups.find((g) =>
      g.items.some((it) => path === it.path || path.startsWith(it.path + '/'))
    )
    if (group) {
      if (group.key !== currentGroupKey.value) {
        currentGroupKey.value = group.key
        collapsed.value = false
      }
    } else if (currentGroupKey.value === null) {
      // 页面不在菜单内（如账号安全页）时保持当前分组
      currentGroupKey.value = menuGroups[0]?.key ?? null
    }
  },
  { immediate: true }
)

function openGroup(key: string) {
  if (key === currentGroupKey.value && !collapsed.value) {
    // 再次点击已展开的分组 → 收起面板
    collapsed.value = true
    return
  }
  currentGroupKey.value = key
  collapsed.value = false
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
    <!-- 侧边导航：左侧图标条 + 右侧分组菜单面板 -->
    <div class="sidebar" :class="{ collapsed }">
      <!-- 图标条 -->
      <div class="rail">
        <el-tooltip content="AgentForge" placement="right" :show-after="200">
          <div class="rail-logo" @click="go(DEFAULT_HOME)">
            <div class="rail-logo-badge">
              <el-icon :size="19"><Monitor /></el-icon>
            </div>
          </div>
        </el-tooltip>

        <div class="rail-nav">
          <el-tooltip
            v-for="g in navGroups"
            :key="g.key"
            :content="g.title"
            placement="right"
            :show-after="200"
          >
            <button
              class="rail-btn"
              :class="{ active: g.key === currentGroupKey }"
              @click="openGroup(g.key)"
            >
              <el-icon :size="18"><component :is="g.icon" /></el-icon>
            </button>
          </el-tooltip>
        </div>

        <div class="rail-bottom">
          <el-tooltip
            v-for="g in footGroups"
            :key="g.key"
            :content="g.title"
            placement="right"
            :show-after="200"
          >
            <button
              class="rail-btn"
              :class="{ active: g.key === currentGroupKey }"
              @click="openGroup(g.key)"
            >
              <el-icon :size="18"><component :is="g.icon" /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip :content="collapsed ? '展开菜单' : '收起菜单'" placement="right" :show-after="200">
            <button class="rail-btn" @click="collapsed = !collapsed">
              <el-icon :size="16"><Expand v-if="collapsed" /><Fold v-else /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </div>

      <!-- 分组菜单面板 -->
      <div class="panel">
        <template v-if="currentGroup">
          <div class="panel-header">
            <el-icon class="panel-header-icon" :size="16">
              <component :is="currentGroup.icon" />
            </el-icon>
            <span class="panel-header-title">{{ currentGroup.title }}</span>
          </div>
          <div class="panel-list">
            <button
              v-for="item in currentGroup.items"
              :key="item.path"
              class="panel-item"
              :class="{ 'is-active': item.path === activeMenu }"
              @click="go(item.path)"
            >
              <el-icon class="pi-icon" :size="15"><component :is="item.icon" /></el-icon>
              <span class="panel-item-title">{{ item.title }}</span>
              <span v-if="item.planned" class="planned-badge">规划</span>
            </button>
          </div>
        </template>
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

/* ============================================================
   侧边导航（图标条 + 分组面板）
   ============================================================ */
.sidebar {
  height: 100%;
  width: 264px;
  flex-shrink: 0;
  display: flex;
  background: var(--bg-card);
  border-right: 1px solid var(--border-color);
  overflow: hidden;
  transition: width 0.25s ease;
}
.sidebar.collapsed {
  width: 64px;
}

/* ---------- 图标条 ---------- */
.rail {
  width: 64px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.rail-logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  user-select: none;
}
.rail-logo-badge {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--brand-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-card);
  transition: transform 0.2s ease;
}
.rail-logo:hover .rail-logo-badge {
  transform: scale(1.06);
}
.rail-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 6px 0 12px;
  overflow-y: auto;
  overflow-x: hidden;
}
.rail-btn {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}
.rail-btn:hover {
  background: var(--fill-light);
  color: var(--text-primary);
}
.rail-btn.active {
  background: var(--brand-gradient);
  color: #fff;
  box-shadow: var(--shadow-card);
}
.rail-bottom {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 0 14px;
  border-top: 1px solid var(--border-color);
}

/* ---------- 分组菜单面板 ---------- */
.panel {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
}
.panel-header {
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-color);
}
.panel-header-icon {
  color: var(--brand-1);
}
.panel-header-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.panel-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px 12px 16px;
}
.panel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 38px;
  margin-bottom: 4px;
  padding: 0 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.panel-item .pi-icon {
  flex-shrink: 0;
  color: var(--text-secondary);
  transition: color 0.15s ease;
}
.panel-item:hover {
  background: var(--fill-light);
  color: var(--text-primary);
}
.panel-item:hover .pi-icon {
  color: var(--text-primary);
}
.panel-item.is-active {
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  font-weight: 600;
}
.panel-item.is-active .pi-icon {
  color: var(--brand-1);
}
.panel-item-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.planned-badge {
  flex-shrink: 0;
  margin-left: 4px;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 10px;
  line-height: 1.4;
  letter-spacing: 0.5px;
  color: var(--text-tertiary);
  background: var(--fill-light);
}

/* ============================================================
   顶栏
   ============================================================ */
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
