<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChatDotRound, Coin, Lightning, PriceTag, Refresh, Setting } from '@element-plus/icons-vue'
import { usageApi } from '@/api/usage-stat'
import type { UsageOverview } from '@/api/types'

/* ---------- 数据加载 ---------- */
const loading = ref(false)
const days = ref(30)
const overview = ref<UsageOverview | null>(null)

async function load() {
  loading.value = true
  try {
    overview.value = await usageApi.overview(days.value)
  } catch {
    /* 接口异常时保留旧数据 */
  } finally {
    loading.value = false
  }
}

/* ---------- 指标卡 ---------- */
function fmtWan(n: number) {
  if (!n) return '0'
  if (n >= 1e8) return (n / 1e8).toFixed(2) + ' 亿'
  if (n >= 1e4) return (n / 1e4).toFixed(1) + ' 万'
  return n.toLocaleString()
}

const unitPrice = ref(Number(localStorage.getItem('usage_unit_price') || 1))
function persistPrice() {
  localStorage.setItem('usage_unit_price', String(unitPrice.value))
}
const cost = computed(() => ((overview.value?.tokens ?? 0) / 1_000_000) * unitPrice.value)
function fmtMoney(n: number) {
  return n >= 1 ? `¥ ${n.toFixed(2)}` : `¥ ${n.toFixed(4)}`
}

