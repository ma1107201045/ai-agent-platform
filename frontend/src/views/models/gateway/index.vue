<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { gatewayApi } from '@/api/gateway'
import type { GatewayRoute, RouteTarget, SimulateResult } from '@/api/gateway'
import { modelApi } from '@/api/model'
import type { ChatModelInfo } from '@/api/types'

// ---------------- 常量与映射 ----------------

const STRATEGIES: { value: string; label: string; desc: string; tag: 'primary' | 'success' | 'warning' | 'danger' }[] = [
  { value: 'priority', label: '加权优先', desc: '按权重随机挑选一个目标模型', tag: 'primary' },
  { value: 'failover', label: '故障回退', desc: '始终选择优先级最高的目标', tag: 'danger' },
  { value: 'round_robin', label: '轮询调度', desc: '按列表顺序依次轮询使用', tag: 'success' }
]
const STRATEGY_MAP = Object.fromEntries(STRATEGIES.map((s) => [s.value, s]))

const DEFAULT_TARGET_WEIGHT = 1

function strategyInfo(v?: string) {
  return STRATEGY_MAP[v || 'priority'] || STRATEGY_MAP.priority
}
function fmtTime(t?: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}
function fmtCount(n?: number) {
  return n == null ? 0 : n.toLocaleString('zh-CN')
}

// ---------------- 数据加载 ----------------

