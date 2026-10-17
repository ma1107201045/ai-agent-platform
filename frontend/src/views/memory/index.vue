<template>
  <div class="page-container memory-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head-info">
        <h2 class="head-title">记忆管理</h2>
        <p class="head-desc">
          管理智能体的长期记忆与会话变量：手动沉淀记忆、配置自动抽取策略，让智能体记住用户的偏好与上下文。
        </p>
      </div>
      <div class="head-actions">
        <span class="action-label">选择智能体</span>
        <el-select
          v-model="selectedAppId"
          placeholder="选择智能体应用"
          filterable
          class="app-select"
          :disabled="!apps.length"
        >
          <el-option v-for="app in apps" :key="app.id" :value="app.id" :label="app.name" />
        </el-select>
      </div>
    </div>

    <!-- 无应用引导 -->
    <div v-if="!apps.length" class="hover-card empty-guide">
      <el-empty description="还没有智能体应用，请先创建一个应用，再为其配置记忆">
        <el-button type="primary" @click="goCreateApp">去创建应用</el-button>
      </el-empty>
    </div>

    <!-- 主区域 -->
    <template v-else>
      <div class="stat-row">
        <div class="hover-card stat-item" v-loading="loading">
          <div class="stat-icon" :class="strategy?.enabled ? 'on' : 'off'">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="stat-body">
            <p class="stat-label">长期记忆</p>
            <p class="stat-value" :class="strategy?.enabled ? 'text-primary' : 'text-muted'">
              {{ strategy?.enabled ? '已开启' : '未开启' }}
            </p>
          </div>
        </div>
        <div class="hover-card stat-item" v-loading="loading">
          <div class="stat-icon purple">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="stat-body">
            <p class="stat-label">记忆条目</p>
            <p class="stat-value">{{ itemTotal }}</p>
          </div>
        </div>
        <div class="hover-card stat-item" v-loading="loading">
          <div class="stat-icon cyan">
            <el-icon><Connection /></el-icon>
          </div>
          <div class="stat-body">
            <p class="stat-label">会话变量</p>
            <p class="stat-value">{{ variableTotal }}</p>
          </div>
        </div>
        <div class="hover-card stat-item" v-loading="loading">
          <div class="stat-icon orange">
            <el-icon><Files /></el-icon>
          </div>
          <div class="stat-body">
            <p class="stat-label">每次注入条数</p>
            <p class="stat-value">{{ strategy?.topN ?? '-' }} <span class="stat-unit">条</span></p>
          </div>
        </div>
      </div>

      <el-card shadow="never" class="content-card">
        <el-tabs v-model="activeTab" class="memory-tabs">
          <!-- ==================== 长期记忆 ==================== -->
          <el-tab-pane label="长期记忆" name="items">
            <div class="tab-toolbar">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="长期记忆让智能体跨会话记住用户偏好、事实与事件。可手动沉淀，也可开启「自动抽取」让对话后自动生成记忆；运行时按重要度挑选条目注入上下文。"
              />
              <div class="toolbar-row">
                <div class="toolbar-filters">
                  <el-select
                    v-model="itemFilters.category"
                    placeholder="全部分类"
                    clearable
                    class="filter-select"
                  >
                    <el-option
                      v-for="c in itemCategories"
                      :key="c.value"
                      :value="c.value"
                      :label="c.label"
                    />
                  </el-select>
                  <el-select
                    v-model="itemFilters.scope"
                    placeholder="全部作用域"
                    clearable
                    class="filter-select"
                  >
                    <el-option value="global" label="全局记忆" />
                    <el-option value="user" label="用户记忆" />
                  </el-select>
                  <el-input
                    v-model="itemFilters.keyword"
                    placeholder="搜索记忆内容"
                    clearable
                    class="filter-search"
                    @keyup.enter="loadItems"
                    @clear="loadItems"
                  >
                    <template #prefix
                      ><el-icon><Search /></el-icon
                    ></template>
                  </el-input>
                  <el-button @click="loadItems"
                    ><el-icon><Refresh /></el-icon
                  ></el-button>
                </div>
                <el-button type="primary" @click="itemDialog?.open()">
                  <el-icon><Plus /></el-icon>&nbsp;新增记忆
                </el-button>
              </div>
            </div>

            <el-table
              v-loading="itemsLoading"
              :data="items"
              class="data-table"
              empty-text="暂无记忆条目"
            >
              <el-table-column label="记忆内容" min-width="360">
                <template #default="{ row }">
                  <div class="item-content" :title="row.content">{{ row.content }}</div>
                </template>
              </el-table-column>
              <el-table-column label="类别" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="catTag(row.category)" effect="light" size="small">
                    {{ catText(row.category) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="重要度" width="110" align="center">
                <template #default="{ row }">
                  <span
                    class="importance"
                    :class="'lv' + row.importance"
                    :title="importanceText(row.importance)"
                  >
                    {{ '★'.repeat(row.importance) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="来源" width="90" align="center">
                <template #default="{ row }">
                  <el-tag
                    :type="row.source === 'auto' ? 'warning' : 'info'"
                    size="small"
                    effect="plain"
                  >
                    {{ row.source === 'auto' ? '自动' : '手动' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="命中" width="70" align="center" prop="hitCount" />
              <el-table-column label="启用" width="70" align="center">
                <template #default="{ row }">
                  <el-switch
                    :model-value="row.status === 1"
                    size="small"
                    :loading="switchingId === row.id"
                    @change="toggleItem(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatTime(row.updateTime, true) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="130" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="itemDialog?.open(row)"
                    >编辑</el-button
                  >
                  <el-button link type="danger" size="small" @click="removeItem(row)"
                    >删除</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- ==================== 会话变量 ==================== -->
          <el-tab-pane label="会话变量" name="variables">
            <div class="tab-toolbar">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="会话变量是键值形式的上下文，可跨会话保存。全局变量作用于该应用的全部对话；会话变量可绑定到某个会话（为空时作用于全部会话）。运行时变量会被带入提示词。"
              />
              <div class="toolbar-row">
                <div class="toolbar-filters">
                  <el-select
                    v-model="variableFilters.scope"
                    placeholder="全部作用域"
                    clearable
                    class="filter-select"
                  >
                    <el-option value="global" label="全局" />
                    <el-option value="session" label="会话" />
                  </el-select>
                  <el-input
                    v-model="variableFilters.keyword"
                    placeholder="搜索变量名 / 值"
                    clearable
                    class="filter-search"
                    @keyup.enter="loadVariables"
                    @clear="loadVariables"
                  >
                    <template #prefix
                      ><el-icon><Search /></el-icon
                    ></template>
                  </el-input>
                  <el-button @click="loadVariables"
                    ><el-icon><Refresh /></el-icon
                  ></el-button>
                </div>
                <el-button type="primary" @click="variableDialog?.open()">
                  <el-icon><Plus /></el-icon>&nbsp;新增变量
                </el-button>
              </div>
            </div>

            <el-table
              v-loading="varLoading"
              :data="variables"
              class="data-table"
              empty-text="暂无会话变量"
            >
              <el-table-column label="变量名" width="200">
                <template #default="{ row }">
                  <code class="var-name">{{ row.name }}</code>
                </template>
              </el-table-column>
              <el-table-column label="作用域" width="120" align="center">
                <template #default="{ row }">
                  <el-tag
                    :type="row.scope === 'global' ? 'success' : 'warning'"
                    size="small"
                    effect="light"
                  >
                    {{ row.scope === 'global' ? '全局' : '会话' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="100" align="center">
                <template #default="{ row }">
                  <span class="type-chip">{{ typeText(row.valueType) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="值" min-width="220">
                <template #default="{ row }">
                  <span class="var-value" :title="row.value">{{ row.value ?? '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="140">
                <template #default="{ row }">
                  <span class="muted-text">{{ row.remark || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatTime(row.updateTime, true) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="130" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="variableDialog?.open(row)"
                    >编辑</el-button
                  >
                  <el-button link type="danger" size="small" @click="removeVariable(row)"
                    >删除</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- ==================== 记忆策略 ==================== -->
          <el-tab-pane label="记忆策略" name="strategy">
            <MemoryStrategyPanel
              v-model="strategy"
              :chat-models="chatModels"
              @saved="loadStrategy"
            />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <MemoryItemDialog ref="itemDialog" :app-id="selectedAppId" @saved="onItemSaved" />
    <MemoryVariableDialog ref="variableDialog" :app-id="selectedAppId" @saved="onVariableSaved" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { appAgentApi } from '@/api/app-agent'
import { modelApi } from '@/api/model'
import { memoryApi } from '@/api/memory'
import type { AppAgent, ChatModelInfo, MemItem, MemStrategy, MemVariable } from '@/api/types'
import { formatTime } from '@/utils/format'
import { itemCategories, catText, catTag, typeText, importanceText } from './memoryMeta'
import MemoryItemDialog from './components/MemoryItemDialog.vue'
import MemoryVariableDialog from './components/MemoryVariableDialog.vue'
import MemoryStrategyPanel from './components/MemoryStrategyPanel.vue'

const router = useRouter()

// ---------- 应用与基础数据 ----------
const apps = ref<AppAgent[]>([])
const selectedAppId = ref<number | null>(null)
const activeTab = ref('items')
const chatModels = ref<ChatModelInfo[]>([])

// ---------- 数据 ----------
const loading = ref(false)
const strategy = ref<MemStrategy | null>(null)
const items = ref<MemItem[]>([])
const variables = ref<MemVariable[]>([])
const itemTotal = ref(0)
const variableTotal = ref(0)

const itemFilters = reactive({ category: '', scope: '', keyword: '' })
const variableFilters = reactive({ scope: '', keyword: '' })
const itemsLoading = ref(false)
const varLoading = ref(false)
const switchingId = ref<number | null>(null)

async function loadStrategy() {
  if (selectedAppId.value == null) return
  strategy.value = await memoryApi.strategy(selectedAppId.value)
}

async function loadItems() {
  if (selectedAppId.value == null) return
  itemsLoading.value = true
  try {
    items.value = await memoryApi.items(selectedAppId.value, {
      category: itemFilters.category || undefined,
      scope: itemFilters.scope || undefined,
      keyword: itemFilters.keyword || undefined
    })
  } finally {
    itemsLoading.value = false
  }
}

async function loadVariables() {
  if (selectedAppId.value == null) return
  varLoading.value = true
  try {
    variables.value = await memoryApi.variables(selectedAppId.value, {
      scope: variableFilters.scope || undefined,
      keyword: variableFilters.keyword || undefined
    })
  } finally {
    varLoading.value = false
  }
}

/** 切换应用后全量拉取（计数不受筛选影响） */
async function loadAll() {
  if (selectedAppId.value == null) return
  loading.value = true
  try {
    const [strategyRes, itemsRes, variablesRes] = await Promise.all([
      memoryApi.strategy(selectedAppId.value),
      memoryApi.items(selectedAppId.value),
      memoryApi.variables(selectedAppId.value)
    ])
    strategy.value = strategyRes
    items.value = itemsRes
    variables.value = variablesRes
    itemTotal.value = itemsRes.length
    variableTotal.value = variablesRes.length
  } finally {
    loading.value = false
  }
}

// ---------- 应用切换 ----------
async function loadApps() {
  const res = await appAgentApi.page({ page: 1, size: 100 })
  apps.value = res.records || []
  if (apps.value.length) {
    selectedAppId.value = apps.value[0].id
    await loadAll()
  }
}

async function loadChatModels() {
  try {
    chatModels.value = await modelApi.chatModels()
  } catch {
    chatModels.value = []
  }
}

// ---------- 记忆条目操作 ----------
const itemDialog = ref<InstanceType<typeof MemoryItemDialog> | null>(null)

async function onItemSaved() {
  await loadItems()
  itemTotal.value = items.value.length
}

async function toggleItem(row: MemItem) {
  switchingId.value = row.id ?? null
  try {
    const next = row.status === 1 ? 0 : 1
    await memoryApi.updateItem(row.id as number, { status: next })
    row.status = next
  } finally {
    switchingId.value = null
  }
}

async function removeItem(row: MemItem) {
  await ElMessageBox.confirm('删除后无法恢复，确定删除这条记忆吗？', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await memoryApi.removeItem(row.id as number)
  ElMessage.success('记忆已删除')
  await loadItems()
  itemTotal.value = items.value.length
}

// ---------- 会话变量操作 ----------
const variableDialog = ref<InstanceType<typeof MemoryVariableDialog> | null>(null)

async function onVariableSaved() {
  await loadVariables()
  variableTotal.value = variables.value.length
}

async function removeVariable(row: MemVariable) {
  await ElMessageBox.confirm(`确定删除变量 ${row.name} 吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await memoryApi.removeVariable(row.id as number)
  ElMessage.success('变量已删除')
  await loadVariables()
  variableTotal.value = variables.value.length
}

const goCreateApp = () => router.push('/app/agents')

onMounted(async () => {
  await loadApps()
  await loadChatModels()
})
</script>

<style scoped>
.memory-page {
  min-height: 100%;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.page-head-info .head-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.head-desc {
  margin: 6px 0 0;
  max-width: 720px;
  font-size: 13px;
  color: var(--text-tertiary, #8a8f98);
  line-height: 1.6;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.action-label {
  font-size: 13px;
  color: var(--text-tertiary, #8a8f98);
}
.app-select {
  width: 240px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
  margin: 16px 0;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: var(--fill-light);
  color: var(--text-secondary);
}
.stat-icon.on {
  background: rgba(52, 199, 123, 0.14);
  color: #34c77b;
}
.stat-icon.off {
  background: rgba(245, 158, 11, 0.14);
  color: #f59e0b;
}
.stat-icon.purple {
  background: rgba(124, 93, 250, 0.12);
  color: #7c5dfa;
}
.stat-icon.cyan {
  background: rgba(0, 168, 255, 0.12);
  color: #00a8ff;
}
.stat-icon.orange {
  background: rgba(255, 152, 0, 0.14);
  color: #ff9800;
}
.stat-body .stat-label {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
.stat-value {
  margin: 2px 0 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.text-primary {
  color: #34c77b !important;
}
.text-muted {
  color: #f59e0b !important;
}
.stat-unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary, #8a8f98);
}

.content-card {
  border-radius: 14px;
}
.memory-tabs :deep(.el-tabs__header) {
  margin: 0 0 4px;
}
.memory-tabs :deep(.el-tabs__item) {
  font-size: 14px;
}
.tab-toolbar {
  padding: 4px 0 12px;
}
.tab-toolbar :deep(.el-alert) {
  margin-bottom: 12px;
  border-radius: 10px;
}
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}
.filter-select {
  width: 130px;
}
.filter-search {
  width: 240px;
}

.data-table {
  width: 100%;
}
.data-table :deep(th.el-table__cell) {
  background: var(--fill-light);
  color: var(--text-secondary);
  font-size: 12px;
}
.item-content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.5;
}
.importance {
  font-size: 14px;
  letter-spacing: 1px;
}
.importance.lv1,
.importance.lv2 {
  color: #909399;
}
.importance.lv3 {
  color: #e6a23c;
}
.importance.lv4 {
  color: #f56c6c;
}
.importance.lv5 {
  color: #ff5f5f;
}
.muted-text {
  color: var(--text-tertiary, #8a8f98);
  font-size: 12px;
}
.var-name {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  color: #7c5dfa;
  background: rgba(124, 93, 250, 0.08);
  padding: 2px 6px;
  border-radius: 6px;
}
.var-value {
  font-size: 13px;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.type-chip {
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
  border: 1px solid var(--border-color, #e4e7ed);
  padding: 1px 8px;
  border-radius: 10px;
}
.empty-guide {
  margin-top: 24px;
  padding: 40px 0;
}
</style>
