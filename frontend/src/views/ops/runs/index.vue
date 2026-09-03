<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { pageRuns, type AgentRunRecord } from '@/api/chat-run.ts'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent, TraceItem } from '@/api/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const records = ref<AgentRunRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const statusFilter = ref('')

/** 应用名映射（agent_run 未冗余应用名，前端拉取全量应用构建） */
const appNameMap = ref<Record<number, string>>({})

const appId = computed(() => {
  const raw = route.query.appId
  const id = Number(raw)
  return raw && Number.isFinite(id) ? id : undefined
})
const currentAppName = computed(() => (appId.value ? appNameMap.value[appId.value] : undefined))

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'running', label: '运行中' },
  { value: 'success', label: '成功' },
  { value: 'failed', label: '失败' },
  { value: 'canceled', label: '已停止' },
  { value: 'timeout', label: '超时' }
]

const statusMeta: Record<string, { type: 'success' | 'danger' | 'warning' | 'info'; text: string }> = {
  running: { type: 'warning', text: '运行中' },
  success: { type: 'success', text: '成功' },
  failed: { type: 'danger', text: '失败' },
  canceled: { type: 'info', text: '已停止' },
  timeout: { type: 'info', text: '超时' }
}

const nodeColor: Record<string, 'success' | 'danger' | 'info'> = {
  success: 'success',
  error: 'danger',
  skipped: 'info',
  canceled: 'info'
}

function parseTrace(run: AgentRunRecord): TraceItem[] {
  if (!run.traceJson) return []
  try {
    const arr = JSON.parse(run.traceJson)
    return Array.isArray(arr) ? (arr as TraceItem[]) : []
  } catch {
    return []
  }
}

function traceCount(run: AgentRunRecord) {
  return parseTrace(run).length
}

function failedCount(run: AgentRunRecord) {
  return parseTrace(run).filter((t) => t.status === 'error').length
}

function traceNodeName(t: TraceItem) {
  return t.label || t.nodeType || t.nodeId
}

function fmtTime(value?: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function fmtCost(ms?: number | null) {
  if (ms === null || ms === undefined) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

async function loadApps() {
  try {
    const data = await appAgentApi.page({ page: 1, size: 1000 })
    const map: Record<number, string> = {}
    for (const a of data.records as AppAgent[]) map[a.id] = a.name
    appNameMap.value = map
  } catch {
    /* 应用名映射加载失败不影响记录列表 */
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pageRuns({
      appId: appId.value,
      status: statusFilter.value || undefined,
      page: page.value,
      size: pageSize.value
    })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function clearAppFilter() {
  router.replace({ path: '/ops/runs' })
}

onMounted(() => {
  loadApps()
  load()
})
</script>

<template>
  <div class="page-container runs-page">
    <div class="runs-head">
      <div>
        <h2 class="head-title">运行记录</h2>
        <p class="head-desc">按工作流运行维度回溯每次执行的节点轨迹、耗时与结果</p>
      </div>
      <div class="runs-actions">
        <el-tag v-if="appId" type="primary" closable effect="plain" @close="clearAppFilter">
          仅看应用「{{ currentAppName || `#${appId}` }}」
        </el-tag>
        <el-button :icon="Refresh" circle title="刷新" @click="reload" />
      </div>
    </div>

    <div class="runs-filter">
      <el-select v-model="statusFilter" style="width: 140px" @change="reload">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <span class="runs-total">共 {{ total }} 次运行</span>
    </div>

    <el-table
      v-loading="loading"
      :data="records"
      row-key="runId"
      class="runs-table"
      empty-text="暂无运行记录"
    >
      <el-table-column type="expand" width="36">
        <template #default="{ row }">
          <div class="run-detail">
            <div class="rd-section">
              <span class="rd-label">运行标识</span>
              <code class="rd-run-id">{{ row.runId }}</code>
            </div>
            <div class="rd-section">
              <span class="rd-label">用户输入</span>
              <div class="rd-text">{{ row.input || '（空）' }}</div>
            </div>
            <div v-if="row.error" class="rd-section">
              <span class="rd-label">错误信息</span>
              <div class="rd-text rd-error-text">{{ row.error }}</div>
            </div>
            <div class="rd-section">
              <span class="rd-label">最终回答</span>
              <div class="rd-text">{{ row.answer || '（无回答）' }}</div>
            </div>
            <div class="rd-section">
              <span class="rd-label">节点轨迹（{{ traceCount(row) }}）</span>
              <div v-if="traceCount(row) > 0" class="rd-trace-list">
                <div v-for="(t, i) in parseTrace(row)" :key="i" class="rd-trace-item">
                  <el-tag size="small" :type="nodeColor[t.status] || 'info'" effect="light">
                    {{ t.status }}
                  </el-tag>
                  <span class="rd-node-name" :title="traceNodeName(t)">{{ traceNodeName(t) }}</span>
                  <span class="rd-node-type">{{ t.nodeType }}</span>
                  <span class="rd-cost">{{ fmtCost(t.costMs) }}</span>
                  <span v-if="t.error" class="rd-node-error" :title="t.error">⚠ {{ t.error }}</span>
                </div>
              </div>
              <div v-else class="rd-muted">
                本次运行未产生节点轨迹（{{ row.mode === 'agent' ? '智能体模式' : '可能为直接对话或链路未被记录' }}）
              </div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMeta[row.status]?.type || 'info'" size="small" effect="light">
            {{ statusMeta[row.status]?.text || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="应用" width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="run-app">
            <span>{{ appNameMap[row.appId] || `应用 #${row.appId}` }}</span>
            <el-tag v-if="row.mode" size="small" type="info" effect="plain">{{ row.mode }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="输入" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.input || '（空）' }}</template>
      </el-table-column>
      <el-table-column label="结果摘要" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.error" class="cell-error">{{ row.error }}</span>
          <span v-else>{{ row.answer || '（无回答）' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="节点" width="120" align="center">
        <template #default="{ row }">
          <span :class="{ 'cell-danger': failedCount(row) > 0 }">
            {{ traceCount(row) }}{{ failedCount(row) > 0 ? `（失败 ${failedCount(row)}）` : '' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="right">
        <template #default="{ row }">{{ fmtCost(row.costMs) }}</template>
      </el-table-column>
      <el-table-column label="开始时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
    </el-table>

    <div class="runs-pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        background
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.runs-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.runs-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.runs-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.runs-filter {
  display: flex;
  align-items: center;
  gap: 10px;
}

.runs-total {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-secondary);
}

.runs-table {
  flex: 1;
}

.runs-pager {
  display: flex;
  justify-content: flex-end;
  padding-bottom: 8px;
}

.run-app {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.run-app span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-error {
  color: #f56c6c;
}

.cell-danger {
  color: #f56c6c;
}

/* ---------- 展开详情 ---------- */
.run-detail {
  padding: 4px 12px 8px 48px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--fill-lighter);
  border-radius: 8px;
}

.rd-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rd-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.rd-text {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text-primary);
}

.rd-error-text {
  color: #f56c6c;
}

.rd-run-id {
  font-size: 12px;
  color: var(--text-secondary);
  word-break: break-all;
}

.rd-muted {
  font-size: 12px;
  color: var(--text-tertiary);
}

.rd-trace-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rd-trace-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 4px 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-primary);
}

.rd-node-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.rd-node-type {
  color: var(--text-tertiary);
  flex: none;
}

.rd-cost {
  color: var(--text-secondary);
  flex: none;
  margin-left: auto;
}

.rd-node-error {
  color: #f56c6c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}
</style>
