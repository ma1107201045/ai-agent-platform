<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { BellFilled, Plus, Refresh, Search, VideoPlay, WarningFilled } from '@element-plus/icons-vue'
import { alertApi, type AlertEvent, type AlertEventStats, type AlertRule } from '@/api/ops-alert'

/* ---------------- 常量 ---------------- */
const metricMap: Record<string, { label: string; unit?: string }> = {
  error_rate: { label: '错误率', unit: '%' },
  failures: { label: '运行失败数' },
  latency: { label: '平均延迟', unit: 'ms' },
  cost: { label: '成本', unit: '元' }
}
const metricOptions = Object.entries(metricMap).map(([value, m]) => ({ value, label: m.label }))
const windowOptions = [
  { value: 5, label: '近 5 分钟' },
  { value: 60, label: '近 1 小时' },
  { value: 1440, label: '近 24 小时' }
]
const channelMap: Record<string, string> = { notification: '站内通知', email: '邮件', webhook: 'Webhook' }

const activeTab = ref('rules')
const rulesLoading = ref(false)
const evLoading = ref(false)

/* ================= 规则 ================= */
const rules = ref<AlertRule[]>([])
const ruleTotal = ref(0)
const rulePage = ref(1)
const ruleSize = ref(10)
const ruleKeyword = ref('')

async function loadRules() {
  rulesLoading.value = true
  try {
    const data = await alertApi.rulePage({
      page: rulePage.value,
      size: ruleSize.value,
      keyword: ruleKeyword.value.trim() || undefined
    })
    rules.value = data.records
    ruleTotal.value = data.total
  } finally {
    rulesLoading.value = false
  }
}

/* ---- 规则编辑 ---- */
const dialogVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const form = reactive({
  name: '',
  metric: 'error_rate',
  operator: '>=',
  threshold: 5,
  windowMinutes: 60,
  level: 'warning',
  channels: ['notification'],
  webhookUrl: '',
  remark: ''
})
function resetForm() {
  editId.value = null
  form.name = ''
  form.metric = 'error_rate'
  form.operator = '>='
  form.threshold = 5
  form.windowMinutes = 60
  form.level = 'warning'
  form.channels = ['notification']
  form.webhookUrl = ''
  form.remark = ''
}
function openCreate() {
  resetForm()
  dialogVisible.value = true
}
function openEdit(row: AlertRule) {
  editId.value = row.id
  form.name = row.name
  form.metric = row.metric
  form.operator = row.operator || '>='
  form.threshold = row.threshold
  form.windowMinutes = row.windowMinutes || 60
  form.level = row.level
  form.channels = (row.channels || 'notification').split(',').filter(Boolean)
  form.webhookUrl = row.webhookUrl || ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}
