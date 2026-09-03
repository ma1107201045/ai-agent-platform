<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, MagicStick, Plus, Search, VideoPlay } from '@element-plus/icons-vue'
import { toolConnectorApi } from '@/api/tool-connector'
import type { ToolConnector } from '@/api/types'

const loading = ref(false)
const list = ref<ToolConnector[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const filterType = ref<string | undefined>()
const filterStatus = ref<number | undefined>()

const typeOptions = [
  { value: 'http', label: 'HTTP API' },
  { value: 'mysql', label: 'MySQL 数据库' }
]
const typeLabels: Record<string, string> = { http: 'HTTP API 连接器', mysql: 'MySQL 数据库' }
const authLabels: Record<string, string> = { none: '无鉴权', bearer: 'Bearer', basic: 'Basic 账密' }
const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' }
]

function formatTime(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const type = filterType.value || undefined
    const status = filterStatus.value === undefined || filterStatus.value === '' ? undefined : filterStatus.value
    const data = await toolConnectorApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      type,
      status
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

function onPageChange(p: number) {
  page.value = p
  load()
}

function targetText(row: ToolConnector) {
  return row.url || '-'
}

function authText(row: ToolConnector) {
  if (row.type === 'mysql') return row.authUsername ? `账号 ${row.authUsername}` : '无账号'
  const auth = authLabels[row.authType || 'none']
  if (row.authType === 'bearer') return row.authToken ? `${auth} · 已配置` : `${auth} · 未配置`
  if (row.authType === 'basic') return row.authUsername ? `${auth} · ${row.authUsername}` : auth
  return auth
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const headersText = ref('')
const form = reactive({
  name: '',
  description: '',
  type: 'http' as 'http' | 'mysql',
  url: '',
  method: 'GET',
  authType: 'none',
  authToken: '',
  authUsername: '',
  authPassword: ''
})

function openCreate() {
  editId.value = null
  form.name = ''
  form.description = ''
  form.type = 'http'
  form.url = ''
  form.method = 'GET'
  form.authType = 'none'
  form.authToken = ''
  form.authUsername = ''
  form.authPassword = ''
  headersText.value = ''
  dialogVisible.value = true
}

function openEdit(row: ToolConnector) {
  editId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.type = row.type
  form.url = row.url || ''
  form.method = row.method || 'GET'
  form.authType = row.authType || 'none'
  form.authToken = ''
  form.authUsername = row.authUsername || ''
  form.authPassword = ''
  headersText.value = row.headers || ''
  dialogVisible.value = true
}

function onTypeChange() {
  if (form.type === 'http' && !form.authType) form.authType = 'none'
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入连接器名称')
  if (editId.value == null && !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(form.name.trim())) {
    return ElMessage.warning('连接器名称须为英文标识符（字母/数字/下划线，首字符非数字）')
  }
  if (!form.url.trim()) return ElMessage.warning(form.type === 'http' ? '请输入 API 地址' : '请输入 JDBC URL')
  if (form.type === 'http' && !/^https?:\/\//.test(form.url.trim())) {
    return ElMessage.warning('API 地址须以 http:// 或 https:// 开头')
  }
  if (form.type === 'mysql' && !form.url.trim().startsWith('jdbc:mysql://')) {
    return ElMessage.warning('JDBC URL 须以 jdbc:mysql:// 开头，如 jdbc:mysql://127.0.0.1:3306/agent_platform')
  }
  if (form.type === 'mysql' && !form.authUsername.trim()) return ElMessage.warning('请输入数据库用户名')
  let headers: string | undefined
  const raw = headersText.value.trim()
  if (raw) {
    try {
      headers = JSON.stringify(JSON.parse(raw))
    } catch {
      return ElMessage.warning('Headers 必须是合法 JSON 对象')
    }
  } else {
    headers = undefined
  }
  const payload = {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    type: form.type,
    url: form.url.trim(),
    method: form.type === 'http' ? form.method : undefined,
    headers: form.type === 'http' ? headers : undefined,
    authType: form.type === 'http' ? form.authType : 'none',
    authToken: form.type === 'http' && form.authType === 'bearer' && form.authToken.trim() ? form.authToken.trim() : undefined,
    authUsername: form.authUsername.trim() || undefined,
    authPassword: form.authPassword ? form.authPassword : undefined
  }
  saving.value = true
  try {
    if (editId.value != null) {
      await toolConnectorApi.update(editId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await toolConnectorApi.create({ ...payload, status: 1 })
      ElMessage.success('连接器创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/* ---------------- 状态 ---------------- */
async function toggleStatus(row: ToolConnector, val: boolean) {
  const next = val ? 1 : 0
  try {
    await toolConnectorApi.setStatus(row.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
    load()
  } catch {
    // 单向 model-value，失败时刷新还原
  }
}

/* ---------------- 删除 ---------------- */
function remove(row: ToolConnector) {
  ElMessageBox.confirm(
    `确认删除连接器「${row.name}」？删除后由它生成的工具仍会保留，但将无法再基于它创建新工具。`,
    '删除确认',
    { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
    .then(async () => {
      await toolConnectorApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

/* ---------------- 连通性测试 ---------------- */
const testingId = ref<number | null>(null)
const testVisible = ref(false)
const testRow = ref<ToolConnector | null>(null)
const testResult = ref('')

async function doTest(row: ToolConnector) {
  testingId.value = row.id
  try {
    const result = await toolConnectorApi.test(row.id)
    testRow.value = row
    testResult.value = result
    testVisible.value = true
  } finally {
    testingId.value = null
  }
}

function testSuccess() {
  return testResult.value.startsWith('连接成功')
}

/* ---------------- 集成调用：连接器 → HTTP 工具 ---------------- */
const generating = ref<number | null>(null)

async function genTool(row: ToolConnector) {
  try {
    await ElMessageBox.confirm(
      `将基于连接器「${row.name}」创建一个同名 HTTP 工具（继承地址 / Headers / 鉴权）。` +
        '创建后请在「工具管理」中补充参数 Schema，即可在智能体对话与工作流编排中调用该 API。',
      '生成工具',
      { type: 'info', confirmButtonText: '生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  generating.value = row.id
  try {
    await toolConnectorApi.asTool(row.id)
    ElMessage.success(`工具「${row.name}」生成成功，已加入工具管理`)
  } finally {
    generating.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container int-page">
    <div class="int-head">
      <div>
        <h2 class="head-title">数据集成</h2>
        <p class="head-desc">
          连接外部 API 与数据库等数据源，经连通性校验后可一键生成可被智能体调用的工具 ·
          共 {{ total }} 个连接器
        </p>
      </div>
      <el-button type="primary" class="btn-gradient" :icon="Plus" @click="openCreate">新建连接器</el-button>
    </div>

    <el-card shadow="never" class="int-card">
      <!-- 筛选工具栏 -->
      <div class="table-toolbar int-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索名称 / 描述"
            clearable
            class="toolbar-search"
            @keyup.enter="search"
            @clear="search"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterType" placeholder="全部类型" clearable class="toolbar-type" @change="search">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="全部状态" clearable class="toolbar-status" @change="search">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </div>
      </div>

      <el-table v-loading="loading" :data="list">
        <el-table-column label="连接器" min-width="230">
          <template #default="{ row }">
            <div class="conn-main">
              <span class="conn-name">{{ row.name }}</span>
              <span v-if="row.description" class="conn-desc">{{ row.description }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="160">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'http' ? 'warning' : 'success'" effect="light">
              {{ typeLabels[row.type] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标" min-width="240">
          <template #default="{ row }">
            <span class="mono target" :title="targetText(row)">{{ targetText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="鉴权" width="150">
          <template #default="{ row }">
            <span class="auth-text">{{ authText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="(val) => toggleStatus(row, val === true)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">
            <span class="dim-text">{{ formatTime(row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-ops">
              <div class="row-links">
                <el-button
                  link
                  type="primary"
                  :icon="VideoPlay"
                  :loading="testingId === row.id"
                  @click="doTest(row)"
                >
                  测试
                </el-button>
                <el-button
                  v-if="row.type === 'http'"
                  link
                  type="success"
                  :icon="MagicStick"
                  :loading="generating === row.id"
                  @click="genTool(row)"
                >
                  生成工具
                </el-button>
                <el-button link :icon="EditPen" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
              </div>
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
        @current-change="onPageChange"
      />
    </el-card>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editId != null ? '编辑连接器' : '新建连接器'" width="640px" :close-on-click-modal="false">
      <div class="form-body">
        <div class="field-group">
          <div class="field-label">连接器名称 <span class="req">*</span></div>
          <el-input
            v-model="form.name"
            :disabled="editId != null"
            placeholder="英文标识符（生成工具时的工具名），如 slack_api / order_db"
          />
          <p v-if="editId == null" class="field-tip">创建后不可修改，建议与业务目标对应</p>
        </div>
        <div class="field-group">
          <div class="field-label">描述</div>
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="用途说明（可选）" maxlength="255" />
        </div>
        <div class="field-group">
          <div class="field-label">连接类型 <span class="req">*</span></div>
          <el-radio-group v-model="form.type" :disabled="editId != null" @change="onTypeChange">
            <el-radio-button value="http">HTTP API 连接器</el-radio-button>
            <el-radio-button value="mysql">MySQL 数据库</el-radio-button>
          </el-radio-group>
        </div>

        <!-- HTTP 连接器 -->
        <template v-if="form.type === 'http'">
          <div class="field-group">
            <div class="field-label">API 地址 <span class="req">*</span></div>
            <el-input v-model="form.url" placeholder="https://api.example.com/v1" />
          </div>
          <div class="field-row">
            <div class="field-group grow">
              <div class="field-label">请求方式</div>
              <el-select v-model="form.method" style="width: 100%">
                <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']" :key="m" :label="m" :value="m" />
              </el-select>
            </div>
            <div class="field-group" style="width: 220px">
              <div class="field-label">鉴权方式</div>
              <el-select v-model="form.authType" style="width: 100%">
                <el-option label="无" value="none" />
                <el-option label="Bearer Token" value="bearer" />
                <el-option label="Basic 账密" value="basic" />
              </el-select>
            </div>
          </div>
          <div v-if="form.authType === 'bearer'" class="field-group">
            <div class="field-label">Bearer Token</div>
            <el-input v-model="form.authToken" type="password" show-password placeholder="连接目标的访问令牌" />
          </div>
          <div v-if="form.authType === 'basic'" class="field-row">
            <div class="field-group grow">
              <div class="field-label">用户名</div>
              <el-input v-model="form.authUsername" placeholder="Basic 认证用户名" />
            </div>
            <div class="field-group grow">
              <div class="field-label">密码</div>
              <el-input v-model="form.authPassword" type="password" show-password :placeholder="editId != null ? '留空保持不变' : 'Basic 认证密码'" />
            </div>
          </div>
          <div class="field-group">
            <div class="field-label">自定义请求头 Headers</div>
            <el-input
              v-model="headersText"
              type="textarea"
              :rows="2"
              placeholder='JSON 对象，如 {"X-Requested-By":"agent-platform"}'
            />
          </div>
        </template>

        <!-- MySQL 连接器 -->
        <template v-else>
          <div class="field-group">
            <div class="field-label">JDBC URL <span class="req">*</span></div>
            <el-input v-model="form.url" placeholder="jdbc:mysql://127.0.0.1:3306/agent_platform?useSSL=false&amp;serverTimezone=Asia/Shanghai" />
          </div>
          <div class="field-row">
            <div class="field-group grow">
              <div class="field-label">用户名 <span class="req">*</span></div>
              <el-input v-model="form.authUsername" placeholder="数据库账号" />
            </div>
            <div class="field-group grow">
              <div class="field-label">密码</div>
              <el-input v-model="form.authPassword" type="password" show-password :placeholder="editId != null ? '留空保持不变' : '数据库密码'" />
            </div>
          </div>
          <p class="field-tip">当前提供连接连通性与账号权限校验（SELECT 1）；数据库查询类工具可在「工具管理」中以代码脚本方式扩展。</p>
        </template>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试结果对话框 -->
    <el-dialog v-model="testVisible" :title="`连通性测试 - ${testRow?.name || ''}`" width="620px">
      <div class="test-result-head">
        <el-tag :type="testSuccess() ? 'success' : 'danger'" effect="light">
          {{ testSuccess() ? '连接成功' : '连接失败' }}
        </el-tag>
        <span class="dim-text">{{ typeLabels[testRow?.type || 'http'] }} · {{ targetText(testRow!) }}</span>
      </div>
      <el-input
        :model-value="testResult"
        type="textarea"
        :rows="5"
        readonly
        :class="{ 'err-area': !testSuccess() }"
      />
      <template #footer>
        <el-button type="primary" @click="testVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.int-page {
  max-width: 1400px;
  margin: 0 auto;
}
.int-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
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
.int-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.int-toolbar {
  margin-bottom: 8px;
}
.toolbar-left {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.toolbar-search {
  width: 260px;
}
.toolbar-type {
  width: 160px;
}
.toolbar-status {
  width: 130px;
}
.conn-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.conn-name {
  font-size: 13.5px;
  font-weight: 600;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.conn-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
}
.mono {
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
}
.target {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.auth-text {
  font-size: 12.5px;
  color: var(--text-secondary);
}
.row-ops {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.row-links {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  column-gap: 6px;
}
.row-links .el-button {
  padding: 0 2px;
}
.dim-text {
  color: var(--text-tertiary);
  font-size: 12px;
}
/* 表单 */
.form-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.field-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field-row {
  display: flex;
  gap: 16px;
}
.grow {
  flex: 1;
}
.field-label {
  font-size: 13px;
  font-weight: 600;
}
.req {
  color: #f56c6c;
}
.field-tip {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
/* 测试对话框 */
.test-result-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.err-area :deep(textarea) {
  color: #f56c6c;
}
</style>
