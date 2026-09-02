<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ChatDotRound,
  Coin,
  Connection,
  Files,
  Lightning,
  PriceTag,
  Refresh,
} from '@element-plus/icons-vue'
import { appAgentApi } from '@/api/app-agent'
import { usageApi } from '@/api/usage-stat'
import type { UsageAppRow, UsageModelRow, UsageTrendPoint } from '@/api/types'

// ---------- 日期与筛选 ----------
const today = () => {
  const d = new Date()
  return fmtDay(d)
}
const daysAgo = (n: number) => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return fmtDay(d)
}
function fmtDay(d: Date) {
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const range = ref<[string, string]>([daysAgo(6), today()])
const appId = ref<number | undefined>()
const shortcuts = [
  { text: '今天', value: () => [today(), today()] },
  { text: '近 7 天', value: () => [daysAgo(6), today()] },
  { text: '近 30 天', value: () => [daysAgo(29), today()] },
  { text: '近 90 天', value: () => [daysAgo(89), today()] },
]

const appOptions = ref<{ id: number; name: string }[]>([])

// ---------- 数据 ----------
const loading = ref(false)
const summary = ref({
  conversations: 0,
  calls: 0,
  inputTokens: 0,
  outputTokens: 0,
  totalTokens: 0,
  cost: 0,
  startDate: '',
  endDate: '',
  trend: [] as UsageTrendPoint[],
  apps: [] as UsageAppRow[],
  models: [] as UsageModelRow[],
})

async function load() {
  loading.value = true
  try {
    const res = await usageApi.summary({
      appId: appId.value,
      startDate: range.value[0],
      endDate: range.value[1],
    })
    summary.value = res
  } finally {
    loading.value = false
  }
}

async function loadApps() {
  const res = await appAgentApi.page({ page: 1, size: 100 })
  appOptions.value = (res.records || []).map((a) => ({ id: a.id, name: a.name }))
}

onMounted(() => {
  loadApps()
  load()
})

// ---------- 汇总指标 ----------
const fmtNum = (v: number) => (v == null ? '0' : Number(v).toLocaleString('zh-CN'))

function fmtBig(v: number) {
  if (!v) return '0'
  if (v >= 1e8) return `${+(v / 1e8).toFixed(2)} 亿`
  if (v >= 1e4) return `${+(v / 1e4).toFixed(2)} 万`
  return fmtNum(v)
}

function fmtMoney(v: number) {
  if (!v) return '0'
  if (v >= 100) return v.toFixed(2)
  if (v >= 1) return v.toFixed(3)
  if (v >= 0.01) return v.toFixed(4)
  return v.toFixed(6)
}

const rangeText = computed(
  () => `${summary.value.startDate || range.value[0]} ~ ${summary.value.endDate || range.value[1]}`,
)

const metrics = computed(() => [
  { label: '会话数', display: fmtNum(summary.value.conversations), note: '仅控制台会话去重', icon: ChatDotRound, color: '#8b5cf6' },
  { label: '模型调用', display: fmtNum(summary.value.calls), note: `${rangeText.value} 期间`, icon: Lightning, color: '#0ea5e9' },
  { label: 'Token 总量', display: fmtBig(summary.value.totalTokens), note: `输入 ${fmtBig(summary.value.inputTokens)} / 输出 ${fmtBig(summary.value.outputTokens)}`, icon: Coin, color: '#f59e0b' },
  { label: '输入 Token', display: fmtBig(summary.value.inputTokens), note: 'prompt_tokens', icon: Connection, color: '#3b82f6' },
  { label: '输出 Token', display: fmtBig(summary.value.outputTokens), note: 'completion_tokens', icon: Files, color: '#f97316' },
  { label: '估算成本', display: `¥ ${fmtMoney(summary.value.cost)}`, note: '按模型官方单价估算', icon: PriceTag, color: '#10b981' },
])

// ---------- 趋势图 ----------
interface ChartPoint {
  date: string
  v: number
  x: number
  y: number
}
interface ChartData {
  points: ChartPoint[]
  line: string
  area: string
  max: number
}

function buildChart(values: UsageTrendPoint[], key: 'totalTokens' | 'calls'): ChartData {
  const list = values.map((t) => ({ date: t.date, v: Number(t[key]) || 0 }))
  const w = 640
  const h = 210
  const pad = { l: 6, r: 6, t: 16, b: 6 }
  const max = Math.max(1, ...list.map((p) => p.v))
  const iw = w - pad.l - pad.r
  const ih = h - pad.t - pad.b
  const n = list.length
  const step = n > 1 ? iw / (n - 1) : 0
  const points = list.map((p, i) => ({
    ...p,
    x: pad.l + (n === 1 ? iw / 2 : i * step),
    y: pad.t + ih - (p.v / max) * ih,
  }))
  const line = points.map((p) => `${p.x},${p.y}`).join(' ')
  const first = points[0]
  const last = points[points.length - 1]
  const area = first ? `M${first.x},${pad.t + ih} L${line} L${last.x},${pad.t + ih} Z` : ''
  return { points, line, area, max }
}

const chartDefs = computed<{ title: string; key: 'totalTokens' | 'calls'; color: string; grad: string; total: string }[]>(() => [
  { title: 'Token 消耗趋势', key: 'totalTokens', color: '#5b6cff', grad: 'gradUsageToken', total: fmtBig(summary.value.totalTokens) },
  { title: '模型调用趋势', key: 'calls', color: '#10b981', grad: 'gradUsageCalls', total: fmtNum(summary.value.calls) },
])

const chartDataOf = (key: 'totalTokens' | 'calls') => buildChart(summary.value.trend, key)

// ---------- 排行 ----------
const maxAppTokens = computed(() => Math.max(1, ...summary.value.apps.map((a) => a.totalTokens)))
const maxModelTokens = computed(() => Math.max(1, ...summary.value.models.map((m) => m.totalTokens)))
const pct = (v: number, max: number) => (max > 0 ? +((v / max) * 100).toFixed(1) : 0)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>用量统计</h2>
        <p class="page-sub">按应用 / 模型统计调用次数与 Token 消耗，成本按模型官方单价估算，掌控运行成本</p>
      </div>
    </div>

    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span class="filter-label">统计区间</span>
          <el-date-picker
            v-model="range"
            type="daterange"
            unlink-panels
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :shortcuts="shortcuts"
            style="width: 300px"
          />
        </div>
        <div class="filter-item">
          <span class="filter-label">应用</span>
          <el-select v-model="appId" placeholder="全部应用" clearable filterable style="width: 200px">
            <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </div>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="metric-grid">
      <div v-for="m in metrics" :key="m.label" class="metric-card">
        <div class="metric-head">
          <span class="metric-ico" :style="{ background: m.color }">
            <el-icon :size="14"><component :is="m.icon" /></el-icon>
          </span>
          <span class="metric-label">{{ m.label }}</span>
        </div>
        <div class="metric-value" :title="m.display">{{ m.display }}</div>
        <div v-if="m.note" class="metric-note">{{ m.note }}</div>
      </div>
    </div>

    <div class="chart-grid">
      <div v-for="c in chartDefs" :key="c.title" class="card">
        <div class="card-head">
          <h3>{{ c.title }}</h3>
          <span class="head-extra">合计 {{ c.total }}</span>
        </div>
        <div class="legend-row">
          <span><i class="dot" :style="{ background: c.color }"></i>{{ c.title }}</span>
          <span class="head-extra">{{ rangeText }}</span>
        </div>
        <template v-if="summary.trend.length">
          <svg :key="c.key" viewBox="0 0 640 210" class="chart-svg" preserveAspectRatio="none">
            <defs>
              <linearGradient :id="c.grad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" :stop-color="c.color" stop-opacity="0.25" />
                <stop offset="100%" :stop-color="c.color" stop-opacity="0.02" />
              </linearGradient>
            </defs>
            <path :d="chartDataOf(c.key).area" :fill="`url(#${c.grad})`" />
            <polyline
              :points="chartDataOf(c.key).line"
              fill="none"
              :stroke="c.color"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <template v-for="(p, i) in chartDataOf(c.key).points" :key="i">
              <title>{{ p.date }}：{{ p.v.toLocaleString('zh-CN') }}</title>
              <rect :x="p.x - 4" :y="0" width="8" height="210" fill="transparent" />
              <circle v-if="chartDataOf(c.key).points.length <= 60" :cx="p.x" :cy="p.y" r="2.5" :fill="c.color" />
            </template>
          </svg>
        </template>
        <el-empty v-else description="该区间暂无数据，先发起一次对话吧" :image-size="72" />
      </div>
    </div>

    <div class="rank-grid">
      <div class="card">
        <div class="card-head">
          <h3>应用 Token 排行</h3>
          <span class="head-extra">{{ summary.apps.length }} 个应用</span>
        </div>
        <el-table :data="summary.apps" size="small" style="width: 100%">
          <el-table-column label="排名" width="56" align="center">
            <template #default="{ $index }">
              <span :class="['rank', $index < 3 ? `rank-${$index + 1}` : '']">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="应用" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.appName }}</template>
          </el-table-column>
          <el-table-column label="会话数" width="80" align="right">
            <template #default="{ row }">{{ row.conversations }}</template>
          </el-table-column>
          <el-table-column label="调用次数" width="90" align="right">
            <template #default="{ row }">{{ row.calls }}</template>
          </el-table-column>
          <el-table-column label="Token 消耗" min-width="150">
            <template #default="{ row }">
              <div class="cell-bar">
                <div class="cell-bar-text">{{ fmtNum(row.totalTokens) }}</div>
                <div class="bar-track">
                  <div class="bar-fill" :style="{ width: pct(row.totalTokens, maxAppTokens) + '%', background: '#5b6cff' }" />
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="成本(元)" width="110" align="right">
            <template #default="{ row }">¥ {{ fmtMoney(row.cost) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!summary.apps.length" description="该区间暂无应用用量" :image-size="72" />
      </div>

      <div class="card">
        <div class="card-head">
          <h3>模型 Token 排行</h3>
          <span class="head-extra">{{ summary.models.length }} 个模型</span>
        </div>
        <el-table :data="summary.models" size="small" style="width: 100%">
          <el-table-column label="排名" width="56" align="center">
            <template #default="{ $index }">
              <span :class="['rank', $index < 3 ? `rank-${$index + 1}` : '']">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.modelName }}</template>
          </el-table-column>
          <el-table-column label="调用次数" width="90" align="right">
            <template #default="{ row }">{{ row.calls }}</template>
          </el-table-column>
          <el-table-column label="输入 Token" width="110" align="right">
            <template #default="{ row }">{{ fmtNum(row.inputTokens) }}</template>
          </el-table-column>
          <el-table-column label="输出 Token" width="110" align="right">
            <template #default="{ row }">{{ fmtNum(row.outputTokens) }}</template>
          </el-table-column>
          <el-table-column label="Token 合计" min-width="120">
            <template #default="{ row }">
              <div class="cell-bar">
                <div class="cell-bar-text">{{ fmtNum(row.totalTokens) }}</div>
                <div class="bar-track">
                  <div class="bar-fill" :style="{ width: pct(row.totalTokens, maxModelTokens) + '%', background: '#10b981' }" />
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="成本(元)" width="110" align="right">
            <template #default="{ row }">¥ {{ fmtMoney(row.cost) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!summary.models.length" description="该区间暂无模型用量" :image-size="72" />
      </div>
    </div>

    <el-alert
      class="usage-tip"
      type="info"
      :closable="false"
      show-icon
      title="统计口径"
      description="每次模型调用（控制台会话 console / 公开 API public，覆盖 direct / agent 模式）成功并返回 usage 时记一条用量事件；会话数仅统计控制台去重会话。成本按模型官方单价（元/百万 Token）估算：deepseek-chat 1/2、deepseek-reasoner 4/16，未知模型按 0 计。"
    />
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
}
.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}
.page-sub {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.filter-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(178px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.metric-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 14px 16px;
}
.metric-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.metric-ico {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.metric-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.metric-value {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.metric-note {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chart-grid,
.rank-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}
.card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 14px 16px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.card-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.head-extra {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.legend-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}
.chart-svg {
  display: block;
  width: 100%;
  height: 210px;
}

.rank {
  display: inline-flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.rank-1 {
  background: #fef3c7;
  color: #b45309;
}
.rank-2 {
  background: #e5e7eb;
  color: #4b5563;
}
.rank-3 {
  background: #ffe0c7;
  color: #c2410c;
}
.cell-bar-text {
  font-variant-numeric: tabular-nums;
}
.bar-track {
  height: 3px;
  margin-top: 4px;
  background: var(--el-fill-color-light);
  border-radius: 2px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.usage-tip {
  border-radius: 8px;
}

@media (max-width: 900px) {
  .chart-grid,
  .rank-grid {
    grid-template-columns: 1fr;
  }
}
</style>
