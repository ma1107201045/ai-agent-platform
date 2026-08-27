<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Aim,
  ArrowDown,
  Avatar,
  Bell,
  Box,
  ChatDotRound,
  Clock,
  Collection,
  CollectionTag,
  CreditCard,
  Connection,
  CopyDocument,
  Cpu,
  DataAnalysis,
  DataBoard,
  DataLine,
  Delete,
  Document,
  Folder,
  EditPen,
  Files,
  FolderOpened,
  Fold,
  Grid,
  Histogram,
  Key,
  Link,
  List,
  Lock,
  MagicStick,
  Management,
  Monitor,
  Notebook,
  Notification,
  Odometer,
  OfficeBuilding,
  Operation,
  Picture,
  Platform,
  Promotion,
  QuestionFilled,
  Reading,
  Search,
  SemiSelect,
  Setting,
  Share,
  Shop,
  ShoppingCart,
  Umbrella,
  SwitchButton,
  Timer,
  Tools,
  TrendCharts,
  User,
  WarningFilled
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

/** 侧边栏菜单：一级分类 + 二级页面；planned 标记的为规划中的模块（指向建设中占位页） */
const menuGroups = [
  {
    key: 'workbench',
    title: '工作台',
    icon: Odometer,
    items: [
      { path: '/dashboard', title: '工作台概览', icon: Odometer },
      { path: '/notifications', title: '通知中心', icon: Bell, planned: true }
    ]
  },
  {
    key: 'agents',
    title: '智能体',
    icon: MagicStick,
    items: [
      { path: '/apps', title: '应用管理', icon: Grid },
      { path: '/apps/templates', title: '应用模板', icon: CopyDocument, planned: true },
      { path: '/apps/marketplace', title: '应用市场', icon: Shop, planned: true },
      { path: '/apps/multi-agent', title: '多智能体编排', icon: Avatar, planned: true },
      { path: '/apps/prompts', title: '提示词库', icon: Collection, planned: true },
      { path: '/apps/schedules', title: '定时任务', icon: Timer, planned: true },
      { path: '/apps/guardrails', title: '内容安全', icon: Umbrella, planned: true }
    ]
  },
  {
    key: 'knowledge',
    title: '数据',
    icon: Files,
    items: [
      { path: '/knowledge', title: '知识库', icon: FolderOpened },
      { path: '/apps/memory', title: '记忆管理', icon: Notebook, planned: true },
      { path: '/apps/storage', title: '数据存储', icon: Folder, planned: true },
      { path: '/knowledge/assets', title: '素材管理', icon: Picture, planned: true }
    ]
  },
  {
    key: 'tools',
    title: '工具',
    icon: Tools,
    items: [
      { path: '/tools', title: '工具管理', icon: Operation },
      { path: '/tools/marketplace', title: '插件市场', icon: ShoppingCart, planned: true },
      { path: '/tools/integrations', title: '数据集成', icon: Link, planned: true }
    ]
  },
  {
    key: 'publish',
    title: '发布',
    icon: Promotion,
    items: [
      { path: '/publish', title: '发布管理', icon: Share },
      { path: '/publish/channels', title: '渠道管理', icon: Platform, planned: true },
      { path: '/publish/api-keys', title: 'API 密钥', icon: Key, planned: true },
      { path: '/publish/docs', title: 'API 文档', icon: Reading, planned: true },
      { path: '/publish/versions', title: '版本历史', icon: Clock, planned: true }
    ]
  },
  {
    key: 'observe',
    title: '观测',
    icon: DataLine,
    items: [
      { path: '/ops', title: '运行监控', icon: TrendCharts },
      { path: '/conversations', title: '对话记录', icon: ChatDotRound },
      { path: '/conversations/label', title: '对话标注', icon: CollectionTag, planned: true },
      { path: '/ops/usage', title: '用量统计', icon: Histogram, planned: true },
      { path: '/ops/billing', title: '费用账单', icon: CreditCard, planned: true },
      { path: '/ops/alerts', title: '告警管理', icon: WarningFilled, planned: true }
    ]
  },
  {
    key: 'eval',
    title: '评测',
    icon: Aim,
    items: [
      { path: '/eval', title: '评测中心', icon: DataAnalysis, planned: true },
      { path: '/eval/datasets', title: '评测数据集', icon: DataBoard, planned: true },
      { path: '/eval/experiments', title: '对比实验', icon: SemiSelect, planned: true }
    ]
  },
  {
    key: 'model',
    title: '模型',
    icon: Cpu,
    items: [
      { path: '/models', title: '供应商管理', icon: Management },
      { path: '/models/playground', title: '模型广场', icon: Box, planned: true },
      { path: '/models/finetune', title: '模型微调', icon: EditPen, planned: true },
      { path: '/models/gateway', title: '模型网关', icon: Connection, planned: true }
    ]
  },
  {
    key: 'system',
    title: '系统管理',
    icon: Setting,
    items: [
      { path: '/users', title: '团队与权限', icon: User },
      { path: '/users/audit', title: '操作日志', icon: Document, planned: true },
      { path: '/workspace', title: '工作空间', icon: OfficeBuilding, planned: true },
      { path: '/trash', title: '回收站', icon: Delete, planned: true },
      { path: '/system/announcements', title: '公告管理', icon: Notification, planned: true }
    ]
  },
  {
    key: 'help',
    title: '帮助与文档',
    icon: QuestionFilled,
    items: [
      { path: '/help', title: '使用指南', icon: QuestionFilled, planned: true },
      { path: '/changelog', title: '更新日志', icon: List, planned: true }
    ]
  }
]

