<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Delete, Edit, Plus, Search, VideoPlay } from '@element-plus/icons-vue'
import { appAgentApi } from '@/api/app-agent'
import { scheduleApi, type AppSchedule, type AppScheduleLog } from '@/api/app-schedule'
import type { AppAgent } from '@/api/types'

const loading = ref(false)
const list = ref<AppSchedule[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const enabledFilter = ref<number | undefined>(undefined)

async function load() {
  loading.value = true
  try {
    const data = await scheduleApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      enabled: enabledFilter.value === -1 ? undefined : enabledFilter.value
    })
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

const weekdayNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
const weekdayOptions = computed(() =>
  weekdayNames.map((name, i) => ({ value: i, label: name })).filter((o) => o.value > 0)
)
function triggerText(row: AppSchedule) {
  if (row.triggerType === 'daily') return `每天 ${row.runTime || '--:--'}`
  if (row.triggerType === 'weekly') {
    return `每周 ${weekdayNames[row.runWeekday || 0] || '--'} ${row.runTime || '--:--'}`
  }
  return `每 ${row.intervalMinutes ?? '--'} 分钟`
}

function fmt(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 16)
}

/* ---------- 启用开关 ---------- */
async function toggle(row: AppSchedule) {
  try {
    await scheduleApi.setEnabled(row.id, row.enabled !== 1)
    ElMessage.success(row.enabled !== 1 ? '任务已启用，将按计划自动执行' : '任务已停用')
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败')
    load()
  }
}

/* ---------- 立即执行 ---------- */
const runningId = ref<number | null>(null)
function runNow(row: AppSchedule) {
  ElMessageBox.confirm(
    `将立即执行「${row.name}」，真实调用应用「${row.appName || ''}」，可能耗时数秒。`,
    '立即执行',
    { confirmButtonText: '开始执行', type: 'info' }
  )
    .then(async () => {
      runningId.value = row.id
      try {
        const result = await scheduleApi.run(row.id)
        if (result.status === 'success') {
          ElNotification({
            title: '执行成功',
            message: `${result.message || '执行完成'}${result.costMs ? `（耗时 ${result.costMs}ms）` : ''}`,
            type: 'success',
            duration: 5000
          })
        } else {
          ElNotification({
            title: '执行失败',
            message: result.message || '执行出错，请查看执行记录',
            type: 'error',
            duration: 6000
          })
        }
        load()
      } finally {
        runningId.value = null
      }
    })
    .catch(() => {})
}

/* ---------- 新建 / 编辑 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const appOptions = ref<AppAgent[]>([])
const form = reactive({
  name: '',
  appId: undefined as number | undefined,
  triggerType: 'interval',
  intervalMinutes: 30,
  runTime: '09:00',
  runWeekday: 1,
  inputMessage: '',
  remark: ''
})

async function loadAppOptions() {
  try {
    const data = await appAgentApi.page({ page: 1, size: 200 })
    appOptions.value = data.records
  } catch {
    appOptions.value = []
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.appId = undefined
  form.triggerType = 'interval'
  form.intervalMinutes = 30
  form.runTime = '09:00'
  form.runWeekday = 1
  form.inputMessage = ''
  form.remark = ''
  dialogVisible.value = true
}
function openEdit(row: AppSchedule) {
  editingId.value = row.id
  form.name = row.name
  form.appId = row.appId
  form.triggerType = row.triggerType
  form.intervalMinutes = row.intervalMinutes || 30
  form.runTime = row.runTime || '09:00'
  form.runWeekday = row.runWeekday || 1
  form.inputMessage = row.inputMessage || ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入任务名称')
  if (!form.appId) return ElMessage.warning('请选择要执行的应用')
  if (form.triggerType === 'interval' && (!form.intervalMinutes || form.intervalMinutes < 1)) {
    return ElMessage.warning('请设置正确的触发间隔')
  }
  if ((form.triggerType === 'daily' || form.triggerType === 'weekly') && !form.runTime) {
    return ElMessage.warning('请设置执行时刻')
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      appId: form.appId,
      triggerType: form.triggerType,
      intervalMinutes: form.triggerType === 'interval' ? form.intervalMinutes : undefined,
      runTime: form.triggerType === 'interval' ? undefined : form.runTime,
      runWeekday: form.triggerType === 'weekly' ? form.runWeekday : undefined,
      inputMessage: form.inputMessage || undefined,
      remark: form.remark.trim() || undefined,
      enabled: 1
    }
    if (editingId.value == null) {
      await scheduleApi.create(payload)
      ElMessage.success('任务已创建，保存后请确认应用已发布')
    } else {
      await scheduleApi.update(editingId.value, payload)
      ElMessage.success('任务已更新')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function remove(row: AppSchedule) {
  ElMessageBox.confirm(`确认删除任务「${row.name}」？其执行记录将一并删除。`, '删除确认', { type: 'error' })
    .then(async () => {
      await scheduleApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

/* ---------- 执行记录 ---------- */
const logDrawer = ref(false)
const logRows = ref<AppScheduleLog[]>([])
const logSchedule = ref<AppSchedule | null>(null)
const logLoading = ref(false)
async function openLogs(row: AppSchedule) {
  logSchedule.value = row
  logDrawer.value = true
  await fetchLogs(row.id, 1)
}
async function fetchLogs(id: number, pageNum: number) {
  logLoading.value = true
  try {
    const data = await scheduleApi.logs(id, { page: pageNum, size: 10 })
    logRows.value = data.records
  } finally {
    logLoading.value = false
  }
}

onMounted(() => {
  load()
  loadAppOptions()
})
</script>