const loading = ref(false)
const rows = ref<GatewayRoute[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const enabledCount = computed(() => rows.value.filter((r) => r.enabled === 1).length)
const defaultCount = computed(() => rows.value.filter((r) => r.isDefault === 1).length)
const totalCalls = computed(() => rows.value.reduce((acc, r) => acc + (r.callCount || 0), 0))

/** 全部启用的模型候选（供应商名 + 模型名） */
const candidates = ref<ChatModelInfo[]>([])
async function loadCandidates() {
  try {
    candidates.value = await modelApi.allModels()
  } catch {
    candidates.value = []
  }
}
function modelLabel(id?: number | null) {
  if (id == null) return ''
  const hit = candidates.value.find((c) => c.id === id)
  return hit ? `${hit.providerName} · ${hit.modelName}` : `模型 #${id}`
}
function modelShortLabel(id?: number | null) {
  if (id == null) return ''
  const hit = candidates.value.find((c) => c.id === id)
  return hit ? hit.modelName : `#${id}`
}

async function load() {
  loading.value = true
  try {
    const data = await gatewayApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function onSearch() {
  page.value = 1
  load()
}

onMounted(() => {
  load()
  loadCandidates()
})

// ---------------- 解析目标列表 ----------------

function parseTargets(json?: string): RouteTarget[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    if (Array.isArray(arr)) {
      return arr.map((t) => ({
        modelId: Number(t.modelId),
        weight: Number(t.weight ?? 1),
        priority: Number(t.priority ?? 1)
      }))
    }
  } catch {
    /* ignore */
  }
  return []
}
// ---------------- 路由弹窗 ----------------

const dialogVisible = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<{
  name: string
  description: string
  routeType: string
  isDefault: number
  enabled: number
  targets: RouteTarget[]
}>({
  name: '',
  description: '',
  routeType: 'priority',
  isDefault: 0,
  enabled: 1,
  targets: []
})

function resetForm() {
  form.name = ''
  form.description = ''
  form.routeType = 'priority'
  form.isDefault = 0
  form.enabled = 1
  form.targets = []
}

function openCreate() {
  resetForm()
  editId.value = null
  addTarget()
  dialogVisible.value = true
}
function openEdit(row: GatewayRoute) {
  resetForm()
  editId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.routeType = row.routeType
  form.isDefault = row.isDefault
  form.enabled = row.enabled
  const targets = parseTargets(row.targetsJson)
  form.targets = targets.length ? targets : []
  if (!form.targets.length) addTarget()
  dialogVisible.value = true
}

function addTarget() {
  form.targets.push({
    modelId: undefined as unknown as number,
    weight: DEFAULT_TARGET_WEIGHT,
    priority: form.targets.length + 1
  })
}
function removeTarget(i: number) {
  if (form.targets.length <= 1) {
    ElMessage.warning('至少保留一个目标模型')
    return
  }
  form.targets.splice(i, 1)
  // 重排 priority 展示顺序不强制，保持用户自定义
}

function validateTargets(): string | null {
  if (!form.targets.length) return '请至少添加一个目标模型'
  const ids = form.targets.map((t) => t.modelId)
  if (ids.some((id) => id == null)) return '请为每个目标选择模型'
  if (new Set(ids).size !== ids.length) return '目标模型不能重复'
  return null
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入路由名称')
    return
  }
  const err = validateTargets()
  if (err) {
    ElMessage.warning(err)
    return
  }
  saving.value = true
  const payload = {
    route: {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      routeType: form.routeType,
      isDefault: form.isDefault,
      enabled: form.enabled
    } as Partial<GatewayRoute>,
    targets: form.targets.map((t) => ({
      modelId: t.modelId,
      weight: Math.max(1, t.weight || 1),
      priority: t.priority ?? 1
    }))
  }
  try {
    if (editId.value != null) {
      await gatewayApi.update(editId.value, payload)
    } else {
      await gatewayApi.create(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 状态 / 默认开关（乐观更新，失败回滚）
async function onEnabledChange(row: GatewayRoute) {
  try {
    await gatewayApi.update(row.id, {
      route: { enabled: row.enabled } as Partial<GatewayRoute>,
      targets: []
    })
  } catch {
    row.enabled = row.enabled === 1 ? 0 : 1
    ElMessage.error('更新失败')
  }
}
async function onDefaultChange(row: GatewayRoute) {
  try {
    await gatewayApi.update(row.id, {
      route: { isDefault: row.isDefault } as Partial<GatewayRoute>,
      targets: []
    })
    load()
  } catch {
    row.isDefault = row.isDefault === 1 ? 0 : 1
    ElMessage.error('更新失败')
  }
}

function remove(row: GatewayRoute) {
  ElMessageBox.confirm(`删除路由「${row.name}」？`, '删除确认', { type: 'error', confirmButtonText: '删除' })
    .then(async () => {
      await gatewayApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

// ---------------- 路由决策模拟 ----------------

const simVisible = ref(false)
const simLoading = ref(false)
const simResult = ref<SimulateResult | null>(null)
const simError = ref('')

async function simulate(row: GatewayRoute) {
  simLoading.value = true
  simError.value = ''
  simResult.value = null
  simVisible.value = true
  try {
    const res = await gatewayApi.simulate(row.id)
    simResult.value = res as SimulateResult
    load()
  } catch (e) {
    simError.value = e instanceof Error ? e.message : String(e)
  } finally {
    simLoading.value = false
  }
}

function strategyExplain(strategy?: string) {
  const info = strategyInfo(strategy)
  return `${info.label}：${info.desc}`
}
</script>

<template>
  <div class="page-container gateway-page">
    <!-- 头部 -->
    <div class="gateway-head">
      <div>
        <h2 class="head-title">模型网关</h2>
        <p class="head-desc">将多个同能力模型编排为一条路由：加权优先 / 故障回退 / 轮询，统一调度入口</p>
      </div>
      <div class="head-actions">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索路由名称 / 描述"
          clearable
          @keyup.enter="onSearch"
          @clear="onSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-tooltip content="刷新">
          <el-button circle @click="load"><el-icon><Refresh /></el-icon></el-button>
        </el-tooltip>
        <el-button type="primary" class="btn-gradient" @click="openCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>新建路由
        </el-button>
      </div>
    </div>

    <!-- 概览卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-label">路由总数</span>
        <span class="stat-num">{{ total }}</span>
        <span class="dim-text">当前空间配置的路由</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">启用中</span>
        <span class="stat-num">{{ enabledCount }}</span>
        <span class="dim-text">本页数据统计</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">默认路由</span>
        <span class="stat-num">{{ defaultCount }}</span>
        <span class="dim-text">未指定路由时的兜底</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">累计调度</span>
        <span class="stat-num">{{ fmtCount(totalCalls) }}</span>
        <span class="dim-text">路由命中调用次数</span>
      </div>
    </div>

    <!-- 路由列表 -->
    <el-card shadow="never" class="gateway-card">
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="路由" min-width="220">
          <template #default="{ row }">
            <div class="route-name">{{ row.name }}</div>
            <div v-if="row.description" class="route-desc" :title="row.description">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="策略" width="150">
          <template #default="{ row }">
            <el-tag :type="strategyInfo(row.routeType).tag" effect="light" size="small">
              {{ strategyInfo(row.routeType).label }}
            </el-tag>
            <div class="muted">{{ strategyInfo(row.routeType).desc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="目标模型" min-width="300">
          <template #default="{ row }">
            <div class="target-cell">
              <div class="target-tags">
                <el-tag
                  v-for="(t, i) in parseTargets(row.targetsJson)"
                  :key="i"
                  size="small"
                  effect="plain"
                  class="target-tag"
                >
                  {{ modelShortLabel(t.modelId) }}
                </el-tag>
              </div>
              <span class="count-badge">{{ parseTargets(row.targetsJson).length }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.isDefault"
              :active-value="1"
              :inactive-value="0"
              :disabled="row.isDefault === 1"
              @change="onDefaultChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="onEnabledChange(row)" />
          </template>
        </el-table-column>
        <el-table-column label="累计调用" width="110" align="right">
          <template #default="{ row }">
            <span class="mono">{{ fmtCount(row.callCount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="140">
          <template #default="{ row }">
            <span class="muted">{{ fmtTime(row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="success" @click="simulate(row)">模拟</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="load"
      />
    </el-card>

    <!-- 新建 / 编辑路由 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editId ? '编辑路由' : '新建路由'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="路由名称" required>
              <el-input v-model="form.name" placeholder="如 主力对话路由 / DeepSeek 备用" maxlength="50" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="路由策略" required>
              <el-select v-model="form.routeType" style="width: 100%">
                <el-option v-for="s in STRATEGIES" :key="s.value" :label="s.label" :value="s.value">
                  <div class="strategy-option">
                    <span>{{ s.label }}</span>
                    <span class="muted">{{ s.desc }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="路由用途 / 适用场景说明（可选）" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item label="目标模型" required>
          <div class="target-editor">
            <div class="target-editor-head">
              <span class="dim-text">{{ strategyInfo(form.routeType).label }}策略：{{ strategyInfo(form.routeType).desc }}</span>
              <el-button type="primary" plain size="small" @click="addTarget">
                <el-icon style="margin-right: 4px"><Plus /></el-icon>添加目标
              </el-button>
            </div>
            <div v-for="(t, i) in form.targets" :key="i" class="target-row">
              <div class="target-idx">{{ i + 1 }}</div>
              <el-select
                v-model="t.modelId"
                filterable
                placeholder="选择目标模型"
                class="target-model"
                :style="{ width: '100%' }"
              >
                <template #prefix><el-icon><Cpu /></el-icon></template>
                <el-option v-for="c in candidates" :key="c.id" :value="c.id" :label="`${c.providerName} · ${c.modelName}`">
                  <div class="model-option">
                    <span>{{ c.providerName }} · <b>{{ c.modelName }}</b></span>
                    <span v-if="c.contextWindow" class="muted">{{ (c.contextWindow / 1024).toFixed(0) }}K ctx</span>
                  </div>
                </el-option>
              </el-select>
              <div class="target-param" :title="strategyInfo(form.routeType).value === 'failover' ? '数值越小优先级越高' : '加权随机命中权重'">
                <span class="param-label">{{ form.routeType === 'failover' ? '优先级' : '权重' }}</span>
                <el-input-number
                  v-model="t.weight"
                  :min="1"
                  :max="999"
                  size="small"
                  controls-position="right"
                  :disabled="form.routeType === 'failover'"
                />
                <el-input-number
                  v-if="form.routeType === 'failover'"
                  v-model="t.priority"
                  :min="1"
                  :max="99"
                  size="small"
                  controls-position="right"
                />
              </div>
              <el-button link type="danger" :disabled="form.targets.length <= 1" @click="removeTarget(i)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div v-if="form.routeType === 'round_robin'" class="form-tip">轮询策略按上列顺序依次命中，可通过拖拽调整顺序</div>
          </div>
        </el-form-item>

        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          <div class="form-tip" style="margin-left: 8px">设为默认路由后，平台内未显式指定路由的调用将走该路由</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 决策模拟结果 -->
    <el-dialog v-model="simVisible" title="路由决策模拟" width="560px">
      <div v-if="simLoading" class="sim-loading">
        <el-icon class="spin-icon"><Loading /></el-icon>
        <span>正在执行路由决策…</span>
      </div>
      <div v-else-if="simError" class="sim-error">
        <div class="sim-error-title">模拟失败</div>
        <pre class="mono">{{ simError }}</pre>
      </div>
      <template v-else-if="simResult">
        <div class="sim-strategy">
          <el-tag size="small" :type="strategyInfo(simResult.strategy).tag" effect="light">
            {{ strategyInfo(simResult.strategy).label }}
          </el-tag>
          <span class="dim-text">{{ strategyExplain(simResult.strategy) }}</span>
        </div>
        <div class="sim-picked">
          <div class="picked-label">第 {{ simResult.callIndex }} 次调度选中</div>
          <div class="picked-box">
            <div class="picked-name" :title="modelLabel(simResult.selected.modelId)">
              {{ modelLabel(simResult.selected.modelId) }}
            </div>
            <div class="picked-extra">
              <span v-if="simResult.strategy === 'failover'">优先级 {{ simResult.selected.priority }}</span>
              <span v-else>权重 {{ simResult.selected.weight }}</span>
            </div>
          </div>
        </div>
        <div class="sim-tip">
          模拟会真实累加一次「累计调用」计数，便于观察轮询 / 加权分布效果。可点击下方按钮继续模拟。
        </div>
      </template>
      <template #footer>
        <el-button @click="simVisible = false">关闭</el-button>
        <el-button
          v-if="simResult"
          type="primary"
          class="btn-gradient"
          :loading="simLoading"
          @click="simulate({ id: simResult.routeId } as GatewayRoute)"
        >
          再模拟一次
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.gateway-page {
  max-width: 1400px;
  margin: 0 auto;
}
.gateway-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
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
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.search-input {
  width: 240px;
}
.gateway-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
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
.stat-label {
  font-size: 12.5px;
  color: var(--text-tertiary);
}
.stat-num {
  font-size: 24px;
  font-weight: 800;
  color: var(--brand-1);
}
.dim-text,
.muted {
  font-size: 12px;
  color: var(--text-tertiary);
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
.form-tip {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

.route-name {
  font-weight: 600;
}
.route-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.target-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.target-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}
.target-tag {
  border-color: var(--border-color);
  color: var(--text-secondary);
}
.count-badge {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--fill-light);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 11px;
}

/* 目标编辑 */
.target-editor {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px;
  background: var(--fill-lighter);
}
.target-editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.target-row {
  display: grid;
  grid-template-columns: 24px 1fr auto 28px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.target-row:last-child {
  margin-bottom: 0;
}
.target-idx {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
.target-param {
  display: flex;
  align-items: center;
  gap: 6px;
}
.param-label {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}
.strategy-option,
.model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

/* 模拟结果 */
.sim-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 30px 0;
  justify-content: center;
  color: var(--text-tertiary);
}
.spin-icon {
  animation: sim-rotate 1s linear infinite;
}
@keyframes sim-rotate {
  to {
    transform: rotate(360deg);
  }
}
.sim-error {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
}
.sim-error-title {
  font-weight: 600;
  font-size: 12px;
  color: var(--el-color-danger);
}
.sim-error pre {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-color-danger);
}
.sim-strategy {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.sim-picked {
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--fill-lighter);
}
.picked-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}
.picked-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.picked-name {
  font-size: 16px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.picked-extra {
  flex: none;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--fill-light);
  padding: 4px 10px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
}
.sim-tip {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
}
@media (max-width: 1100px) {
  .stat-row {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
