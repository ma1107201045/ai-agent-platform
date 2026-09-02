<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { appApiKeyApi } from '@/api/app-api-key'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent, AppApiKey } from '@/api/types'

const loading = ref(false)
const list = ref<AppApiKey[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const filterAppId = ref<number | undefined>()
const filterStatus = ref<number | undefined>()

const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' }
]

/** 可关联的应用（新建时下拉选择） */
const appOptions = ref<AppAgent[]>([])
const appTypeLabel: Record<string, string> = {
  chatflow: '对话',
  workflow: '工作流',
  agent: '智能体'
}

async function loadApps() {
  try {
    const data = await appAgentApi.page({ page: 1, size: 500 })
    appOptions.value = data.records
  } catch {
    // 应用加载失败不阻塞页面
  }
}

async function load() {
  loading.value = true
  try {
    const appId = filterAppId.value || undefined
    const status = filterStatus.value === '' || filterStatus.value == null ? undefined : filterStatus.value
    const data = await appApiKeyApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      appId,
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

function appName(id?: number) {
  const app = appOptions.value.find((a) => a.id === id)
  return app ? app.name : id ? `应用 #${id}` : '-'
}

function appType(id?: number) {
  const app = appOptions.value.find((a) => a.id === id)
  return app ? appTypeLabel[app.type] || app.type : ''
}

/* ---------------- 时间 / 状态工具 ---------------- */
function formatTime(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function isExpired(row: AppApiKey) {
  return !!row.expiresAt && new Date(row.expiresAt).getTime() <= Date.now()
}

function maskKey(row: AppApiKey) {
  return (row.keyPrefix || 'sk-') + '••••••••••'
}

/* ---------------- 复制 ---------------- */
async function copyText(text: string, tip = '已复制到剪贴板') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(tip)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ElMessage.success(tip)
    } catch {
      ElMessage.error('复制失败，请手动复制')
    }
    document.body.removeChild(ta)
  }
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const form = reactive({
  name: '',
  appId: undefined as number | undefined,
  expiresAt: '',
  rateLimit: '',
  remark: ''
})

function openCreate() {
  editId.value = null
  form.name = ''
  form.appId = undefined
  form.expiresAt = ''
  form.rateLimit = ''
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: AppApiKey) {
  editId.value = row.id
  form.name = row.name
  form.appId = row.appId
  form.expiresAt = row.expiresAt || ''
  form.rateLimit = row.rateLimit != null ? String(row.rateLimit) : ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

function validRateLimit(): number | undefined {
  const raw = form.rateLimit.trim()
  if (!raw) return undefined
  const n = Number(raw)
  if (!Number.isInteger(n) || n < 1) {
    throw new Error('限流须为正整数')
  }
  return n
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入密钥名称')
    return
  }
  if (!editId.value && !form.appId) {
    ElMessage.warning('请选择关联应用')
    return
  }
  let rateLimit: number | undefined
  try {
    rateLimit = validRateLimit()
  } catch (e) {
    ElMessage.warning((e as Error).message)
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      expiresAt: form.expiresAt || undefined,
      rateLimit,
      remark: form.remark.trim() || undefined
    }
    if (editId.value) {
      await appApiKeyApi.update(editId.value, payload)
      ElMessage.success('保存成功')
      dialogVisible.value = false
      load()
    } else {
      const created = await appApiKeyApi.create({
        appId: form.appId!,
        ...payload
      })
      ElMessage.success('密钥创建成功')
      dialogVisible.value = false
      load()
      if (created?.plainKey) {
        showSecret(created)
      }
    }
  } finally {
    saving.value = false
  }
}

