<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { evalApi, type EvalDataset, type EvalExperiment, type EvalRun } from '@/api/eval'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent, ChatModelInfo } from '@/api/types'
import { modelApi } from '@/api/model'

const loading = ref(false)
const list = ref<EvalExperiment[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const datasetOptions = ref<EvalDataset[]>([])

const STATUS_META: Record<string, string> = { pending: '排队中', running: '运行中', success: '已完成', failed: '失败', stopped: '已停止' }
const pct = (v?: number) => (v == null ? '-' : `${(v * 100).toFixed(1)}%`)
const fmt = (s?: string) => {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function refreshDatasets() {
  try {
    datasetOptions.value = await evalApi.datasetOptions()
  } catch {
    /* ignore */
  }
}
function datasetName(id?: number) {
  if (!id) return '-'
  return datasetOptions.value.find((d) => d.id === id)?.name || `数据集 #${id}`
}
async function load() {
  loading.value = true
  try {
    const data = await evalApi.experimentPage({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() {
  page.value = 1
  load()
}

/* ===== 实验 CRUD ===== */
const formVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const form = reactive({ name: '', description: '', datasetId: undefined as number | undefined })
function openCreate() {
  editId.value = null
  form.name = ''
  form.description = ''
  form.datasetId = undefined
  formVisible.value = true
  refreshDatasets()
}
function openEdit(row: EvalExperiment) {
  editId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.datasetId = row.datasetId
  formVisible.value = true
  refreshDatasets()
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入实验名称')
  if (!form.datasetId) return ElMessage.warning('请选择共用数据集')
  saving.value = true
  try {
    const payload = { name: form.name.trim(), description: form.description.trim() || undefined, datasetId: form.datasetId }
    if (editId.value) {
      await evalApi.updateExperiment(editId.value, payload)
      ElMessage.success('已保存')
    } else {
      await evalApi.createExperiment(payload)
      ElMessage.success('实验已创建，可在详情中添加入组评测')
    }
    formVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}
async function remove(row: EvalExperiment) {
  try {
    await ElMessageBox.confirm(`确认删除实验「${row.name}」？实验下已有评测任务时不可删除。`, '删除确认', { type: 'error' })
    await evalApi.removeExperiment(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* cancel */
  }
}

/* ===== 详情：组内运行 + 添加入组评测 ===== */
const detailVisible = ref(false)
const activeExp = ref<EvalExperiment | null>(null)
const expRuns = ref<EvalRun[]>([])
const runsLoading = ref(false)
async function openDetail(row: EvalExperiment) {
  activeExp.value = row
  detailVisible.value = true
  await loadRuns()
}
async function loadRuns() {
  if (!activeExp.value) return
  runsLoading.value = true
  try {
    const data = await evalApi.runPage({ experimentId: activeExp.value.id, size: 50 })
    expRuns.value = data.records
  } finally {
    runsLoading.value = false
  }
}

const appOptions = ref<AppAgent[]>([])
const modelOptions = ref<ChatModelInfo[]>([])
const addVisible = ref(false)
const adding = ref(false)
const runForm = reactive({ name: '', mode: 'app' as 'app' | 'model', appId: undefined as number | undefined, modelId: undefined as number | undefined })
function openAddRun() {
  runForm.name = ''
  runForm.mode = 'app'
  runForm.appId = undefined
  runForm.modelId = undefined
  addVisible.value = true
  appAgentApi.page({ page: 1, size: 500 }).then((d) => (appOptions.value = d.records.filter((a) => a.status === 1))).catch(() => {})
  modelApi.chatModels().then((d) => (modelOptions.value = d)).catch(() => {})
}
async function addRun() {
  if (!activeExp.value) return
  if (!runForm.name.trim()) return ElMessage.warning('请输入本组评测名称')
  if (runForm.mode === 'app' && !runForm.appId) return ElMessage.warning('请选择被测应用')
  if (runForm.mode === 'model' && !runForm.modelId) return ElMessage.warning('请选择被测模型')
  adding.value = true
  try {
    await evalApi.createRun({
      name: `${activeExp.value.name} · ${runForm.name.trim()}`,
      datasetId: activeExp.value.datasetId,
      appId: runForm.mode === 'app' ? runForm.appId : undefined,
      modelId: runForm.mode === 'model' ? runForm.modelId : undefined,
      experimentId: activeExp.value.id
    })
    ElMessage.success('评测已入组执行')
    addVisible.value = false
    loadRuns()
  } catch (e) {
    ElMessage.error((e as Error).message || '创建失败')
  } finally {
    adding.value = false
  }
}

function targetText(row: EvalRun) {
  if (row.modelId) return `模型 · ${row.modelName || `#${row.modelId}`}`
  return `应用 · ${row.appName || `#${row.appId}`}`
}

onMounted(() => {
  load()
  refreshDatasets()
})
</script>

<template>
  <div class="page-container exp-page">
    <div class="exp-head">
      <div>
        <h2 class="head-title">对比实验</h2>
        <p class="head-desc">同一数据集下按不同应用 / 模型分组评测，横向比较效果并沉淀基线</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="openCreate">
        <el-icon><Plus /></el-icon>&nbsp;新建实验
      </el-button>
    </div>

    <el-card shadow="never" class="exp-card">
      <div class="table-toolbar">
        <el-input v-model="keyword" placeholder="搜索实验名称 / 描述" clearable style="width: 240px" @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <el-table v-loading="loading" :data="list">
        <el-table-column label="实验" min-width="220">
          <template #default="{ row }">
            <div class="cell-main">
              <span class="cell-name">{{ row.name }}</span>
              <span class="dim-text">{{ row.description || '暂无描述' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="共用数据集" min-width="160">
          <template #default="{ row }">{{ datasetName(row.datasetId) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }"><span class="dim-text">{{ fmt(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-links">
              <el-button link type="primary" size="small" @click="openDetail(row)">查看对比</el-button>
              <el-button link size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
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

    <!-- 实验表单 -->
    <el-dialog v-model="formVisible" :title="editId ? '编辑实验' : '新建对比实验'" width="560px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>实验名称 <span class="req">*</span></label>
          <el-input v-model="form.name" placeholder="例如：Prompt 版本对比实验" maxlength="128" />
        </div>
        <div class="field-group">
          <label>共用数据集 <span class="req">*</span></label>
          <el-select v-model="form.datasetId" placeholder="所有组共用同一评测数据集（需已启用且有样本）" style="width: 100%">
            <el-option v-for="d in datasetOptions" :key="d.id" :label="`${d.name}（${d.sampleCount} 样本）`" :value="d.id" />
          </el-select>
        </div>
        <div class="field-group">
          <label>描述</label>
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="说明要验证的假设，例如：新 Prompt 是否显著提升售后退款类问题正确率" maxlength="512" />
        </div>
        <el-alert type="info" :closable="false" description="创建后进入「查看对比」，为每个版本 / 模型添加一组评测，组间结果将并列展示对比。" />
      </div>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 实验对比详情 -->
    <el-drawer v-model="detailVisible" :title="activeExp ? `对比实验 · ${activeExp.name}` : '对比实验'" size="860px">
      <template v-if="activeExp">
        <div class="exp-meta">
          <div class="exp-meta-item">
            <span class="meta-label">共用数据集</span>
            <span>{{ datasetName(activeExp.datasetId) }}</span>
          </div>
          <div class="exp-meta-item">
            <span class="meta-label">组数</span>
            <span>{{ expRuns.length }}</span>
          </div>
          <el-button type="primary" class="btn-gradient" size="small" @click="openAddRun">
            <el-icon><Plus /></el-icon>&nbsp;添加入组评测
          </el-button>
        </div>

        <div class="cmp-head">
          <span class="cmp-col col-winner">基准线</span>
          <span class="cmp-col col-name">被测对象</span>
          <span class="cmp-col col-rate">通过率</span>
          <span class="cmp-col col-score">平均得分</span>
          <span class="cmp-col col-avg">平均耗时</span>
          <span class="cmp-col col-status">状态</span>
          <span class="cmp-col col-time">完成时间</span>
        </div>
        <div v-loading="runsLoading" class="cmp-list">
          <div v-for="row in expRuns" :key="row.id" class="cmp-row">
            <span class="cmp-col col-winner">
              <el-tag v-if="row.passRate != null && row.passRate >= 0.6 && row.status === 'success'" size="small" type="success" effect="plain">优</el-tag>
              <span v-else>-</span>
            </span>
            <span class="cmp-col col-name">{{ targetText(row) }}</span>
            <span class="cmp-col col-rate rate-text" :class="row.passRate != null ? (row.passRate >= 0.6 ? 'good' : row.passRate >= 0.3 ? 'mid' : 'bad') : ''">
              {{ pct(row.passRate) }}
            </span>
            <span class="cmp-col col-score">{{ pct(row.avgScore) }}</span>
            <span class="cmp-col col-avg">{{ row.reportJson ? (JSON.parse(row.reportJson)?.avgLatencyMs ?? '-') : '-' }}ms</span>
            <span class="cmp-col col-status">
              <el-tag size="small" :type="row.status === 'success' ? 'success' : row.status === 'running' || row.status === 'pending' ? 'primary' : row.status === 'failed' ? 'danger' : 'warning'" effect="light">
                {{ STATUS_META[row.status] }}
              </el-tag>
            </span>
            <span class="cmp-col col-time dim-text">{{ fmt(row.finishedAt || row.createTime) }}</span>
          </div>
          <el-empty v-if="!expRuns.length && !runsLoading" description="还没有评测组，点击右上角「添加入组评测」开始对比" :image-size="80" />
        </div>
        <div class="exp-tip">
          <el-text size="small" type="info">评分口径：无参考答案的样本按可执行性计通过；有参考答案按字符重合度打分（≥45% 计通过）。</el-text>
        </div>
      </template>
    </el-drawer>

    <!-- 入组评测 -->
    <el-dialog v-model="addVisible" title="添加一组对比评测" width="540px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>本组名称 <span class="req">*</span></label>
          <el-input v-model="runForm.name" placeholder="例如：v2.3 线上版 / GPT-4o 基线" maxlength="128" />
        </div>
        <div class="field-group">
          <label>被测对象</label>
          <el-radio-group v-model="runForm.mode">
            <el-radio-button value="app">已发布应用</el-radio-button>
            <el-radio-button value="model">直连模型</el-radio-button>
          </el-radio-group>
        </div>
        <div class="field-group">
          <label>被测对象 <span class="req">*</span></label>
          <el-select v-if="runForm.mode === 'app'" v-model="runForm.appId" placeholder="选择已发布应用" filterable style="width: 100%">
            <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id">
              <span>{{ a.name }}</span>
              <span style="float: right; font-size: 12px; color: var(--text-tertiary)">{{ a.type }}</span>
            </el-option>
          </el-select>
          <el-select v-else v-model="runForm.modelId" placeholder="选择对话模型" filterable style="width: 100%">
            <el-option
              v-for="m in modelOptions"
              :key="m.id"
              :label="`${m.providerName} · ${m.modelName}`"
              :value="m.id"
            />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="adding" @click="addRun">运行本组评测</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.exp-page {
  max-width: 1400px;
  margin: 0 auto;
}
.exp-head {
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
.exp-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.table-toolbar {
  margin-bottom: 12px;
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
}
.dim-text {
  color: var(--text-tertiary);
  font-size: 12px;
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
.exp-meta {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 12px 0 14px;
}
.exp-meta-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 13.5px;
}
.meta-label {
  font-size: 12px;
  color: var(--text-tertiary);
}
.cmp-head,
.cmp-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
}
.cmp-head {
  font-size: 12px;
  color: var(--text-tertiary);
  background: var(--fill-light);
}
.cmp-row {
  border-bottom: 1px dashed var(--border-color);
  font-size: 13px;
}
.cmp-col {
  flex-shrink: 0;
}
.col-winner {
  width: 48px;
  text-align: center;
}
.col-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.col-rate {
  width: 76px;
  text-align: center;
}
.col-score {
  width: 76px;
  text-align: center;
}
.col-avg {
  width: 76px;
  text-align: center;
}
.col-status {
  width: 76px;
  text-align: center;
}
.col-time {
  width: 130px;
  text-align: right;
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
.exp-tip {
  margin-top: 14px;
}
</style>
