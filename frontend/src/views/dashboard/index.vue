<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Box, Collection, Connection, Cpu, Files, Grid, Plus, Right } from '@element-plus/icons-vue'
import { appAgentApi } from '@/api/app-agent'
import { modelApi } from '@/api/model'
import { knowledgeApi } from '@/api/knowledge'
import type { AppAgent } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'
import { typeLabels } from '@/views/app/agents/agentMeta'
import CreateAppDialog from '@/views/app/agents/components/CreateAppDialog.vue'

const user = useUserStore()
const createDialog = ref<InstanceType<typeof CreateAppDialog>>()
const apps = ref<AppAgent[]>([])
const appCount = ref<number>()
const datasetCount = ref<number>()
const providerCount = ref<number>()
const loading = ref(true)

async function load() {
  loading.value = true
  const [applications, datasets, providers] = await Promise.allSettled([
    appAgentApi.page({ page: 1, size: 5 }),
    knowledgeApi.datasetPage({ page: 1, size: 1 }),
    modelApi.providerPage({ page: 1, size: 1 })
  ])
  appCount.value = applications.status === 'fulfilled' ? applications.value.total : undefined
  apps.value = applications.status === 'fulfilled' ? applications.value.records : []
  datasetCount.value = datasets.status === 'fulfilled' ? datasets.value.total : undefined
  providerCount.value = providers.status === 'fulfilled' ? providers.value.total : undefined
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="dashboard">
    <header class="overview-head">
      <h1>工作台概览</h1>
      <el-button type="primary" :icon="Plus" @click="createDialog?.open()">新建应用</el-button>
    </header>
    <dl class="metrics" aria-label="工作空间资源数量" :aria-busy="loading">
      <div>
        <dt>
          <el-icon><Grid /></el-icon>智能体应用
        </dt>
        <dd>{{ appCount ?? '—' }}</dd>
      </div>
      <div>
        <dt>
          <el-icon><Files /></el-icon>知识库数据集
        </dt>
        <dd>{{ datasetCount ?? '—' }}</dd>
      </div>
      <div>
        <dt>
          <el-icon><Cpu /></el-icon>模型供应商
        </dt>
        <dd>{{ providerCount ?? '—' }}</dd>
      </div>
    </dl>
    <div class="work-sections">
      <section>
        <header class="section-head">
          <h2>最近创建的应用</h2>
          <router-link to="/app/agents"
            >全部应用 <el-icon><Right /></el-icon
          ></router-link>
        </header>
        <div class="recent-list" :aria-busy="loading">
          <p v-if="loading" class="list-note">正在读取应用…</p>
          <div v-else-if="appCount === undefined" class="list-note">
            应用列表暂时无法加载<el-button link type="primary" @click="load">重试</el-button>
          </div>
          <div v-else-if="!apps.length" class="list-note">
            <el-icon :size="26"><Box /></el-icon>
            <p>还没有应用</p>
            <el-button link type="primary" @click="createDialog?.open()">创建第一个应用</el-button>
          </div>
          <router-link
            v-for="app in apps"
            :key="app.id"
            class="recent-row"
            :to="`/app/agents/${app.id}/edit`"
          >
            <span class="row-info"
              ><strong>{{ app.name }}</strong
              ><small
                >{{ typeLabels[app.type] || app.type }} · {{ formatTime(app.createTime) }}</small
              ></span
            >
            <span class="status" :class="{ published: app.status === 1 }">{{
              app.status === 1 ? '已发布' : '草稿'
            }}</span>
            <el-icon><Right /></el-icon>
          </router-link>
        </div>
      </section>
      <section>
        <header class="section-head"><h2>常用入口</h2></header>
        <nav class="shortcuts" aria-label="常用入口">
          <router-link to="/data/knowledge"
            ><el-icon><Collection /></el-icon>管理知识库<el-icon><Right /></el-icon
          ></router-link>
          <router-link :to="user.isAdmin ? '/models' : '/models/playground'"
            ><el-icon><Cpu /></el-icon>{{ user.isAdmin ? '接入模型' : '模型广场'
            }}<el-icon><Right /></el-icon
          ></router-link>
          <router-link to="/publish"
            ><el-icon><Connection /></el-icon>已发布应用<el-icon><Right /></el-icon
          ></router-link>
        </nav>
      </section>
    </div>
    <CreateAppDialog ref="createDialog" @created="load" />
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1360px;
  margin: 0 auto;
  padding: clamp(24px, 3.5vw, 54px) clamp(16px, 3.5vw, 54px);
}
.overview-head,
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.overview-head {
  margin-bottom: 28px;
}
h1 {
  font-size: clamp(22px, 2.4vw, 34px);
  letter-spacing: -0.045em;
  font-weight: 620;
}
h2 {
  font-size: 17px;
  font-weight: 600;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid var(--border-color);
  border-top: 2px solid var(--brand-1);
  background: var(--bg-card);
  border-radius: 5px;
}
.metrics > div {
  padding: clamp(14px, 2.3vw, 32px);
}
.metrics > div + div {
  border-left: 1px solid var(--border-color);
}
dt {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: var(--text-secondary);
}
dd {
  margin-top: 12px;
  font-size: clamp(28px, 3.4vw, 46px);
  font-weight: 520;
  line-height: 1.1;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}
.work-sections {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(190px, 1fr);
  gap: clamp(22px, 3vw, 44px);
  margin-top: 38px;
}
.section-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-color);
}
a {
  color: inherit;
  text-decoration: none;
}
.section-head a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--brand-1);
}
.recent-row,
.shortcuts a {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 4px;
  border-bottom: 1px solid var(--border-color);
}
.recent-row:hover,
.shortcuts a:hover {
  background: var(--accent-surface);
}
.row-info {
  flex: 1;
  min-width: 0;
}
.row-info strong,
.row-info small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-info strong {
  font-size: 14px;
  font-weight: 600;
}
.row-info small {
  margin-top: 5px;
  font-size: 12px;
  color: var(--text-secondary);
}
.status {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.published {
  color: var(--published-text);
}
.shortcuts a {
  font-size: 14px;
  min-height: 54px;
}
.shortcuts .el-icon:last-child {
  margin-left: auto;
}
.recent-row > .el-icon,
.shortcuts .el-icon {
  color: var(--text-secondary);
}
.list-note {
  padding: 32px 12px;
  display: grid;
  justify-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}
@media (max-width: 950px) {
  .work-sections {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 600px) {
  .dashboard {
    padding: 22px 12px;
  }
  .overview-head {
    margin-bottom: 18px;
  }
  .metrics > div {
    padding: 14px 7px;
  }
  dt {
    font-size: 12px;
  }
  dt .el-icon {
    display: none;
  }
  .work-sections {
    margin-top: 24px;
  }
  .recent-row {
    gap: 6px;
  }
}
</style>