<template>
  <div class="page-container sched-page">
    <div class="sched-head">
      <div>
        <h2 class="head-title">定时任务</h2>
        <p class="head-desc">按间隔 / 每天 / 每周自动执行已发布应用，支持手动触发与执行记录</p>
      </div>
      <el-button type="primary" class="btn-gradient" :icon="Plus" @click="openCreate">新建任务</el-button>
    </div>

    <div class="sched-toolbar">
      <el-radio-group v-model="enabledFilter" size="small" @change="search">
        <el-radio-button :value="-1">全部</el-radio-button>
        <el-radio-button :value="1">启用中</el-radio-button>
        <el-radio-button :value="0">已停用</el-radio-button>
      </el-radio-group>
      <div class="toolbar-right">
        <el-input
          v-model="keyword"
          placeholder="搜索任务 / 应用"
          clearable
          style="width: 240px"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="search">搜索</el-button>
      </div>
    </div>

    <el-card shadow="never" class="sched-card">
      <el-table v-loading="loading" :data="list">
        <el-table-column label="任务" min-width="220">
          <template #default="{ row }">
            <div class="task-cell">
              <div class="task-name">{{ row.name }}</div>
              <div v-if="row.remark" class="task-remark">{{ row.remark }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="触发规则" width="170">
          <template #default="{ row }">
            <el-tag size="small" effect="light" type="info">{{ triggerText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行应用" min-width="160">
          <template #default="{ row }">{{ row.appName || `#${row.appId}` }}</template>
        </el-table-column>
        <el-table-column label="最近执行" width="150">
          <template #default="{ row }">{{ fmt(row.lastRunTime) }}</template>
        </el-table-column>
        <el-table-column label="下次执行" width="150">
          <template #default="{ row }">
            <span v-if="row.enabled !== 1" class="dim-text">已停用</span>
            <span v-else>{{ fmt(row.nextRunTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="toggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="VideoPlay" :loading="runningId === row.id" @click="runNow(row)">
              立即执行
            </el-button>
            <el-button link @click="openLogs(row)">记录</el-button>
            <el-button link :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > size"
        style="margin-top: 16px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="load"
      />
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建定时任务' : '编辑定时任务'" width="600px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>任务名称</label>
          <el-input v-model="form.name" maxlength="128" placeholder="例如：每日运营数据报告" />
        </div>
        <div class="field-group">
          <label>执行应用</label>
          <el-select v-model="form.appId" filterable style="width: 100%" placeholder="选择已发布或待发布的应用">
            <el-option v-for="app in appOptions" :key="app.id" :label="`${app.icon || ''} ${app.name}`" :value="app.id" />
          </el-select>
        </div>
        <div class="field-group">
          <label>触发方式</label>
          <el-radio-group v-model="form.triggerType">
            <el-radio-button value="interval">间隔执行</el-radio-button>
            <el-radio-button value="daily">每天执行</el-radio-button>
            <el-radio-button value="weekly">每周执行</el-radio-button>
          </el-radio-group>
        </div>
        <template v-if="form.triggerType === 'interval'">
          <div class="field-group inline">
            <label>触发间隔</label>
            <div class="inline-control">
              <el-input-number v-model="form.intervalMinutes" :min="1" :max="1440" />
              <span class="unit">分钟</span>
            </div>
          </div>
        </template>
        <template v-else-if="form.triggerType === 'daily'">
          <div class="field-group inline">
            <label>执行时刻</label>
            <el-time-picker v-model="form.runTime" format="HH:mm" value-format="HH:mm" style="width: 180px" />
          </div>
        </template>
        <template v-else>
          <div class="field-row">
            <div class="field-group">
              <label>执行星期</label>
              <el-select v-model="form.runWeekday" style="width: 100%">
                <el-option v-for="opt in weekdayOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </div>
            <div class="field-group">
              <label>执行时刻</label>
              <el-time-picker v-model="form.runTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </div>
          </div>
        </template>
        <div class="field-group">
          <label>触发输入（可选）</label>
          <el-input
            v-model="form.inputMessage"
            type="textarea"
            :rows="2"
            maxlength="1000"
            placeholder="执行时发送给应用的任务指令，例如：请生成昨天的运营日报"
          />
        </div>
        <div class="field-group">
          <label>备注（可选）</label>
          <el-input v-model="form.remark" maxlength="255" placeholder="用途说明" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行记录 -->
    <el-drawer v-model="logDrawer" :title="`执行记录 · ${logSchedule?.name || ''}`" size="46%">
      <div v-loading="logLoading">
        <el-table :data="logRows" size="small">
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="触发" width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.triggerBy === 'manual' ? 'warning' : 'info'">
                {{ row.triggerBy === 'manual' ? '手动' : '自动' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'" effect="light">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ row.costMs != null ? `${row.costMs}ms` : '-' }}</template>
          </el-table-column>
          <el-table-column label="摘要 / 错误" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.message || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!logLoading && logRows.length === 0" description="暂无执行记录" :image-size="80" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.sched-page {
  max-width: 1400px;
  margin: 0 auto;
}
.sched-head {
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
.sched-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.toolbar-right {
  display: flex;
  gap: 10px;
}
.sched-card {
  border-radius: var(--radius-lg);
}
.task-cell .task-name {
  font-weight: 600;
}
.task-remark {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.app-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.app-icon {
  font-size: 15px;
}
.dim-text {
  font-size: 12px;
  color: var(--text-tertiary);
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
.field-group.inline {
  flex-direction: row;
  align-items: center;
  gap: 12px;
}
.field-group label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-secondary);
  width: 72px;
  flex-shrink: 0;
}
.inline-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
.unit {
  font-size: 12px;
  color: var(--text-tertiary);
}
.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
</style>
