<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { agentTeamApi } from '@/api/multi-agent'
import type { AgentTeam, TeamMember, TeamMemberRow, TeamRun, TeamRunStep } from '@/api/multi-agent'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent } from '@/api/types'

// ---------------- 常量 ----------------
const ROUTINGS: Record<string, { label: string; desc: string; tag: 'primary' | 'warning' | 'success' }> = {
  first_match: { label: '意图匹配', desc: '按关键词把任务分给最合适的成员，未命中走兜底', tag: 'primary' },
  round_robin: { label: '轮询', desc: '按启用顺序轮流分配任务', tag: 'warning' },
  all: { label: '并行汇合', desc: '全部成员同时执行并汇总结果', tag: 'success' }
}
const ROUTING_KEYS = Object.keys(ROUTINGS)
const routingOf = (r?: string) => ROUTINGS[r || 'first_match'] || ROUTINGS.first_match

function fmtTime(t?: string) {
  return t ? t.replace('T', ' ').slice(0, 19) : '-'
}
function parseTrace(run?: TeamRun): TeamRunStep[] {
  if (!run?.traceJson) return []
  try {
    const arr = JSON.parse(run.traceJson) as TeamRunStep[]
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

// ---------------- 团队列表 ----------------
const loading = ref(false)
const rows = ref<AgentTeam[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const data = await agentTeamApi.page({ page: page.value, size: size.value, keyword: keyword.value.trim() || undefined })
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

async function toggleStatus(row: AgentTeam) {
  try {
    await agentTeamApi.update(row.id, { status: row.status })
    ElMessage.success(row.status === 1 ? '已启用' : '已停用')
  } catch (e) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error((e as Error).message || '操作失败')
  }
}

// ---------------- 团队编辑 ----------------
const editVisible = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({ name: '', description: '', routing: 'first_match', status: 1 })
function openCreate() {
  editId.value = null
  Object.assign(form, { name: '', description: '', routing: 'first_match', status: 1 })
  editVisible.value = true
}
function openEdit(row: AgentTeam) {
  editId.value = row.id
  Object.assign(form, {
    name: row.name,
    description: row.description || '',
    routing: row.routing || 'first_match',
    status: row.status
  })
  editVisible.value = true
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入团队名称')
  saving.value = true
  try {
    if (editId.value != null) {
      await agentTeamApi.update(editId.value, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        routing: form.routing,
        status: form.status
      })
    } else {
      await agentTeamApi.create({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        routing: form.routing,
        status: form.status
      })
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function remove(row: AgentTeam) {
  ElMessageBox.confirm(`删除团队「${row.name}」及其成员与运行记录？`, '删除确认', { type: 'error', confirmButtonText: '删除' })
    .then(async () => {
      await agentTeamApi.remove(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

// ---------------- 成员管理 ----------------
const memberVisible = ref(false)
const memberLoading = ref(false)
const team = ref<AgentTeam | null>(null)
const memberRows = ref<TeamMember[]>([])
const appOptions = ref<AppAgent[]>([])

async function loadApps() {
  try {
    const data = await appAgentApi.page({ page: 1, size: 500 })
    appOptions.value = data.records.filter((a) => a.status === 1)
  } catch {
    appOptions.value = []
  }
}

async function openMembers(row: AgentTeam) {
  team.value = row
  memberRows.value = []
  memberVisible.value = true
  memberLoading.value = true
  try {
    const detail = await agentTeamApi.detail(row.id)
    memberRows.value = detail.members.map((m: TeamMemberRow) => ({ ...m.member }))
  } catch (e) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    memberLoading.value = false
  }
}
function addMember() {
  memberRows.value.push({ name: '', appId: 0, keywords: '', priority: memberRows.value.length + 1, enabled: 1 })
}
function removeMember(idx: number) {
  memberRows.value.splice(idx, 1)
}
async function saveMembers() {
  if (!team.value) return
  const payload: TeamMember[] = memberRows.value.map((m, i) => ({
    id: m.id,
    name: m.name.trim(),
    description: m.description?.trim() || undefined,
    appId: Number(m.appId),
    keywords: m.keywords?.trim() || undefined,
    priority: m.priority || i + 1,
    enabled: m.enabled ?? 1
  }))
  if (payload.some((m) => !m.name)) return ElMessage.warning('请填写完整成员名称')
  if (payload.some((m) => !m.appId)) return ElMessage.warning('请为成员选择已发布应用')
  memberLoading.value = true
  try {
    await agentTeamApi.saveMembers(team.value.id, payload)
    ElMessage.success('成员已保存')
    // 同步最新成员回显
    const detail = await agentTeamApi.detail(team.value.id)
    memberRows.value = detail.members.map((m: TeamMemberRow) => ({ ...m.member }))
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    memberLoading.value = false
  }
}

// ---------------- 测试运行 ----------------
const runVisible = ref(false)
const runTeam = ref<AgentTeam | null>(null)
const runInput = ref('')
const running = ref(false)
const runResult = ref<TeamRun | null>(null)
function openRun(row: AgentTeam) {
  runTeam.value = row
  runInput.value = ''
  runResult.value = null
  runVisible.value = true
}
async function doRun() {
  if (!runTeam.value || !runInput.value.trim()) return ElMessage.warning('请输入测试输入')
  running.value = true
  runResult.value = null
  try {
    runResult.value = await agentTeamApi.run(runTeam.value.id, runInput.value.trim())
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '运行失败')
  } finally {
    running.value = false
  }
}

// ---------------- 运行记录 ----------------
const runsVisible = ref(false)
const runsLoading = ref(false)
const runTeamForList = ref<AgentTeam | null>(null)
const runs = ref<TeamRun[]>([])
const runsTotal = ref(0)
const runsPage = ref(1)
async function loadRuns() {
  if (!runTeamForList.value) return
  runsLoading.value = true
  try {
    const data = await agentTeamApi.runs(runTeamForList.value.id, { page: runsPage.value, size: 20 })
    runs.value = data.records
    runsTotal.value = data.total
  } finally {
    runsLoading.value = false
  }
}
function openRuns(row: AgentTeam) {
  runTeamForList.value = row
  runsPage.value = 1
  runs.value = []
  runsVisible.value = true
  loadRuns()
}

onMounted(() => {
  load()
  loadApps()
})
</script>

<template>
  <div class="page-container multi-page">
    <div class="multi-head">
      <div>
        <h2 class="head-title">多智能体编排</h2>
        <p class="head-desc">编排多个角色智能体协作分工，通过意图匹配 / 轮询 / 并行汇合路由任务</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="openCreate">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>新建团队
      </el-button>
    </div>

    <el-card shadow="never" class="multi-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索团队名称 / 描述"
          clearable
          @keyup.enter="onSearch"
          @clear="onSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-tooltip content="刷新">
          <el-button circle @click="load"><el-icon><Refresh /></el-icon></el-button>
        </el-tooltip>
      </div>

      <el-table v-loading="loading" :data="rows">
        <el-table-column label="团队" min-width="220">
          <template #default="{ row }">
            <div class="team-name">{{ row.name }}</div>
            <div v-if="row.description" class="muted team-desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="路由策略" width="200">
          <template #default="{ row }">
            <el-tag :type="routingOf(row.routing).tag" size="small" effect="light">{{ routingOf(row.routing).label }}</el-tag>
            <div class="muted" style="margin-top: 3px">{{ routingOf(row.routing).desc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="运行次数" width="100" align="center">
          <template #default="{ row }"><span class="mono">{{ row.runCount ?? 0 }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="toggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }"><span class="muted">{{ fmtTime(row.updateTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="openRun(row)">运行</el-button>
            <el-button link type="primary" @click="openMembers(row)">成员</el-button>
            <el-button link @click="openRuns(row)">记录</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
    </el-card>

    <!-- 团队编辑 -->
    <el-dialog v-model="editVisible" :title="editId ? '编辑团队' : '新建团队'" width="520px" :close-on-click-modal="false">
      <el-form label-width="86px">
        <el-form-item label="团队名称" required>
          <el-input v-model="form.name" maxlength="60" show-word-limit placeholder="如 客服应急响应团队" />
        </el-form-item>
        <el-form-item label="团队描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="300" placeholder="团队定位与能力说明（可选）" />
        </el-form-item>
        <el-form-item label="路由策略" required>
          <el-radio-group v-model="form.routing">
            <el-radio-button v-for="k in ROUTING_KEYS" :key="k" :value="k">{{ ROUTINGS[k].label }}</el-radio-button>
          </el-radio-group>
          <div class="form-tip">{{ routingOf(form.routing).desc }}</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成员管理 -->
    <el-drawer v-model="memberVisible" :title="`成员编排 · ${team?.name ?? ''}`" size="860px">
      <div class="drawer-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>路由：{{ routingOf(team?.routing).label }} — {{ routingOf(team?.routing).desc }}。成员需绑定<b>已发布</b>应用；意图匹配下填写关键词可将任务定向分配给该成员。</span>
      </div>
      <div v-loading="memberLoading">
        <el-table :data="memberRows" size="small">
          <el-table-column label="成员" width="180">
            <template #default="{ row }">
              <el-input v-model="row.name" size="small" placeholder="角色名" />
              <el-input v-model="row.description" size="small" style="margin-top: 4px" placeholder="职责描述（可选）" />
            </template>
          </el-table-column>
          <el-table-column label="绑定应用" width="200">
            <template #default="{ row }">
              <el-select v-model="row.appId" size="small" style="width: 100%" filterable placeholder="选择已发布应用">
                <el-option v-for="a in appOptions" :key="a.id" :label="`${a.name}（${a.type}）`" :value="a.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="意图关键词" width="180">
            <template #default="{ row }">
              <el-input v-model="row.keywords" size="small" placeholder="逗号分隔，如 退货,退款" />
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="70" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.priority" :min="1" :max="99" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="启用" width="60" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="" width="50" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeMember($index)"><el-icon><Delete /></el-icon></el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button style="margin-top: 10px" @click="addMember">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>添加成员
        </el-button>
      </div>
      <template #footer>
        <span class="form-tip" style="margin-right: auto">共 {{ memberRows.length }} 名成员</span>
        <el-button @click="memberVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="memberLoading" @click="saveMembers">保存成员</el-button>
      </template>
    </el-drawer>

    <!-- 测试运行 -->
    <el-dialog v-model="runVisible" :title="`团队运行 · ${runTeam?.name ?? ''}`" width="680px" :close-on-click-modal="false">
      <el-input v-model="runInput" type="textarea" :rows="3" placeholder="输入任务/问题，将按当前路由策略分发给成员应用执行" />
      <el-button type="primary" class="btn-gradient" style="margin-top: 10px" :loading="running" @click="doRun">
        {{ running ? '执行中…' : '执行' }}
      </el-button>
      <template v-if="runResult">
        <div class="run-result" :class="runResult.status">
          <div class="rr-head">
            <el-tag :type="runResult.status === 'success' ? 'success' : 'danger'" size="small" effect="light">
              {{ runResult.status === 'success' ? '运行成功' : '运行失败' }}
            </el-tag>
            <span class="muted">路由成员：{{ runResult.routedMember || '-' }} · 耗时 {{ runResult.costMs ?? 0 }}ms</span>
          </div>
          <div v-if="runResult.answer" class="rr-answer"><pre class="pre">{{ runResult.answer }}</pre></div>
          <div v-if="runResult.error" class="rr-error"><pre class="pre">{{ runResult.error }}</pre></div>
          <div v-if="parseTrace(runResult).length" class="rr-trace">
            <div v-for="(s, i) in parseTrace(runResult)" :key="s.memberId" class="step">
              <div class="step-head">
                <span class="step-idx">{{ i + 1 }}</span>
                <b>{{ s.memberName }}</b>
                <el-tag :type="s.status === 'success' ? 'success' : 'danger'" size="small">{{ s.status === 'success' ? '成功' : '失败' }}</el-tag>
                <span class="muted">耗时 {{ s.costMs }}ms</span>
              </div>
              <div v-if="s.answer" class="step-body"><pre class="pre">{{ s.answer }}</pre></div>
              <div v-if="s.error" class="step-body error"><pre class="pre">{{ s.error }}</pre></div>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 运行记录 -->
    <el-dialog v-model="runsVisible" :title="`运行记录 · ${runTeamForList?.name ?? ''}`" width="760px">
      <div v-loading="runsLoading">
        <div v-if="runs.length" class="run-list">
          <div v-for="r in runs" :key="r.id" class="run-item">
            <div class="ri-head">
              <el-tag :type="r.status === 'success' ? 'success' : 'danger'" size="small" effect="light">
                {{ r.status === 'success' ? '成功' : '失败' }}
              </el-tag>
              <b class="ri-title">{{ r.input }}</b>
              <span class="muted">{{ fmtTime(r.createTime) }} · {{ r.costMs ?? 0 }}ms</span>
            </div>
            <div class="ri-body">
              <div v-if="r.answer" class="mono pre">{{ r.answer }}</div>
              <div v-else-if="r.error" class="pre error">{{ r.error }}</div>
              <div v-else class="muted">（无输出）</div>
            </div>
            <div v-if="parseTrace(r).length" class="ri-trace muted">
              成员：{{ r.routedMember }} · {{ parseTrace(r).length }} 步
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无运行记录" />
      </div>
      <el-pagination
        v-if="runsTotal > 20"
        class="pager"
        layout="total, prev, pager, next"
        :total="runsTotal"
        :page-size="20"
        v-model:current-page="runsPage"
        @current-change="loadRuns"
      />
    </el-dialog>
  </div>
</template>

<style scoped>
.multi-page {
  max-width: 1400px;
  margin: 0 auto;
}
.multi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
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
.multi-card {
  border-radius: var(--radius-lg);
  padding: 4px 12px 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.search-input {
  width: 240px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.team-name {
  font-weight: 600;
}
.team-desc {
  margin-top: 2px;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
.form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
}
.drawer-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.run-result {
  margin-top: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}
.run-result.failed {
  border-color: var(--el-color-danger-light-7);
}
.rr-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.rr-answer {
  margin-top: 8px;
}
.rr-error {
  margin-top: 8px;
  color: var(--el-color-danger);
}
.rr-trace {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.step {
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  padding: 8px 10px;
}
.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.step-idx {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.step-body {
  margin-top: 6px;
}
.step-body.error {
  color: var(--el-color-danger);
}
.pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
}
.run-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.run-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}
.ri-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ri-title {
  flex: 1;
  font-size: 13px;
}
.ri-body {
  margin-top: 6px;
  max-height: 140px;
  overflow: auto;
}
.ri-trace {
  margin-top: 6px;
}
.error {
  color: var(--el-color-danger);
}
</style>