async function saveRule() {
  if (!form.name.trim()) return ElMessage.warning('请输入规则名称')
  const payload = {
    name: form.name.trim(),
    metric: form.metric,
    operator: '>=',
    threshold: form.threshold,
    windowMinutes: form.windowMinutes,
    level: form.level,
    channels: form.channels.join(','),
    webhookUrl: form.webhookUrl.trim() || null,
    remark: form.remark.trim() || null
  }
  saving.value = true
  try {
    if (editId.value) {
      await alertApi.updateRule(editId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await alertApi.createRule(payload)
      ElMessage.success('规则已创建')
    }
    dialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}
async function toggleRule(row: AlertRule, enabled: boolean) {
  await alertApi.toggleRule(row.id, enabled ? 1 : 0)
  row.enabled = enabled ? 1 : 0
  ElMessage.success(enabled ? '已启用' : '已停用')
}
async function removeRule(row: AlertRule) {
  await ElMessageBox.confirm(`确定删除规则「${row.name}」吗？其关联事件也会一并删除。`, '删除告警规则', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await alertApi.removeRule(row.id)
  ElMessage.success('规则已删除')
  loadRules()
}
async function fireTest(row: AlertRule) {
  await alertApi.fireTest(row.id)
  ElMessage.success(`已为「${row.name}」触发一条测试事件，请到「告警事件」查看`)
  if (activeTab.value === 'events') loadEvents()
  else loadRuleStats()
}

/* ================= 事件 ================= */
const events = ref<AlertEvent[]>([])
const evTotal = ref(0)
const evPage = ref(1)
const evSize = ref(10)
const evStatus = ref('')
const evKeyword = ref('')
const stats = ref<AlertEventStats>({ open: 0, today: 0 })

async function loadEvents() {
  evLoading.value = true
  try {
    const data = await alertApi.events({
      page: evPage.value,
      size: evSize.value,
      status: evStatus.value || undefined,
      keyword: evKeyword.value.trim() || undefined
    })
    events.value = data.records
    evTotal.value = data.total
  } finally {
    evLoading.value = false
  }
}
async function loadRuleStats() {
  stats.value = await alertApi.eventStats()
  if (activeTab.value === 'events') loadEvents()
}
function searchEvents() {
  evPage.value = 1
  loadEvents()
}
async function setEventStatus(row: AlertEvent, status: string) {
  await alertApi.setEventStatus(row.id, status)
  ElMessage.success(status === 'handled' ? '已标记处理' : status === 'ignored' ? '已忽略' : '已重新打开')
  loadEvents()
  loadRuleStats()
}
async function removeEvent(row: AlertEvent) {
  await ElMessageBox.confirm('确定删除该告警事件吗？', '删除事件', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await alertApi.removeEvent(row.id)
  ElMessage.success('事件已删除')
  loadEvents()
  loadRuleStats()
}

/* ---------------- 工具 ---------------- */
function metricText(m?: string) {
  return (metricMap[m || '']?.label) || m || '-'
}
function fmt(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 19)
}
function triggerText(row: AlertRule) {
  const m = metricMap[row.metric]
  const v = Number(row.threshold)
  return `${m?.label || row.metric} ${row.operator} ${v}${m?.unit || ''}`
}

onMounted(() => {
  loadRules()
  loadRuleStats()
})
</script>

<template>
  <div class="page-container alerts-page">
    <div class="alerts-head">
      <div>
        <h2 class="head-title">告警管理</h2>
        <p class="head-desc">配置错误率、失败数等阈值规则，触发后通过通知渠道推送</p>
      </div>
      <div class="stat-pills">
        <div class="pill danger"><el-icon><WarningFilled /></el-icon><b>{{ stats.open }}</b> 未处理</div>
        <div class="pill"><el-icon><BellFilled /></el-icon>今日触发 {{ stats.today }} 次</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="activeTab === 'events' ? (loadEvents(), loadRuleStats()) : loadRules()">
      <!-- ============ 规则 ============ -->
      <el-tab-pane label="告警规则" name="rules">
        <div class="filter-bar hover-card">
          <el-input v-model="ruleKeyword" placeholder="搜索规则名称 / 指标" clearable style="width: 240px"
            @keyup.enter="() => { rulePage = 1; loadRules() }" @clear="() => { rulePage = 1; loadRules() }">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" class="btn-gradient" :icon="Plus" @click="openCreate">新增规则</el-button>
          <el-button :icon="Refresh" @click="loadRules">刷新</el-button>
        </div>

        <el-card shadow="never">
          <el-table v-loading="rulesLoading" :data="rules">
            <el-table-column label="规则名称" min-width="180">
              <template #default="{ row }">
                <div class="rule-name">{{ row.name }}</div>
                <div v-if="row.remark" class="rule-remark">{{ row.remark }}</div>
              </template>
            </el-table-column>
            <el-table-column label="触发条件" width="170">
              <template #default="{ row }">{{ triggerText(row) }}</template>
            </el-table-column>
            <el-table-column label="统计窗口" width="110">
              <template #default="{ row }">
                {{ windowOptions.find((w) => w.value === row.windowMinutes)?.label || `${row.windowMinutes} 分钟` }}
              </template>
            </el-table-column>
            <el-table-column label="级别" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.level === 'critical' ? 'danger' : 'warning'" effect="light">
                  {{ row.level === 'critical' ? '严重' : '警告' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="通知渠道" width="150">
              <template #default="{ row }">
                <div class="channels">
                  <el-tag v-for="c in (row.channels || 'notification').split(',').filter(Boolean)" :key="c" size="small"
                    effect="plain" type="info">{{ channelMap[c] || c }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="最近触发" width="160">
              <template #default="{ row }">{{ fmt(row.lastFireTime) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-switch :model-value="row.enabled === 1" size="small" @change="(v) => toggleRule(row, v as boolean)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" align="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="VideoPlay" @click="fireTest(row)">测试触发</el-button>
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="ruleTotal > ruleSize" style="margin-top: 14px; justify-content: flex-end"
            layout="total, prev, pager, next" :total="ruleTotal" :page-size="ruleSize"
            v-model:current-page="rulePage" @current-change="loadRules" />
        </el-card>
      </el-tab-pane>

      <!-- ============ 事件 ============ -->
      <el-tab-pane label="告警事件" name="events">
        <div class="filter-bar hover-card">
          <el-radio-group v-model="evStatus" @change="searchEvents">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="open">未处理</el-radio-button>
            <el-radio-button value="handled">已处理</el-radio-button>
            <el-radio-button value="ignored">已忽略</el-radio-button>
          </el-radio-group>
          <el-input v-model="evKeyword" placeholder="搜索事件内容 / 规则名" clearable style="width: 240px"
            @keyup.enter="searchEvents" @clear="searchEvents">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button :icon="Refresh" @click="searchEvents">刷新</el-button>
          <span class="events-tip">自动触发将在接入运行监控流后开启，当前可用「测试触发」验证链路</span>
        </div>

        <el-card shadow="never">
          <el-table v-loading="evLoading" :data="events">
            <el-table-column label="触发时间" width="160">
              <template #default="{ row }">{{ fmt(row.triggerTime) }}</template>
            </el-table-column>
            <el-table-column label="事件内容" min-width="280">
              <template #default="{ row }">
                <div class="ev-content">
                  <el-tag size="small" :type="row.level === 'critical' ? 'danger' : 'warning'" effect="light"
                    class="lv-tag">{{ row.level === 'critical' ? '严重' : '警告' }}</el-tag>
                  <span>{{ row.content || row.ruleName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="关联规则" width="150">
              <template #default="{ row }">{{ row.ruleName || '-' }}</template>
            </el-table-column>
            <el-table-column label="指标" width="110">
              <template #default="{ row }">{{ metricText(row.metric) }}</template>
            </el-table-column>
            <el-table-column label="来源" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.source === 'manual'" size="small" effect="plain">手动测试</el-tag>
                <el-tag v-else size="small" effect="plain" type="info">自动触发</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small"
                  :type="row.status === 'open' ? 'danger' : row.status === 'handled' ? 'success' : 'info'"
                  effect="light">
                  {{ row.status === 'open' ? '未处理' : row.status === 'handled' ? '已处理' : '已忽略' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理时间" width="160">
              <template #default="{ row }">{{ fmt(row.handledTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="right">
              <template #default="{ row }">
                <template v-if="row.status === 'open'">
                  <el-button link type="success" @click="setEventStatus(row, 'handled')">处理</el-button>
                  <el-button link type="info" @click="setEventStatus(row, 'ignored')">忽略</el-button>
                </template>
                <el-button v-else link type="primary" @click="setEventStatus(row, 'open')">重新打开</el-button>
                <el-button link type="danger" @click="removeEvent(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="evTotal > evSize" style="margin-top: 14px; justify-content: flex-end"
            layout="total, prev, pager, next" :total="evTotal" :page-size="evSize"
            v-model:current-page="evPage" @current-change="loadEvents" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 规则编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑告警规则' : '新增告警规则'" width="560px"
      :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>规则名称 <span class="req">*</span></label>
          <el-input v-model="form.name" maxlength="60" placeholder="例如：错误率过高预警" />
        </div>
        <div class="form-row">
          <div class="field-group grow">
            <label>监控指标 <span class="req">*</span></label>
            <el-select v-model="form.metric" style="width: 100%">
              <el-option v-for="opt in metricOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </div>
          <div class="field-group w-threshold">
            <label>阈值 <span class="req">*</span></label>
            <el-input-number v-model="form.threshold" :min="0.01" :precision="2" :step="1" controls-position="right"
              style="width: 100%" />
          </div>
        </div>
        <div class="field-group">
          <label>统计窗口</label>
          <el-radio-group v-model="form.windowMinutes">
            <el-radio-button v-for="w in windowOptions" :key="w.value" :value="w.value">{{ w.label }}</el-radio-button>
          </el-radio-group>
        </div>
        <div class="field-group">
          <label>告警级别</label>
          <el-radio-group v-model="form.level">
            <el-radio value="warning">警告（黄色）</el-radio>
            <el-radio value="critical">严重（红色）</el-radio>
          </el-radio-group>
        </div>
        <div class="field-group">
          <label>通知渠道</label>
          <el-checkbox-group v-model="form.channels">
            <el-checkbox value="notification">站内通知</el-checkbox>
            <el-checkbox value="email">邮件</el-checkbox>
            <el-checkbox value="webhook">Webhook</el-checkbox>
          </el-checkbox-group>
        </div>
        <div v-if="form.channels.includes('webhook')" class="field-group">
          <label>Webhook 地址</label>
          <el-input v-model="form.webhookUrl" placeholder="https://..." />
        </div>
        <div class="field-group">
          <label>备注</label>
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" placeholder="可选" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.alerts-page {
  max-width: 1400px;
  margin: 0 auto;
}
.alerts-head {
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
.stat-pills {
  display: flex;
  gap: 10px;
}
.pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 12px;
  background: var(--brand-1);
  color: #fff;
  font-size: 13px;
}
.pill.danger {
  background: #fef2f2;
  color: #dc2626;
}
.pill b {
  font-size: 18px;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 16px;
}
.events-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-left: auto;
}
.rule-name {
  font-weight: 600;
}
.rule-remark {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}
.channels {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.ev-content {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.5;
}
.lv-tag {
  flex-shrink: 0;
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
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-secondary);
}
.field-group .req {
  color: #e11d48;
}
.form-row {
  display: flex;
  gap: 12px;
}
.form-row .grow {
  flex: 1;
}
.form-row .w-threshold {
  width: 180px;
}
</style>
