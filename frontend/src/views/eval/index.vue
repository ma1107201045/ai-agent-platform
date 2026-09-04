<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { evalApi, parseReport, type EvalDataset, type EvalRun, type EvalRunCase, type EvalStats } from '@/api/eval'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent, ChatModelInfo } from '@/api/types'
import { modelApi } from '@/api/model'

const loading = ref(false)
const runs = ref<EvalRun[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const filterStatus = ref<string | undefined>()
const filterDataset = ref<number | undefined>()
const stats = ref<EvalStats | null>(null)

const datasetOptions = ref<EvalDataset[]>([])
const appOptions = ref<AppAgent[]>([])
const modelOptions = ref<ChatModelInfo[]>([])
let timer: ReturnType<typeof setInterval> | null = null

const STATUS_META: Record<string, { label: string; type: 'success' | 'danger' | 'warning' | 'info' | 'primary' }> = {
  pending: { label: '排队中', type: 'info' },
  running: { label: '运行中', type: 'primary' },
  success: { label: '已完成', type: 'success' },
  failed: { label: '失败', type: 'danger' },
  stopped: { label: '已停止', type: 'warning' }
}

const pct = (v?: number) => (v == null ? '-' : `${(v * 100).toFixed(1)}%`)
const fmt = (s?: string) => {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadStats() {
  try {
    stats.value = await evalApi.runStats()
  } catch {
    /* ignore */
  }
}
async function load() {
  loading.value = true
  try {
    const data = await evalApi.runPage({
      page: page.value,
      size: size.value,
      status: filterStatus.value,
      datasetId: filterDataset.value,
      keyword: keyword.value || undefined
    })
    runs.value = data.records
    total.value = data.total
    await loadStats()
    syncTimer()
  } finally {
    loading.value = false
  }
}
function search() {
  page.value = 1
  load()
}

/** 存在运行中任务时轮询刷新进度 */
function syncTimer() {
  const hasRunning = (stats.value?.running ?? 0) > 0
  if (hasRunning && !timer) {
    timer = setInterval(() => {
      loadStats()
      load()
    }, 2500)
  } else if (!hasRunning && timer) {
    clearInterval(timer)
    timer = null
  }
}
onMounted(() => {
  load()
  refreshOptions()
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

async function refreshOptions() {
  try {
    datasetOptions.value = await evalApi.datasetOptions()
    appOptions.value = (await appAgentApi.page({ page: 1, size: 500 })).records.filter((a) => a.status === 1)
    modelOptions.value = await modelApi.chatModels()
  } catch {
    /* ignore */
  }
}

const isActive = (row: EvalRun) => row.status === 'running' || row.status === 'pending'
function targetText(row: EvalRun) {
  if (row.modelId) return `模型 · ${row.modelName || `#${row.modelId}`}`
  return `应用 · ${row.appName || `#${row.appId}`}${row.appType ? `（${row.appType}）` : ''}`
}
function datasetName(id?: number) {
  if (!id) return '-'
  return datasetOptions.value.find((d) => d.id === id)?.name || `数据集 #${id}`
}

async function stopRun(row: EvalRun) {
  try {
    await ElMessageBox.confirm(`确认停止任务「${row.name || row.id}」？未完成的样本将被跳过。`, '停止确认', { type: 'warning' })
    await evalApi.stop(row.id)
    ElMessage.success('已发送停止指令')
    load()
  } catch {
    /* cancel */
  }
}
async function rerun(row: EvalRun) {
  try {
    await ElMessageBox.confirm(`基于同一数据集与被测对象重新运行「${row.name || row.id}」？`, '重跑确认')
    await evalApi.rerun(row.id)
    ElMessage.success('任务已重新启动')
    load()
  } catch {
    /* cancel */
  }
}
async function removeRun(row: EvalRun) {
  try {
    await ElMessageBox.confirm(`确认删除任务「${row.name || row.id}」？用例明细将一并删除。`, '删除确认', { type: 'error' })
    await evalApi.removeRun(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* cancel */
  }
}

/* ============ 创建评测任务 ============ */
const createVisible = ref(false)
const creating = ref(false)
const form = reactive({
  name: '',
  datasetId: undefined as number | undefined,
  mode: 'app' as 'app' | 'model',
  appId: undefined as number | undefined,
  modelId: undefined as number | undefined
})
function openCreate() {
  form.name = ''
  form.datasetId = undefined
  form.mode = 'app'
  form.appId = undefined
  form.modelId = undefined
  createVisible.value = true
  refreshOptions()
}
async function createRun() {
  if (!form.name.trim()) return ElMessage.warning('请输入任务名称')
  if (!form.datasetId) return ElMessage.warning('请选择评测数据集')
  if (form.mode === 'app' && !form.appId) return ElMessage.warning('请选择被测应用')
  if (form.mode === 'model' && !form.modelId) return ElMessage.warning('请选择被测模型')
  creating.value = true
  try {
    await evalApi.createRun({
      name: form.name.trim(),
      datasetId: form.datasetId,
      appId: form.mode === 'app' ? form.appId : undefined,
      modelId: form.mode === 'model' ? form.modelId : undefined
    })
    ElMessage.success('评测任务已启动')
    createVisible.value = false
    page.value = 1
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '创建失败')
  } finally {
    creating.value = false
  }
}

/* ============ 任务详情 ============ */
const detailVisible = ref(false)
const activeRun = ref<EvalRun | null>(null)
const cases = ref<EvalRunCase[]>([])
const caseTotal = ref(0)
const casePage = ref(1)
const caseLoading = ref(false)
const caseFilter = ref<number | undefined>()

async function openDetail(row: EvalRun) {
  activeRun.value = row
  detailVisible.value = true
  casePage.value = 1
  caseFilter.value = undefined
  await loadCases()
}
async function loadCases() {
  if (!activeRun.value) return
  caseLoading.value = true
  try {
    const data = await evalApi.cases(activeRun.value.id, { page: casePage.value, size: 10, passed: caseFilter.value })
    cases.value = data.records
    caseTotal.value = data.total
  } finally {
    caseLoading.value = false
  }
}
</script>

<template>
  <div class="page-container eval-page">
    <div class="eval-head">
      <div>
        <h2 class="head-title">评测中心</h2>
        <p class="head-desc">基于评测数据集批量运行应用 / 模型，量化正确率、得分与耗时报告</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="openCreate">
        <el-icon><VideoPlay /></el-icon>&nbsp;发起评测
      </el-button>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-num">{{ stats?.total ?? 0 }}</span>
        <span class="stat-label">累计评测任务</span>
      </div>
      <div class="stat-card accent">
        <span class="stat-num">{{ stats?.running ?? 0 }}</span>
        <span class="stat-label">运行中 / 排队</span>
      </div>
      <div class="stat-card danger">
        <span class="stat-num">{{ stats?.failed ?? 0 }}</span>
        <span class="stat-label">失败任务</span>
      </div>
    </div>

    <el-card shadow="never" class="run-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input v-model="keyword" placeholder="搜索任务名称" clearable style="width: 220px" @keyup.enter="search" @clear="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 130px" @change="search">
            <el-option v-for="(m, k) in STATUS_META" :key="k" :label="m.label" :value="k" />
          </el-select>
          <el-select v-model="filterDataset" placeholder="数据集" clearable filterable style="width: 200px" @change="search">
            <el-option v-for="d in datasetOptions" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </div>
      </div>

      <el-table v-loading="loading" :data="runs">
        <el-table-column label="任务" min-width="210">
          <template #default="{ row }">
            <div class="cell-main">
              <span class="cell-name">
                {{ row.name || `评测 #${row.id}` }}
                <el-tag v-if="isActive(row)" type="primary" effect="light" size="small" class="live-tag">
                  <span class="pulse-dot"></span>执行中
                </el-tag>
              </span>
              <span class="dim-text">{{ datasetName(row.datasetId) }} · {{ targetText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结果计数" width="130">
          <template #default="{ row }">
            <span class="count-text">
              <span class="ok">{{ row.successCount }}</span> 通过
              <span class="bad">/ {{ row.failedCount }}</span> 未过
            </span>
            <div class="dim-text">已处理 {{ row.totalCount }}</div>
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="95">
          <template #default="{ row }">
            <span
              class="rate-text"
              :class="row.passRate != null ? (row.passRate >= 0.6 ? 'good' : row.passRate >= 0.3 ? 'mid' : 'bad') : ''"
            >
              {{ pct(row.passRate) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="平均得分" width="90">
          <template #default="{ row }"><span>{{ pct(row.avgScore) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_META[row.status]?.type" effect="light">{{ STATUS_META[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="150">
          <template #default="{ row }"><span class="dim-text">{{ fmt(row.finishedAt || row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-links">
              <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
              <el-button v-if="isActive(row)" link type="warning" size="small" @click="stopRun(row)">停止</el-button>
              <el-button v-else link size="small" @click="rerun(row)">重跑</el-button>
              <el-button link type="danger" size="small" @click="removeRun(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 16px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="search"
      />
    </el-card>

    <!-- 发起评测 -->
    <el-dialog v-model="createVisible" title="发起评测任务" width="600px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>任务名称 <span class="req">*</span></label>
          <el-input v-model="form.name" placeholder="例如：客服应用回归评测" maxlength="128" />
        </div>
        <div class="field-group">
          <label>评测数据集 <span class="req">*</span></label>
          <el-select v-model="form.datasetId" placeholder="选择数据集（需已启用且有样本）" style="width: 100%">
            <el-option v-for="d in datasetOptions" :key="d.id" :label="`${d.name}（${d.sampleCount} 样本）`" :value="d.id" />
          </el-select>
        </div>
        <div class="field-group">
          <label>被测对象</label>
          <el-radio-group v-model="form.mode">
            <el-radio-button value="app">已发布应用</el-radio-button>
            <el-radio-button value="model">直连对话模型</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="form.mode === 'app'" class="field-group">
          <label>被测应用 <span class="req">*</span></label>
          <el-select v-model="form.appId" placeholder="选择已发布的应用" filterable style="width: 100%">
            <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id">
              <span>{{ a.name }}</span>
              <span style="float: right; font-size: 12px; color: var(--text-tertiary)">{{ a.type }}</span>
            </el-option>
          </el-select>
        </div>
        <div v-else class="field-group">
          <label>被测模型 <span class="req">*</span></label>
          <el-select v-model="form.modelId" placeholder="选择启用的对话模型" filterable style="width: 100%">
            <el-option
              v-for="m in modelOptions"
              :key="m.id"
              :label="`${m.providerName} · ${m.modelName}`"
              :value="m.id"
            />
          </el-select>
        </div>
        <el-alert
          type="info"
          :closable="false"
          title="评测方式说明"
          description="无参考答案的样本按「可执行性」计通过；有参考答案的样本按字符重合度自动打分（≥45% 计通过）。"
        />
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="creating" @click="createRun">开始评测</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情 -->
    <el-drawer v-model="detailVisible" :title="activeRun ? `评测详情 · ${activeRun.name || '#' + activeRun.id}` : '评测详情'" size="780px">
      <template v-if="activeRun">
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">数据集</span>
            <span>{{ datasetName(activeRun.datasetId) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">被测对象</span>
            <span>{{ targetText(activeRun) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">状态</span>
            <el-tag size="small" :type="STATUS_META[activeRun.status]?.type" effect="light">
              {{ STATUS_META[activeRun.status]?.label }}
            </el-tag>
          </div>
          <div class="detail-item">
            <span class="detail-label">结果</span>
            <span>{{ activeRun.successCount }} 通过 / {{ activeRun.failedCount }} 未通过 · 共 {{ activeRun.totalCount }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">通过率</span>
            <span class="strong">{{ pct(activeRun.passRate) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">平均得分</span>
            <span class="strong">{{ pct(activeRun.avgScore) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">平均耗时</span>
            <span>{{ parseReport(activeRun)?.avgLatencyMs ?? '-' }} ms</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">执行区间</span>
            <span class="dim-text">{{ fmt(activeRun.startedAt) }} ~ {{ fmt(activeRun.finishedAt) }}</span>
          </div>
        </div>
        <el-alert v-if="activeRun.error" type="error" :closable="false" :title="activeRun.error" style="margin-bottom: 12px" />
        <div v-if="activeRun.status === 'success' && parseReport(activeRun)?.categories">
          <div class="sub-title">分类表现</div>
          <div class="cat-row">
            <div v-for="(v, k) in parseReport(activeRun)?.categories" :key="k" class="cat-card">
              <span class="cat-name">{{ k }}</span>
              <span class="cat-rate" :class="(v.passRate ?? 0) >= 0.6 ? 'good' : 'bad'">{{ pct(v.passRate) }}</span>
              <span class="dim-text">{{ v.passed }} / {{ v.total }}</span>
            </div>
          </div>
        </div>

        <div class="sub-title">用例明细</div>
        <el-radio-group v-model="caseFilter" size="small" style="margin-bottom: 10px" @change="casePage = 1; loadCases()">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="1">通过</el-radio-button>
          <el-radio-button :value="0">未通过</el-radio-button>
        </el-radio-group>
        <el-table v-loading="caseLoading" :data="cases" size="small">
          <el-table-column label="提问" prop="question" min-width="170" show-overflow-tooltip />
          <el-table-column label="参考答案" min-width="140" show-overflow-tooltip>
            <template #default="{ row }"><span class="dim-text">{{ row.reference || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="模型回答" min-width="140" show-overflow-tooltip>
            <template #default="{ row }"><span class="dim-text">{{ row.answer || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="结果" width="76">
            <template #default="{ row }">
              <el-tag size="small" :type="row.passed === 1 ? 'success' : 'danger'" effect="light">
                {{ row.passed === 1 ? '通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="得分" width="72">
            <template #default="{ row }">
              <span>{{ row.score == null ? '-' : (row.score * 100).toFixed(0) + '%' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="70">
            <template #default="{ row }"><span class="dim-text">{{ row.latencyMs }}ms</span></template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 12px; justify-content: flex-end"
          small
          layout="total, prev, pager, next"
          :total="caseTotal"
          :page-size="10"
          v-model:current-page="casePage"
          @current-change="loadCases"
        />
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.eval-page {
  max-width: 1400px;
  margin: 0 auto;
}
.eval-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
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
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--fill-lighter);
}
.stat-num {
  font-size: 26px;
  font-weight: 800;
  color: var(--brand-1);
}
.stat-card.accent .stat-num {
  color: #e6a23c;
}
.stat-card.danger .stat-num {
  color: #f56c6c;
}
.stat-label {
  font-size: 12.5px;
  color: var(--text-tertiary);
}
.run-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.toolbar-left {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}
.cell-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 2px 0;
}
.cell-name {
  font-size: 13.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.dim-text {
  color: var(--text-tertiary);
  font-size: 12px;
}
.live-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-1);
  animation: pulse 1.2s infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.25;
  }
}
.count-text {
  font-size: 13px;
}
.count-text .ok {
  color: #67c23a;
  font-weight: 700;
}
.count-text .bad {
  color: #f56c6c;
}
.rate-text {
  font-weight: 700;
}
.rate-text.good {
  color: #67c23a;
}
.rate-text.mid {
  color: #e6a23c;
}
.rate-text.bad {
  color: #f56c6c;
}
.row-links {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
}
.row-links .el-button {
  padding: 0 2px;
}
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-group label {
  font-size: 13px;
  font-weight: 600;
}
.req {
  color: #f56c6c;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 20px;
  padding: 12px 14px;
  margin-bottom: 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--fill-lighter);
}
.detail-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 13px;
}
.detail-label {
  font-size: 12px;
  color: var(--text-tertiary);
}
.strong {
  font-weight: 700;
  color: var(--brand-1);
}
.sub-title {
  font-size: 14px;
  font-weight: 700;
  margin: 16px 0 10px;
}
.cat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;
}
.cat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}
.cat-name {
  font-size: 12px;
  color: var(--text-secondary);
}
.cat-rate {
  font-size: 16px;
  font-weight: 800;
}
.cat-rate.good {
  color: #67c23a;
}
.cat-rate.bad {
  color: #f56c6c;
}
</style>