const metrics = computed(() => {
  const o = overview.value
  return [
    { key: 'conversations', label: '会话总数', value: fmtWan(o?.conversations ?? 0), icon: ChatDotRound, color: '#8b5cf6', bg: 'rgba(139,92,246,0.12)' },
    { key: 'calls', label: '模型调用次数', value: fmtWan(o?.calls ?? 0), icon: Lightning, color: '#0ea5e9', bg: 'rgba(14,165,233,0.12)' },
    { key: 'tokens', label: 'Token 消耗', value: fmtWan(o?.tokens ?? 0), icon: Coin, color: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
    { key: 'cost', label: '参考成本', value: fmtMoney(cost.value), icon: PriceTag, color: '#10b981', bg: 'rgba(16,185,129,0.12)' }
  ]
})

/* ---------- 趋势图（轻量 SVG，无额外依赖） ---------- */
const VB_W = 680
const VB_H = 210
const PAD_X = 4
const PAD_TOP = 18
const PAD_BOTTOM = 14

interface SeriesPt {
  x: number
  y: number
  tip: string
}
interface ChartDesc {
  key: 'tokens' | 'calls'
  title: string
  color: string
  line: string
  area: string
  pts: SeriesPt[]
  xStart: string
  xEnd: string
  totalText: string
}

function buildSeries(key: 'tokens' | 'calls', color: string, rows: { date: string; tokens: number; calls: number }[]): ChartDesc | null {
  if (!rows.length) return null
  const values = rows.map((r) => (key === 'tokens' ? r.tokens : r.calls))
  const total = values.reduce((s, v) => s + v, 0)
  const max = Math.max(...values, 1)
  const innerH = VB_H - PAD_TOP - PAD_BOTTOM
  const innerW = VB_W - PAD_X * 2
  const n = values.length
  const step = n > 1 ? innerW / (n - 1) : 0
  const base = PAD_TOP + innerH
  const pts: SeriesPt[] = values.map((v, i) => ({
    x: +(PAD_X + i * step).toFixed(1),
    y: +(PAD_TOP + innerH * (1 - v / max)).toFixed(1),
    tip: `${rows[i].date} · ${fmtWan(v)}`
  }))
  const line = pts.map((p) => `${p.x},${p.y}`).join(' ')
  const area = `M${pts[0].x},${base} L${pts.map((p) => `${p.x},${p.y}`).join(' L')} L${pts[n - 1].x},${base} Z`
  return {
    key,
    title: key === 'tokens' ? 'Token 消耗趋势' : '模型调用趋势',
    color,
    line,
    area,
    pts,
    xStart: rows[0].date,
    xEnd: rows[n - 1].date,
    totalText: fmtWan(total)
  }
}

const charts = computed<ChartDesc[]>(() => {
  const daily = overview.value?.daily ?? []
  return [
    buildSeries('tokens', '#5b6cff', daily),
    buildSeries('calls', '#10b981', daily)
  ].filter((c): c is ChartDesc => c !== null)
})

/* ---------- 应用排行 ---------- */
const appRows = computed(() => {
  const apps = overview.value?.apps ?? []
  const maxTokens = Math.max(...apps.map((a) => a.tokens), 0)
  return apps.map((a, i) => ({ ...a, rank: i + 1, share: maxTokens > 0 ? (a.tokens / maxTokens) * 100 : 0 }))
})

onMounted(load)
</script>

<template>
  <div class="page-container usage-page">
    <div class="usage-head">
      <div>
        <h2 class="head-title">用量统计</h2>
        <p class="head-desc">按日与按应用统计模型调用与 Token 消耗，掌控运行成本</p>
      </div>
      <div class="head-actions">
        <el-radio-group v-model="days" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
        <el-button class="btn-gradient" @click="load">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>
    </div>

    <!-- 指标卡 -->
    <div v-loading="loading" class="metric-grid">
      <div v-for="m in metrics" :key="m.key" class="metric-card hover-card" :class="`metric-${m.key}`">
        <div class="metric-icon" :style="{ background: m.bg, color: m.color }">
          <el-icon :size="24"><component :is="m.icon" /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-value">{{ loading ? '—' : m.value }}</div>
          <div class="metric-label">{{ m.label }}</div>
        </div>
        <template v-if="m.key === 'cost'">
          <el-popover trigger="click" placement="top" width="250">
            <template #reference>
              <el-icon class="metric-set" :size="14"><Setting /></el-icon>
            </template>
            <div class="set-title">参考单价（元 / 百万 Token）</div>
            <el-input-number v-model="unitPrice" :min="0.01" :max="100" :step="0.1" :precision="2" size="small" @change="persistPrice" />
            <p class="set-note">按模型单价粗略估算，保存后成本按新单价显示</p>
          </el-popover>
        </template>
      </div>
    </div>

    <!-- 趋势图 -->
    <div class="chart-grid">
      <div v-for="c in charts" :key="c.key" class="chart-card hover-card">
        <div class="chart-head">
          <div>
            <div class="chart-title">{{ c.title }}</div>
            <div class="chart-total" :style="{ color: c.color }">{{ c.totalText }}</div>
          </div>
          <div class="chart-range">
            <span>{{ c.xStart }}</span>
            <span>~</span>
            <span>{{ c.xEnd }}</span>
          </div>
        </div>
        <svg v-if="c.pts.length > 1" :viewBox="`0 0 ${VB_W} ${VB_H}`" class="usage-svg">
          <defs>
            <linearGradient :id="`grad-${c.key}`" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" :stop-color="c.color" stop-opacity="0.25" />
              <stop offset="100%" :stop-color="c.color" stop-opacity="0.02" />
            </linearGradient>
          </defs>
          <path :d="c.area" :fill="`url(#grad-${c.key})`" />
          <polyline :points="c.line" fill="none" :stroke="c.color" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          <circle v-for="(p, idx) in c.pts" :key="idx" :cx="p.x" :cy="p.y" r="2.4" :fill="c.color">
            <title>{{ p.tip }}</title>
          </circle>
        </svg>
        <div v-else class="chart-empty">该时间范围内暂无调用数据</div>
      </div>
    </div>

    <!-- 应用 Token 排行 -->
    <div class="rank-card hover-card">
      <div class="rank-head">
        <div>
          <div class="chart-title">应用 Token 消耗排行</div>
          <div class="rank-sub">按 Token 总量降序，便于定位高消耗应用</div>
        </div>
      </div>
      <el-table v-loading="loading" :data="appRows" class="rank-table">
        <el-table-column label="#" width="64">
          <template #default="{ row }">
            <span class="rank-badge" :class="{ top: row.rank <= 3 }">{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column label="应用" min-width="220">
          <template #default="{ row }">
            <span class="app-name">{{ row.appName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="会话数" width="120" align="right">
          <template #default="{ row }">{{ row.conversations.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="调用次数" width="130" align="right">
          <template #default="{ row }">{{ row.calls.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="Token 消耗" width="140" align="right">
          <template #default="{ row }">
            <b class="token-num">{{ row.tokens.toLocaleString() }}</b>
          </template>
        </el-table-column>
        <el-table-column label="占比" min-width="200">
          <template #default="{ row }">
            <div class="share-cell">
              <div class="share-bar">
                <div class="share-fill" :style="{ width: `${row.share}%` }" />
              </div>
              <span class="share-text">{{ row.share.toFixed(1) }}%</span>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="rank-empty">所选时间范围内暂无调用数据</div>
        </template>
      </el-table>
    </div>

    <el-alert
      type="info"
      :closable="false"
      class="scope-tip"
      title="统计口径：一次模型响应计为一次调用（assistant 消息）；直连模式与 Agent 模式的 Token 已逐条落库。工作流节点内的 Token 汇总将在引擎用量上报后自动并入，当前该部分不参与成本估算。"
    />
  </div>
</template>

<style scoped>
.usage-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.usage-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ---------- 指标卡 ---------- */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 20px;
  min-height: 96px;
}
.metric-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
}
.metric-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.metric-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.metric-label {
  margin-top: 3px;
  font-size: 13px;
  color: var(--text-secondary);
}
.metric-set {
  position: absolute;
  top: 10px;
  right: 12px;
  color: var(--text-tertiary);
  cursor: pointer;
}
.metric-set:hover {
  color: var(--el-color-primary);
}
.set-title {
  font-size: 13px;
  margin-bottom: 8px;
}
.set-note {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.6;
  margin: 8px 0 0;
}

/* ---------- 趋势图 ---------- */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-top: 16px;
}
.chart-card {
  padding: 18px 20px 12px;
}
.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
}
.chart-total {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.chart-range {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
.usage-svg {
  width: 100%;
  height: auto;
  display: block;
  margin-top: 6px;
}
.chart-empty {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

/* ---------- 排行表 ---------- */
.rank-card {
  margin-top: 16px;
  padding: 18px 20px;
}
.rank-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.rank-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.rank-table {
  --el-table-border-color: transparent;
  width: 100%;
}
.rank-badge {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-fill, #f0f2f5);
}
.rank-badge.top {
  color: #fff;
  background: linear-gradient(135deg, #ff9d5c, #ff7a5c);
}
.app-name {
  font-weight: 500;
  color: var(--text-primary);
}
.token-num {
  font-variant-numeric: tabular-nums;
}
.share-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.share-bar {
  flex: 1;
  height: 6px;
  border-radius: 4px;
  background: var(--bg-fill, #f0f2f5);
  overflow: hidden;
}
.share-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #5b6cff, #7c8cff);
  transition: width 0.4s ease;
}
.share-text {
  width: 44px;
  font-size: 12px;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
.rank-empty {
  text-align: center;
  padding: 30px 0;
  color: var(--text-tertiary);
  font-size: 13px;
}
.scope-tip {
  margin-top: 16px;
  border-radius: 10px;
}

@media (max-width: 1000px) {
  .metric-grid,
  .chart-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 640px) {
  .metric-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
