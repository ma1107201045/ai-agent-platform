<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Cpu, Connection, DataAnalysis, SwitchButton, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menus = [
  { path: '/dashboard', title: '工作台', icon: DataAnalysis },
  { path: '/apps', title: '应用管理', icon: Connection },
  { path: '/models', title: '模型管理', icon: Cpu },
  { path: '/users', title: '用户管理', icon: User }
]

const activeMenu = computed(() => route.path)
const displayName = computed(
  () => userStore.profile?.nickname || userStore.profile?.username || '用户'
)

function onCommand(command: string | number | object) {
  if (command === 'logout') {
    userStore.logout()
    router.replace('/login')
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
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon :size="24"><Cpu /></el-icon>
        <span>智能体平台</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title }}</span>
        <el-dropdown class="user-dropdown" @command="onCommand">
          <span class="user-trigger">
            <el-avatar :size="28" class="avatar">
              {{ displayName.charAt(0).toUpperCase() }}
            </el-avatar>
            <span class="user-name">{{ displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}
.menu {
  flex: 1;
  border-right: none;
  background: transparent;
  --el-menu-text-color: rgba(255, 255, 255, 0.65);
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #fff;
}
.header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.user-dropdown {
  cursor: pointer;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
  outline: none;
}
.avatar {
  background: #2d5da8;
  color: #fff;
  font-size: 13px;
}
.user-name {
  font-size: 14px;
}
.main {
  overflow-y: auto;
}
</style>
