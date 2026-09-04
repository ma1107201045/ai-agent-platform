<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { billingApi, type BillingSummary, type CostBreakdown, type CostPoint } from '@/api/billing'

const now = new Date()
const month = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const summary = ref<BillingSummary | null>(null)
const trend = ref<CostPoint[]>([])
const byApp = ref<CostBreakdown[]>([])
const byModel = ref<CostBreakdown[]>([])
const loading = ref(false)
const maxCost = ref(1)

const money = (v?: number) => (v == null ? '-' : `¥${Number(v).toFixed(2)}`)
const fmtTokens = (v?: number) => (v == null ? '-' : v.toLocaleString())

async function load() {
  loading.value = true
  try {
    const m = month.value || undefined
    const [s, t, apps, models] = await Promise.all([
      billingApi.summary(m),
      billingApi.trend(m),
      billingApi.byApp(m),
      billingApi.byModel(m)
    ])
    summary.value = s
    trend.value = t
    byApp.value = apps
    byModel.value = models
    maxCost.value = Math.max(1, ...t.map((p) => p.cost), ...apps.map((a) => a.cost), ...models.map((c) => c.cost))
  } finally {
    loading.value = false
  }
}
function onMonthChange(v: string) {
  month.value = v
  load()
}

/* 预算状态 */
const budgetState = () => {
  const pct = summary.value?.budgetUsedPct
  if (pct == null) return { label: '未设置', color: 'var(--text-tertiary)' }
  if (pct >= 100) return { label: `已超支 ${(pct - 100).toFixed(1)}%`, color: '#f56c6c' }
  if (pct >= 80) return { label: `已用 ${pct}%`, color: '#e6a23c' }
  return { label: `已用 ${pct}%`, color: '#67c23a' }
}

