<script setup lang="ts">
import {ref, watch} from 'vue'
import AppToolInfo from './info/index.vue'
import AppToolMarketplace from './marketplace/index.vue'
import AppToolIntegrations from './integrations/index.vue'

const tabs = [
  {name: 'infos', label: '工具管理', component: AppToolInfo},
  {name: 'marketplace', label: '插件市场', component: AppToolMarketplace},
  {name: 'integrations', label: '数据集成', component: AppToolIntegrations}
]

/** 当前激活 tab，纯本地状态切换，不经过路由 */
const activeName = ref<any>('infos')

/**
 * 已挂载过的 tab（懒挂载 + 状态保留，替代 keep-alive 动态组件缓存）。
 * 首次切入才创建组件实例，之后切换仅 v-show 显隐，不再卸载重建。
 */
const mountedTabs = ref<string[]>(['infos'])

watch(
    activeName,
    (name) => {
      if (!mountedTabs.value.includes(name)) {
        mountedTabs.value = [...mountedTabs.value, name]
      }
    },
    {immediate: true}
)

/** 子组件内部引导（如「去插件市场 / 去数据集成 / 去工具管理」）→ 页内切换 tab */
function onSwitchTab(name: string | number) {
  if (tabs.some((t) => t.name === name)) {
    activeName.value = name
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

    <el-tabs v-model="activeName" type="border-card" class="workspace-tabs">
      <el-tab-pane v-for="t in tabs" :key="t.name" :label="t.label" :name="t.name"/>
    </el-tabs>

    <div class="tab-content">
      <template v-for="t in tabs" :key="t.name">
        <component
            v-if="mountedTabs.includes(t.name)"
            v-show="activeName === t.name"
            :is="t.component"
            :embedded="true"
            @switch-tab="onSwitchTab"
        />
      </template>
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
