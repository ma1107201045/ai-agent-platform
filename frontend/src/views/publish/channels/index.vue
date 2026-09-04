<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CHANNEL_TYPE_OPTIONS,
  channelTypeLabel,
  publishChannelApi,
  type ChannelStats,
  type PublishChannel,
  type PublishChannelMsg
} from '@/api/publish-channel'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent } from '@/api/types'

const loading = ref(false)
const list = ref<PublishChannel[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const filterAppId = ref<number | undefined>()
const filterType = ref<string | undefined>()
const appOptions = ref<AppAgent[]>([])

async function loadApps() {
  try {
    const data = await appAgentApi.page({ page: 1, size: 500 })
    appOptions.value = data.records
  } catch { /* ignore */ }
}
const appMap = computed(() => new Map(appOptions.value.map((a) => [a.id, a])))
const appName = (id?: number) => (id ? appMap.value.get(id)?.name || `应用 #${id}` : '-')
const appPublished = (id?: number) => (id ? appMap.value.get(id)?.status === 1 : false)
function fmt(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
async function load() {
  loading.value = true
  try {
    const data = await publishChannelApi.page({ page: page.value, size: size.value, keyword: keyword.value || undefined, appId: filterAppId.value, channelType: filterType.value })
    list.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}
function search() { page.value = 1; load() }

async function toggle(row: PublishChannel, val: string | number | boolean) {
  const next = val === true || val === 1 ? 1 : 0
  try {
    await publishChannelApi.setEnabled(row.id, next as 0 | 1)
    row.enabled = next
    ElMessage.success(next ? '已启用' : '已停用')
  } catch { load() }
}
async function remove(row: PublishChannel) {
  try {
    await ElMessageBox.confirm(`确认删除渠道「${row.name}」？其消息记录将一并清除。`, '删除确认', { type: 'error' })
    await publishChannelApi.remove(row.id)
    ElMessage.success('删除成功')
    load()
  } catch { /* cancel */ }
}

/* ---------- 表单 ---------- */
const CONFIG_META: Record<string, { key: string; label: string; placeholder: string }[]> = {
  wechat_mp: [
    { key: 'token', label: 'Token', placeholder: '微信公众平台服务器配置中的 Token' },
    { key: 'encodingAesKey', label: 'EncodingAESKey', placeholder: '消息加密密钥（43 位）' }
  ],
  feishu: [
    { key: 'appId', label: 'App ID', placeholder: '飞书开放平台应用凭证 App ID' },
    { key: 'appSecret', label: 'App Secret', placeholder: '飞书开放平台应用凭证 App Secret' }
  ],
  dingtalk: [
    { key: 'appKey', label: 'AppKey', placeholder: '钉钉开放平台 AppKey' },
    { key: 'appSecret', label: 'AppSecret', placeholder: '钉钉开放平台 AppSecret' }
  ],
  web: [{ key: 'token', label: '接入 Token', placeholder: '自定义鉴权 Token' }],
  webhook: []
}
const configFields = (type: string) => CONFIG_META[type] || []

const dialogVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const savedRow = ref<PublishChannel | null>(null)
const form = reactive({ name: '', appId: undefined as number | undefined, channelType: 'webhook', description: '', config: {} as Record<string, string> })
function resetForm() {
  form.name = ''
  form.appId = undefined
  form.channelType = 'webhook'
  form.description = ''
  form.config = {}
  configFields(form.channelType).forEach((f) => (form.config[f.key] = ''))
}
function openCreate() { editId.value = null; savedRow.value = null; resetForm(); dialogVisible.value = true }
function openEdit(row: PublishChannel) {
  editId.value = row.id
  savedRow.value = row
  form.name = row.name
  form.appId = row.appId
  form.channelType = row.channelType || 'webhook'
  form.description = row.description || ''
  form.config = {}
  let parsed: Record<string, string> = {}
  try { parsed = row.configJson ? JSON.parse(row.configJson) : {} } catch { parsed = {} }
  configFields(form.channelType).forEach((f) => (form.config[f.key] = String(parsed[f.key] ?? '')))
  dialogVisible.value = true
}
function onTypeChange() {
  const cfg: Record<string, string> = {}
  configFields(form.channelType).forEach((f) => (cfg[f.key] = ''))
  form.config = cfg
}
const typeHint = (t: string) => CHANNEL_TYPE_OPTIONS.find((o) => o.value === t)?.hint || ''
const callbackUrl = (id?: number) => `${window.location.origin}/api/publish/channels/${id}/callback`

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写渠道名称')
  if (!form.appId) return ElMessage.warning('请选择绑定的应用')
  saving.value = true
  try {
    const payload = { name: form.name.trim(), appId: form.appId, channelType: form.channelType, description: form.description.trim() || undefined, configJson: JSON.stringify(form.config) }
    if (editId.value) {
      await publishChannelApi.update(editId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await publishChannelApi.create(payload)
      ElMessage.success('渠道创建成功')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally { saving.value = false }
}

/* ---------- 详情抽屉 ---------- */
const detailVisible = ref(false)
const activeChannel = ref<PublishChannel | null>(null)
const stats = ref<ChannelStats | null>(null)
const messages = ref<PublishChannelMsg[]>([])
const msgTotal = ref(0)
const msgPage = ref(1)
const msgLoading = ref(false)
const msgKeyword = ref('')
const maxTrend = ref(1)
const testContent = ref('')
const testFrom = ref('')
const testing = ref(false)
const testResult = ref<PublishChannelMsg | null>(null)

const STATUS_META: Record<string, { label: string; type: 'success' | 'danger' | 'info' }> = {
  success: { label: '成功', type: 'success' },
  failed: { label: '失败', type: 'danger' },
  skipped: { label: '跳过', type: 'info' }
}

async function loadStats() {
  if (!activeChannel.value) return
  stats.value = await publishChannelApi.stats(activeChannel.value.id)
  const max = Math.max(...(stats.value?.trend || []).map((t) => t.count), 1)
  maxTrend.value = max
}
async function loadMessages() {
  if (!activeChannel.value) return
  msgLoading.value = true
  try {
    const data = await publishChannelApi.messages(activeChannel.value.id, { page: msgPage.value, size: 10, keyword: msgKeyword.value || undefined })
    messages.value = data.records
    msgTotal.value = data.total
  } finally { msgLoading.value = false }
}
async function openDetail(row: PublishChannel) {
  activeChannel.value = row
  detailVisible.value = true
  msgPage.value = 1
  msgKeyword.value = ''
  messages.value = []
  msgTotal.value = 0
  stats.value = null
  testResult.value = null
  await Promise.all([loadStats(), loadMessages()])
}
async function runTest() {
  if (!activeChannel.value) return
  if (!testContent.value.trim()) return ElMessage.warning('请输入测试内容')
  testing.value = true
  try {
    const msg = await publishChannelApi.callback(activeChannel.value.id, { content: testContent.value.trim(), fromUser: testFrom.value.trim() || 'tester' })
    testResult.value = msg
    if (msg.status === 'success') ElMessage.success('回调处理成功')
    else if (msg.status === 'failed') ElMessage.error(msg.errorMsg || '执行失败')
    else ElMessage.warning(msg.errorMsg || '消息未处理')
    await Promise.all([loadStats(), loadMessages()])
  } catch (e) {
    ElMessage.error((e as Error).message || '回调失败')
  } finally { testing.value = false }
}

onMounted(() => { loadApps(); load() })
</script>

<template>
  <div class="page-container channel-page">
    <div class="channel-head">
      <div>
        <h2 class="head-title">渠道管理</h2>
        <p class="head-desc">将智能体接入微信公众号 / 飞书 / 钉钉 / Web 等终端 · 共 {{ total }} 个渠道</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="openCreate"><el-icon><Plus /></el-icon>&nbsp;新建渠道</el-button>
    </div>

    <el-card shadow="never" class="channel-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input v-model="keyword" placeholder="搜索渠道名称 / 描述" clearable class="toolbar-search" @keyup.enter="search" @clear="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterType" placeholder="渠道类型" clearable style="width: 150px" @change="search">
            <el-option v-for="o in CHANNEL_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="filterAppId" placeholder="绑定应用" clearable filterable style="width: 220px" @change="search">
            <el-option v-for="app in appOptions" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
        </div>
      </div>

      <el-table v-loading="loading" :data="list">
        <el-table-column label="渠道" min-width="190">
          <template #default="{ row }">
            <div class="cell-main">
              <span class="cell-name">{{ row.name }}</span>
              <span class="dim-text">{{ row.description || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }"><el-tag size="small" effect="light">{{ channelTypeLabel(row.channelType) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="绑定应用" min-width="160">
          <template #default="{ row }">
            <div class="cell-main">
              <span class="cell-name">{{ appName(row.appId) }}</span>
              <span :class="appPublished(row.appId) ? 'pub-tag' : 'unpub-tag'">{{ appPublished(row.appId) ? '已发布' : '未发布' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="消息数" width="90">
          <template #default="{ row }"><span class="msg-count">{{ row.msgCount ?? 0 }}</span></template>
        </el-table-column>
        <el-table-column label="最近消息" width="150">
          <template #default="{ row }"><span class="dim-text">{{ fmt(row.lastMsgAt) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" inline-prompt active-text="启用" inactive-text="停用" @change="(v: string | number | boolean) => toggle(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }"><span class="dim-text">{{ fmt(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-links">
              <el-button link size="small" @click="openDetail(row)">接入监控</el-button>
              <el-button link size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 16px; justify-content: flex-end" layout="total, prev, pager, next" :total="total" :page-size="size" v-model:current-page="page" @current-change="search" />
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑渠道' : '新建渠道'" width="600px" :close-on-click-modal="false">
      <div class="form-body">
        <div class="field-row">
          <div class="field-group grow">
            <div class="field-label">渠道名称 <span class="req">*</span></div>
            <el-input v-model="form.name" placeholder="例如：官网在线客服" maxlength="128" />
          </div>
          <div class="field-group" style="width: 190px">
            <div class="field-label">渠道类型 <span class="req">*</span></div>
            <el-select v-model="form.channelType" style="width: 100%" @change="onTypeChange">
              <el-option v-for="o in CHANNEL_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </div>
        </div>
        <div class="field-group">
          <div class="field-label">绑定应用 <span class="req">*</span></div>
          <el-select v-model="form.appId" placeholder="选择要接入的应用（需已发布）" filterable style="width: 100%">
            <el-option v-for="app in appOptions" :key="app.id" :label="`${app.name}${app.status === 1 ? '' : '（未发布）'}`" :value="app.id" />
          </el-select>
        </div>
        <div v-if="configFields(form.channelType).length" class="config-box">
          <div class="config-title">{{ channelTypeLabel(form.channelType) }} 凭证配置</div>
          <div v-for="f in configFields(form.channelType)" :key="f.key" class="field-group">
            <div class="field-label">{{ f.label }}</div>
            <el-input v-model="form.config[f.key]" :placeholder="f.placeholder" type="password" show-password />
          </div>
          <p class="form-tip">{{ typeHint(form.channelType) }}</p>
        </div>
        <div class="field-group">
          <div class="field-label">描述</div>
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="渠道用途说明（可选）" maxlength="512" />
        </div>
        <el-alert v-if="editId && savedRow" type="success" :closable="false">
          <template #title>回调地址</template>
          <span class="callback-url">{{ callbackUrl(savedRow.id) }}</span>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 接入监控抽屉 -->
    <el-drawer v-model="detailVisible" title="接入配置与运行监控" size="720px">
      <template v-if="activeChannel">
        <div class="drawer-head">
          <div>
            <div class="drawer-name">{{ activeChannel.name }}</div>
            <span class="dim-text">{{ channelTypeLabel(activeChannel.channelType) }} · {{ appName(activeChannel.appId) }}</span>
          </div>
          <el-tag :type="activeChannel.enabled === 1 ? 'success' : 'info'" effect="light">{{ activeChannel.enabled === 1 ? '已启用' : '已停用' }}</el-tag>
        </div>
        <el-alert type="info" :closable="false">
          <template #title>回调地址（配置到对应终端平台）</template>
          <span class="callback-url">{{ callbackUrl(activeChannel.id) }}</span>
        </el-alert>

        <div v-if="stats" class="stat-row">
          <div class="stat-card"><span class="stat-num">{{ stats.total }}</span><span class="stat-label">累计消息</span></div>
          <div class="stat-card"><span class="stat-num">{{ stats.today }}</span><span class="stat-label">今日消息</span></div>
          <div class="stat-card"><span class="stat-num danger-num">{{ stats.failed }}</span><span class="stat-label">失败消息</span></div>
        </div>

        <div v-if="stats" class="trend-box">
          <div class="section-title">近 7 日消息趋势</div>
          <div class="trend-bars">
            <div v-for="t in stats.trend" :key="t.date" class="trend-col">
              <span class="trend-num">{{ t.count }}</span>
              <div class="trend-bar-wrap"><div class="trend-bar" :style="{ height: `${Math.max(4, (t.count / maxTrend) * 52)}px` }" /></div>
              <span class="trend-date">{{ t.date.slice(5) }}</span>
            </div>
          </div>
        </div>

        <div class="section-title">测试回调</div>
        <div class="test-box">
          <el-input v-model="testContent" type="textarea" :rows="2" placeholder="输入一条消息，模拟终端用户发往该渠道" />
          <div class="test-actions">
            <el-input v-model="testFrom" placeholder="来源用户（openid/昵称，可选）" />
            <el-button type="primary" :loading="testing" @click="runTest">发送测试</el-button>
          </div>
          <div v-if="testResult" class="test-result">
            <el-tag size="small" :type="STATUS_META[testResult.status]?.type">{{ STATUS_META[testResult.status]?.label }}</el-tag>
            <span class="dim-text">{{ testResult.errorMsg || '处理成功' }}</span>
            <div v-if="testResult.reply" class="reply-box"><span class="reply-label">应用回复：</span>{{ testResult.reply }}</div>
          </div>
        </div>

        <div class="section-title">消息记录</div>
        <div class="test-actions" style="margin-bottom: 10px">
          <el-input v-model="msgKeyword" placeholder="搜索内容 / 用户" clearable @keyup.enter="msgPage = 1; loadMessages()" @clear="msgPage = 1; loadMessages()">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="msgPage = 1; loadMessages()">查询</el-button>
        </div>
        <el-table v-loading="msgLoading" :data="messages" size="small">
          <el-table-column label="方向" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.direction === 'inbound' ? 'primary' : 'success'" effect="plain">{{ row.direction === 'inbound' ? '入站' : '出站' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="用户" prop="fromUser" width="110" show-overflow-tooltip />
          <el-table-column label="消息内容" prop="content" show-overflow-tooltip />
          <el-table-column label="应用回复" show-overflow-tooltip>
            <template #default="{ row }"><span class="dim-text">{{ row.reply || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="76">
            <template #default="{ row }"><el-tag size="small" :type="STATUS_META[row.status]?.type" effect="light">{{ STATUS_META[row.status]?.label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="时间" width="140">
            <template #default="{ row }"><span class="dim-text">{{ fmt(row.createTime) }}</span></template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top: 12px; justify-content: flex-end" small layout="total, prev, pager, next" :total="msgTotal" :page-size="10" v-model:current-page="msgPage" @current-change="loadMessages" />
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.channel-page { max-width: 1400px; margin: 0 auto; }
.channel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.head-title { font-size: 22px; font-weight: 700; }
.head-desc { margin-top: 4px; font-size: 13px; color: var(--text-tertiary); }
.channel-card { border-radius: var(--radius-lg); overflow: hidden; }
.toolbar-left { display: flex; gap: 10px; flex-wrap: wrap; }
.toolbar-search { width: 240px; }
.cell-main { display: flex; flex-direction: column; gap: 3px; padding: 2px 0; }
.cell-name { font-size: 13.5px; font-weight: 600; }
.dim-text { color: var(--text-tertiary); font-size: 12px; }
.msg-count { font-weight: 700; color: var(--brand-1); }
.pub-tag, .unpub-tag { width: fit-content; font-size: 11px; padding: 0 6px; border-radius: 4px; }
.pub-tag { color: #529b2e; background: rgba(82, 155, 46, 0.12); }
.unpub-tag { color: var(--text-tertiary); background: var(--fill-light); }
.row-links { display: flex; flex-wrap: wrap; justify-content: center; }
.row-links .el-button { padding: 0 2px; }
.form-body { display: flex; flex-direction: column; gap: 16px; }
.field-row { display: flex; gap: 16px; }
.grow { flex: 1; }
.field-group { display: flex; flex-direction: column; gap: 8px; }
.field-label { font-size: 13px; font-weight: 600; }
.req { color: #f56c6c; }
.config-box { display: flex; flex-direction: column; gap: 14px; padding: 14px; border: 1px dashed var(--border-color); border-radius: var(--radius-md); background: var(--fill-lighter); }
.config-title { font-size: 13px; font-weight: 600; color: var(--brand-1); }
.form-tip { font-size: 12px; color: var(--text-tertiary); line-height: 1.6; margin: 0; }
.callback-url { display: block; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 12px; line-height: 1.8; word-break: break-all; }
.drawer-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.drawer-name { font-size: 16px; font-weight: 700; margin-bottom: 4px; }
.stat-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 16px 0 4px; }
.stat-card { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 14px; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
.stat-num { font-size: 24px; font-weight: 800; color: var(--brand-1); }
.danger-num { color: #f56c6c; }
.stat-label { font-size: 12px; color: var(--text-tertiary); }
.trend-box { margin: 8px 0 4px; }
.section-title { font-size: 14px; font-weight: 700; margin: 16px 0 10px; }
.trend-bars { display: flex; align-items: flex-end; gap: 12px; height: 92px; padding: 4px 8px; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
.trend-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; height: 100%; justify-content: flex-end; }
.trend-num { font-size: 11px; color: var(--text-secondary); }
.trend-bar-wrap { display: flex; align-items: flex-end; height: 60px; }
.trend-bar { width: 22px; min-height: 3px; border-radius: 6px 6px 2px 2px; background: var(--brand-gradient); }
.trend-date { font-size: 11px; color: var(--text-tertiary); }
.test-box { display: flex; flex-direction: column; gap: 10px; padding: 14px; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
.test-actions { display: flex; gap: 10px; }
.test-actions .el-input:first-child { flex: 1; }
.test-result { display: flex; align-items: center; gap: 10px; font-size: 12.5px; }
.reply-box { flex: 1; padding: 8px 10px; background: var(--fill-light); border-radius: 6px; font-size: 12.5px; line-height: 1.6; word-break: break-all; }
.reply-label { color: var(--text-tertiary); }
</style>