/* ---------------- 状态 / 轮换 / 删除 ---------------- */
async function toggleStatus(row: AppApiKey, val: boolean) {
  const next = val ? 1 : 0
  try {
    await appApiKeyApi.setStatus(row.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
    load()
  } catch {
    // 失败时无需回滚（switch 使用单向 model-value，加载后自动还原）
  }
}

async function rotate(row: AppApiKey) {
  try {
    await ElMessageBox.confirm(
      `确认轮换「${row.name}」的密钥？旧密钥将立即失效，正在使用的服务需切换为新密钥。`,
      '轮换确认',
      { type: 'warning', confirmButtonText: '确认轮换' }
    )
    const updated = await appApiKeyApi.rotate(row.id)
    ElMessage.success('轮换成功')
    load()
    if (updated?.plainKey) {
      showSecret(updated)
    }
  } catch {
    /* 用户取消或请求失败 */
  }
}

function remove(row: AppApiKey) {
  ElMessageBox.confirm(`确认删除「${row.name}」？删除后该密钥将无法再用于调用，不可恢复。`, '删除确认', {
    type: 'error'
  })
    .then(async () => {
      await appApiKeyApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

/* ---------------- 明文密钥一次性展示 ---------------- */
const secretVisible = ref(false)
const secretValue = ref('')
const secretFrom = ref<AppApiKey | null>(null)

function showSecret(key: AppApiKey) {
  secretFrom.value = key
  secretValue.value = key.plainKey || ''
  secretVisible.value = true
}

/* ---------------- 调用示例 ---------------- */
const exampleVisible = ref(false)
const exampleRow = ref<AppApiKey | null>(null)

function openExample(row: AppApiKey) {
  exampleRow.value = row
  exampleVisible.value = true
}

function buildSnippets(row: AppApiKey) {
  const base = window.location.origin
  const url = `${base}/api/portal/public/app-agents/${row.appId}/chat`
  return {
    curl: `curl -X POST '${url}' \\
  -H 'Authorization: Bearer <你的 API Key>' \\
  -H 'Content-Type: application/json' \\
  -d '{"messages":[{"role":"user","content":"你好"}]}'`,
    js: `const resp = await fetch('${url}', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Authorization: 'Bearer <你的 API Key>'
  },
  body: JSON.stringify({ messages: [{ role: 'user', content: '你好' }] })
})
const data = await resp.json()
console.log(data)`,
    python: `import requests

url = '${url}'
headers = { 'Authorization': 'Bearer <你的 API Key>' }
resp = requests.post(
    url,
    json={ 'messages': [{ 'role': 'user', 'content': '你好' }] },
    headers=headers
)
print(resp.json())`
  }
}

onMounted(() => {
  loadApps()
  load()
})
</script>

<template>
  <div class="page-container api-page">
    <div class="api-head">
      <div>
        <h2 class="head-title">API 密钥</h2>
        <p class="head-desc">共 {{ total }} 个密钥 · 为已发布应用生成调用密钥，供外部系统接入</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="openCreate">
        <el-icon><Key /></el-icon>&nbsp;新建密钥
      </el-button>
    </div>

    <el-card shadow="never" class="api-card">
      <!-- 筛选工具栏 -->
      <div class="table-toolbar api-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索密钥名称 / 前缀"
            clearable
            class="toolbar-search"
            @keyup.enter="search"
            @clear="search"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select
            v-model="filterAppId"
            placeholder="全部应用"
            clearable
            filterable
            class="toolbar-select"
            @change="search"
          >
            <el-option v-for="app in appOptions" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="全部状态" clearable class="toolbar-status" @change="search">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </div>
      </div>

      <el-table v-loading="loading" :data="list">
        <el-table-column label="密钥" min-width="240">
          <template #default="{ row }">
            <div class="key-main">
              <span class="key-name">{{ row.name }}</span>
              <span class="key-masked" @click="copyText(row.keyPrefix, '密钥前缀已复制')">
                <span class="mono">{{ maskKey(row) }}</span>
                <el-icon :size="12"><CopyDocument /></el-icon>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="关联应用" min-width="150">
          <template #default="{ row }">
            <div class="app-cell">
              <span class="app-type">{{ appType(row.appId) }}</span>
              <span class="app-name">{{ row.appName || appName(row.appId) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="isExpired(row)" size="small" type="danger" effect="light">已过期</el-tag>
            <el-tag v-else size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="限流" width="110">
          <template #default="{ row }">
            <span :class="row.rateLimit ? '' : 'dim-text'">{{ row.rateLimit ? `${row.rateLimit} 次/分` : '不限' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="150">
          <template #default="{ row }">
            <span :class="{ expired: isExpired(row), dim: !row.expiresAt }">
              {{ row.expiresAt ? formatTime(row.expiresAt) : '永久有效' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="调用统计" width="170">
          <template #default="{ row }">
            <div class="usage-cell">
              <span>{{ row.usageCount ?? 0 }} 次调用</span>
              <span class="dim-text">最近 {{ row.lastUsedAt ? formatTime(row.lastUsedAt) : '从未使用' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">
            <span class="dim-text">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-ops">
              <el-switch
                :model-value="row.status === 1"
                :disabled="isExpired(row)"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
                @change="(val) => toggleStatus(row, val === true)"
              />
              <div class="row-links">
                <el-button link type="primary" size="small" @click="openExample(row)">示例</el-button>
                <el-button link size="small" @click="openEdit(row)">编辑</el-button>
                <el-button link type="warning" size="small" @click="rotate(row)">轮换</el-button>
                <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑密钥' : '新建密钥'" width="560px" :close-on-click-modal="false">
      <div class="form-body">
        <div class="field-group">
          <div class="field-label">密钥名称 <span class="req">*</span></div>
          <el-input v-model="form.name" placeholder="例如：生产环境 / 测试环境 / 第三方系统 A" maxlength="64" show-word-limit />
        </div>
        <div class="field-group">
          <div class="field-label">关联应用 <span class="req">*</span></div>
          <el-select v-if="!editId" v-model="form.appId" placeholder="选择要对外提供调用的应用" filterable style="width: 100%">
            <el-option
              v-for="app in appOptions"
              :key="app.id"
              :label="`${app.name}（${appTypeLabel[app.type] || app.type}${app.status === 1 ? '' : ' · 未发布' }）`"
              :value="app.id"
            />
          </el-select>
          <div v-else class="edit-app-name">{{ appName(form.appId) }}<span class="edit-app-tip">密钥创建后不可更换应用，如需请删除重建</span></div>
        </div>
        <div class="field-row">
          <div class="field-group grow">
            <div class="field-label">过期时间</div>
            <el-date-picker
              v-model="form.expiresAt"
              type="datetime"
              placeholder="留空 = 永不过期"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </div>
          <div class="field-group" style="width: 170px">
            <div class="field-label">限流（次/分）</div>
            <el-input v-model="form.rateLimit" placeholder="留空 = 不限" type="number" min="1" @keydown.enter.prevent />
          </div>
        </div>
        <div class="field-group">
          <div class="field-label">备注</div>
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="用途说明（可选）" maxlength="255" />
        </div>
        <p class="form-tip">完整密钥仅在创建 / 轮换时展示一次，此后仅保留哈希与前缀，请妥善保管。</p>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 明文密钥一次性展示 -->
    <el-dialog v-model="secretVisible" title="请保存您的密钥" width="620px" :close-on-click-modal="false" :show-close="false">
      <el-alert type="warning" :closable="false" show-icon class="secret-alert">
        <template #title>完整密钥仅显示一次</template>
        <span>关闭后将无法再次查看；丢失时请创建新密钥或执行「轮换」作废旧值。</span>
      </el-alert>
      <div class="secret-area">
        <div class="secret-caption">
          <span>{{ secretFrom?.name }} · {{ appName(secretFrom?.appId) }}</span>
          <span class="dim-text">{{ secretFrom?.keyPrefix }}</span>
        </div>
        <el-input :model-value="secretValue" type="textarea" :rows="3" readonly class="mono" />
      </div>
      <template #footer>
        <el-button @click="secretVisible = false">关闭</el-button>
        <el-button type="primary" class="btn-gradient" @click="copyText(secretValue, '密钥已复制')">
          <el-icon><CopyDocument /></el-icon>&nbsp;复制密钥
        </el-button>
      </template>
    </el-dialog>

    <!-- 调用示例抽屉 -->
    <el-drawer v-model="exampleVisible" title="API 调用示例" size="620px">
      <template v-if="exampleRow">
        <div class="example-head">
          <span class="example-app">{{ appName(exampleRow.appId) }}</span>
          <el-tag size="small" type="warning" effect="light">密钥 {{ exampleRow.name }}</el-tag>
        </div>
        <el-alert type="info" :closable="false" show-icon class="example-tip">
          将下方示例中的 <code>&lt;你的 API Key&gt;</code> 替换为调用密钥；
          请先发布该应用，且密钥须为启用状态。响应格式为统一 Result JSON，业务数据在 <code>data</code> 字段。
        </el-alert>

        <div class="snippet-block">
          <div class="snippet-head">
            <span class="snippet-title">cURL</span>
            <el-button link type="primary" size="small" @click="copyText(buildSnippets(exampleRow!).curl, 'cURL 已复制')">
              <el-icon :size="13"><CopyDocument /></el-icon>&nbsp;复制
            </el-button>
          </div>
          <pre class="code-pre">{{ buildSnippets(exampleRow).curl }}</pre>
        </div>

        <div class="snippet-block">
          <div class="snippet-head">
            <span class="snippet-title">JavaScript (fetch)</span>
            <el-button link type="primary" size="small" @click="copyText(buildSnippets(exampleRow!).js, 'JS 代码已复制')">
              <el-icon :size="13"><CopyDocument /></el-icon>&nbsp;复制
            </el-button>
          </div>
          <pre class="code-pre">{{ buildSnippets(exampleRow).js }}</pre>
        </div>

        <div class="snippet-block">
          <div class="snippet-head">
            <span class="snippet-title">Python (requests)</span>
            <el-button link type="primary" size="small" @click="copyText(buildSnippets(exampleRow!).python, 'Python 代码已复制')">
              <el-icon :size="13"><CopyDocument /></el-icon>&nbsp;复制
            </el-button>
          </div>
          <pre class="code-pre">{{ buildSnippets(exampleRow).python }}</pre>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.api-page {
  max-width: 1400px;
  margin: 0 auto;
}
.api-head {
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
.api-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.api-toolbar {
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
.toolbar-select {
  width: 220px;
}
.toolbar-status {
  width: 120px;
}
.dim-text {
  color: var(--text-tertiary);
}
.dim {
  color: var(--text-tertiary);
}
.expired {
  color: #f56c6c;
  font-weight: 600;
}
.key-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px 0;
}
.key-name {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.3;
}
.key-masked {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 220px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: color 0.15s ease;
}
.key-masked:hover {
  color: var(--brand-1);
}
.mono {
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
}
.app-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.app-type {
  flex-shrink: 0;
  padding: 1px 8px;
  font-size: 11px;
  line-height: 18px;
  color: var(--brand-1);
  background: var(--brand-gradient-soft);
  border-radius: 6px;
}
.app-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.usage-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
}
/* 操作列：开关 + 文字操作 */
.row-ops {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
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
/* 编辑对话框 */
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
  color: var(--text-primary);
}
.req {
  color: #f56c6c;
}
.edit-app-name {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13.5px;
  line-height: 32px;
  font-weight: 500;
}
.edit-app-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  font-weight: 400;
}
.form-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
/* 明文密钥展示 */
.secret-alert {
  margin-bottom: 16px;
}
.secret-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.secret-caption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  font-weight: 500;
}
.secret-area .el-textarea :deep(textarea) {
  font-size: 13px;
  word-break: break-all;
}
/* 调用示例抽屉 */
.example-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.example-app {
  font-size: 15px;
  font-weight: 700;
}
.example-tip {
  margin-bottom: 18px;
}
.example-tip code {
  padding: 1px 5px;
  background: var(--fill-light);
  border-radius: 4px;
  font-size: 12px;
}
.snippet-block {
  margin-bottom: 18px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.snippet-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: var(--fill-light);
  border-bottom: 1px solid var(--border-color);
}
.snippet-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-secondary);
}
.code-pre {
  margin: 0;
  padding: 14px 16px;
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-primary);
  background: var(--fill-lighter);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
}
</style>