/** 高亮匹配：精确命中优先，其次一级路径前缀命中（如 /apps/1/edit 高亮应用管理） */
const activeMenu = computed(() => {
  const p = route.path
  const allPaths = menuGroups.flatMap((g) => g.items.map((i) => i.path))
  return (
    allPaths.find((path) => p === path) ||
    allPaths.find((path) => p.startsWith(path + '/')) ||
    '/dashboard'
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
    router.push('/users/security')
  }
}

onMounted(() => {
  if (userStore.token && !userStore.profile) {
    userStore.fetchMe().catch(() => {})
  }
})
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '72px' : '228px'" class="aside">
      <div class="logo" @click="router.push('/dashboard')">
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
                <el-tag
                  v-if="item.planned"
                  size="small"
                  type="info"
                  effect="plain"
                  class="menu-item-tag"
                >
                  规划
                </el-tag>
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
            <button class="header-icon-btn" @click="router.push('/notifications')">
              <el-icon :size="17"><Bell /></el-icon>
            </button>
          </el-tooltip>
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
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
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
  border-radius: 10px;
  background: var(--brand-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(91, 108, 255, 0.4);
  flex-shrink: 0;
}
.logo-text {
  font-size: 17px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  white-space: nowrap;
}

.menu-wrap {
  flex: 1;
  padding: 6px 10px 16px;
  overflow-y: auto;
  overflow-x: hidden;
}
.menu {
  border-right: none;
  background: transparent;
  --el-menu-item-height: 38px;
}

/* ---------- 一级分类：分组标题风格 ---------- */
.menu :deep(.el-sub-menu) {
  margin-top: 16px;
}
.menu :deep(.el-sub-menu:first-child) {
  margin-top: 4px;
}
.menu :deep(.el-sub-menu__title) {
  height: 30px;
  margin: 0;
  padding: 0 10px !important;
  border-radius: 8px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 1.5px;
  color: var(--text-tertiary);
  transition: color 0.2s ease;
}
.menu :deep(.el-sub-menu__title .group-icon) {
  font-size: 14px;
  margin-right: 6px;
  color: var(--text-tertiary);
  transition: color 0.2s ease;
}
.menu :deep(.el-sub-menu__icon-arrow) {
  display: none;
}
.menu :deep(.el-sub-menu__title:hover) {
  background: transparent;
  color: var(--brand-1);
}
.menu :deep(.el-sub-menu__title:hover .group-icon) {
  color: var(--brand-1);
}
.menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--brand-1);
}
.menu :deep(.el-sub-menu.is-active > .el-sub-menu__title .group-icon) {
  color: var(--brand-1);
}

/* ---------- 二级菜单项 ---------- */
.menu :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  height: 38px;
  margin: 2px 0;
  padding-left: 22px !important;
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all 0.18s ease;
}
.menu :deep(.el-sub-menu .el-menu-item .item-icon) {
  margin-right: 8px;
  color: var(--text-tertiary);
  transition: color 0.18s ease;
}
.menu :deep(.el-sub-menu .el-menu-item:hover) {
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
}
.menu :deep(.el-sub-menu .el-menu-item:hover .item-icon) {
  color: var(--brand-1);
}
.menu :deep(.el-sub-menu .el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(91, 108, 255, 0.1), rgba(139, 92, 246, 0.1));
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
  border-radius: 3px;
  background: var(--brand-gradient);
}

.menu-item-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.menu-item-tag {
  flex-shrink: 0;
  margin-left: 8px;
  transform: scale(0.85);
  transform-origin: center right;
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
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
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
  gap: 20px;
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
  background: #fff;
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
  background: #fff;
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
  background: var(--brand-gradient);
}
.avatar {
  background: #fff;
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
