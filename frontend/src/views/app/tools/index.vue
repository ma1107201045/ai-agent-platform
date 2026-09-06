<script setup lang="ts">
import {ref, watch, computed} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import AppToolInfo from './info/index.vue'
import AppToolMarketplace from './marketplace/index.vue'
import AppToolIntegrations from './integrations/index.vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  {name: 'infos', label: '工具管理', path: '/app/tools', component: AppToolInfo},
  {name: 'marketplace', label: '插件市场', path: '/app/tool-marketplace', component: AppToolMarketplace},
  {name: 'integrations', label: '数据集成', path: '/app/tool-integrations', component: AppToolIntegrations}
]

function resolveTabName(path: string) {
  return tabs.find((t) => path === t.path || path.startsWith(t.path + '/'))?.name || 'infos'
}

const activeName = ref(resolveTabName(route.path))
const currentTab = computed(() => tabs.find((t) => t.name === activeName.value))

watch(
    () => route.path,
    (path) => {
      activeName.value = resolveTabName(path)
    }
)

function onTabChange(name: string | number) {
  const tab = tabs.find((t) => t.name === name)
  if (tab && route.path !== tab.path) {
    router.push(tab.path)
  }
}
</script>

<template>
  <div class="tool-workspace">
    <div class="workspace-head">
      <div>
        <h2 class="head-title">工具中心</h2>
        <p class="head-desc">管理自定义工具、安装插件模板、配置数据连接器，构建智能体可调用的能力</p>
      </div>
    </div>

    <el-tabs v-model="activeName" type="border-card" class="workspace-tabs" @tab-change="onTabChange">
      <el-tab-pane v-for="t in tabs" :key="t.name" :label="t.label" :name="t.name"/>
    </el-tabs>

    <div class="tab-content">
      <keep-alive>
        <component :is="currentTab?.component" v-if="currentTab" :embedded="true"/>
      </keep-alive>
    </div>
  </div>
</template>

<style scoped>
.tool-workspace {
  padding: 20px 24px 32px;
  max-width: 1440px;
  margin: 0 auto;
}

.workspace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.head-title {
  font-size: 22px;
  font-weight: 700;
}

.head-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-tertiary);
}

.workspace-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.workspace-tabs :deep(.el-tabs__content) {
  display: none;
}

.tab-content {
  background: var(--el-bg-color-overlay, var(--bg-card));
  border: 1px solid var(--el-border-color-light, var(--border-color));
  border-top: none;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  padding: 16px 20px 20px;
}
</style>