const budgetVisible = ref(false)
const saving = ref(false)
const budgetForm = reactive({ month: '', budget: 0, notifyEnabled: 0 })
function openBudget() {
  budgetForm.month = month.value
  budgetForm.budget = summary.value?.budget ?? 0
  budgetForm.notifyEnabled = summary.value?.notifyEnabled ?? 0
  budgetVisible.value = true
}
async function saveBudget() {
  if (!budgetForm.month) return ElMessage.warning('请选择月份')
  if (budgetForm.budget < 0) return ElMessage.warning('预算不能为负')
  saving.value = true
  try {
    await billingApi.setBudget({ month: budgetForm.month, budget: budgetForm.budget, notifyEnabled: budgetForm.notifyEnabled })
    ElMessage.success('预算已保存')
    budgetVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function barHeight(v: number) {
  return Math.max(3, (v / maxCost.value) * 105)
}
function peak() {
  return Math.max(0, ...trend.value.map((t) => t.cost))
}

onMounted(load)
</script>

<template>
  <div class="page-container billing-page" v-loading="loading">
    <div class="bill-head">
      <div>
        <h2 class="head-title">费用账单</h2>
        <p class="head-desc">按月查看模型调用成本，按应用 / 模型拆解消费构成并设置预算提醒</p>
      </div>
      <div class="head-actions">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 150px" @change="(v: string) => onMonthChange(v)" />
        <el-button @click="openBudget">预算设置</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-label">当月费用</span>
        <span class="stat-num money">{{ money(summary?.totalCost) }}</span>
        <span class="dim-text">今日 {{ money(summary?.todayCost) }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">调用次数</span>
        <span class="stat-num">{{ summary?.callCount ?? 0 }}</span>
        <span class="dim-text">当月模型调用</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">Token 用量</span>
        <span class="stat-num">{{ fmtTokens(summary?.totalTokens) }}</span>
        <span class="dim-text">输入 {{ fmtTokens(summary?.promptTokens) }} · 输出 {{ fmtTokens(summary?.completionTokens) }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">预算使用</span>
        <span class="stat-num" :style="{ color: budgetState().color }">
          {{ summary?.budgetUsedPct != null ? summary?.budgetUsedPct + '%' : '未设置' }}
        </span>
        <span class="dim-text">月度预算 {{ money(summary?.budget) }}</span>
      </div>
    </div>

    <el-card shadow="never" class="trend-card">
      <div class="card-head">
        <div>
          <div class="card-title">{{ month }} 每日费用趋势</div>
          <div class="dim-text">峰值 {{ money(peak()) }}</div>
        </div>
        <el-progress
          v-if="(summary?.budget ?? 0) > 0"
          type="dashboard"
          :percentage="Math.min(100, summary?.budgetUsedPct ?? 0)"
          :color="budgetState().color"
          :width="96"
        >
          <template #default>
            <div class="budget-pct">{{ (summary?.budgetUsedPct ?? 0).toFixed(0) }}%</div>
            <div class="dim-text">预算</div>
          </template>
        </el-progress>
      </div>
      <div class="bars">
        <div v-for="(t, i) in trend" :key="t.date" class="bar-col" :title="`${t.date}：${money(t.cost)}`">
          <div class="bar-wrap">
            <div class="bar" :class="t.cost > 0 ? 'hot' : ''" :style="{ height: `${barHeight(t.cost)}px` }"></div>
          </div>
          <span class="bar-date">{{ i % 5 === 0 || i === trend.length - 1 ? t.date : '·' }}</span>
        </div>
      </div>
    </el-card>

    <div class="two-cols">
      <el-card shadow="never" class="col-card">
        <div class="card-title">按应用拆解</div>
        <div v-if="byApp.length" class="break-list">
          <div v-for="row in byApp" :key="'a' + (row.appId ?? 0)" class="break-row">
            <div class="break-name" :title="row.appName">{{ row.appName }}</div>
            <div class="break-bar-wrap"><div class="break-bar" :style="{ width: `${Math.max(2, (row.cost / maxCost) * 100)}%` }"></div></div>
            <div class="break-val">{{ money(row.cost) }}</div>
            <div class="dim-text">{{ row.calls }} 次 · {{ fmtTokens(row.tokens) }} tok</div>
          </div>
        </div>
        <el-empty v-else description="本月暂无应用调用费用" :image-size="70" />
      </el-card>

      <el-card shadow="never" class="col-card">
        <div class="card-title">按模型拆解</div>
        <div v-if="byModel.length" class="break-list">
          <div v-for="row in byModel" :key="'m' + (row.modelId ?? 0)" class="break-row">
            <div class="break-name" :title="row.modelName">{{ row.modelName }}</div>
            <div class="break-bar-wrap"><div class="break-bar" :style="{ width: `${Math.max(2, (row.cost / maxCost) * 100)}%` }"></div></div>
            <div class="break-val">{{ money(row.cost) }}</div>
            <div class="dim-text">{{ row.calls }} 次 · {{ fmtTokens(row.tokens) }} tok</div>
          </div>
        </div>
        <el-empty v-else description="本月暂无模型费用" :image-size="70" />
      </el-card>
    </div>

    <!-- 预算设置 -->
    <el-dialog v-model="budgetVisible" title="月度预算设置" width="480px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>预算月份</label>
          <el-date-picker v-model="budgetForm.month" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 100%" />
        </div>
        <div class="field-group">
          <label>月度预算（元）</label>
          <el-input-number v-model="budgetForm.budget" :min="0" :precision="2" :step="100" style="width: 100%" />
        </div>
        <div class="field-group">
          <el-switch v-model="budgetForm.notifyEnabled" :active-value="1" :inactive-value="0" active-text="超预算提醒" />
          <div class="dim-text" style="margin-top: 4px">启用后当费用超过预算时会在账单页高亮提示。</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="budgetVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="saveBudget">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.billing-page {
  max-width: 1400px;
  margin: 0 auto;
}
.bill-head {
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
.head-actions {
  display: flex;
  gap: 10px;
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
.stat-num.money {
  font-size: 27px;
}
.dim-text {
  font-size: 12px;
  color: var(--text-tertiary);
}
.trend-card {
  border-radius: var(--radius-lg);
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;
}
.card-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 4px;
}
.budget-pct {
  font-size: 18px;
  font-weight: 800;
}
.bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 132px;
  border-bottom: 1px solid var(--border-color);
}
.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  height: 100%;
  justify-content: flex-end;
}
.bar-wrap {
  display: flex;
  align-items: flex-end;
  height: 110px;
  width: 100%;
  justify-content: center;
}
.bar {
  width: 70%;
  min-height: 2px;
  border-radius: 3px 3px 0 0;
  background: var(--fill-strong, #e4e7ed);
  transition: height 0.3s;
}
.bar.hot {
  background: var(--brand-gradient, linear-gradient(135deg, #5b8def, #8a5cf6));
}
.bar-date {
  font-size: 9.5px;
  color: var(--text-tertiary);
  white-space: nowrap;
}
.two-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.col-card {
  border-radius: var(--radius-lg);
}
.break-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.break-row {
  display: grid;
  grid-template-columns: 130px 1fr 90px 110px;
  gap: 10px;
  align-items: center;
  font-size: 12.5px;
}
.break-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.break-bar-wrap {
  height: 10px;
  background: var(--fill-light);
  border-radius: 5px;
  overflow: hidden;
}
.break-bar {
  height: 100%;
  border-radius: 5px;
  background: var(--brand-gradient, linear-gradient(135deg, #5b8def, #8a5cf6));
  min-width: 4px;
  transition: width 0.3s;
}
.break-val {
  font-weight: 700;
  text-align: right;
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
@media (max-width: 1100px) {
  .stat-row,
  .two-cols {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
