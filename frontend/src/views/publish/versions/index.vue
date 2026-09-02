<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, CopyDocument, Document, EditPen, Promotion, Refresh, View } from '@element-plus/icons-vue'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent, AppAgentVersion } from '@/api/types'

const route = useRoute()
const router = useRouter()

/* ---------------- 应用选择 ---------------- */
const apps = ref<AppAgent[]>([])
const appId = ref<number | undefined>()
const appTypeLabel: Record<string, string> = { chatflow: '对话', workflow: '工作流', agent: '智能体' }

const currentApp = computed(() => apps.value.find((a) => a.id === appId.value))

async function loadApps(preferId?: number) {
  try {
    const data = await appAgentApi.page({ page: 1, size: 500 })
    apps.value = data.records
  } catch {
    /* 忽略加载失败 */
  }
  if (appId.value != null && apps.value.some((a) => a.id === appId.value)) {
    return
  }
  const target = preferId && apps.value.some((a) => a.id === preferId)
    ? preferId
    : (apps.value.find((a) => a.publishedVersionId != null)?.id ?? apps.value[0]?.id)
  appId.value = target
}

/* ---------------- 版本加载 ---------------- */
const loading = ref(false)
const list = ref<AppAgentVersion[]>([])

async function load() {
  if (!appId.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = await appAgentApi.versions(appId.value)
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

watch(appId, () => load())
onMounted(() => {
  const q = route.query.appId
  loadApps(typeof q === 'string' && /^\d+$/.test(q) ? Number(q) : undefined)
})

/* ---------------- 展示工具 ---------------- */
function formatTime(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const NODE_TYPE_NAMES: Record<string, string> = {
  start: '开始',
  end: '结束',
  llm: '大模型',
  agent: '智能体',
  condition: '条件分支',
  code: '代码',
  http: 'HTTP 请求',
  template: '文本模板',
  knowledge: '知识检索'
}

function parseJson(text?: string): unknown {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function prettyJson(text?: string): string {
  const obj = parseJson(text)
  return obj == null ? (text ?? '') : JSON.stringify(obj, null, 2)
}

interface DslInfo {
  nodeCount: number
  edgeCount: number
  summary: string
  hasContent: boolean
}

function dslInfo(row: AppAgentVersion): DslInfo | null {
  const obj = parseJson(row.workflowJson) as { nodes?: { type?: string }[]; edges?: unknown[] } | null
  if (!obj || !Array.isArray(obj.nodes)) return null
  const counts: Record<string, number> = {}
  for (const n of obj.nodes) {
    const key = NODE_TYPE_NAMES[n.type || ''] || n.type || '未知'
    counts[key] = (counts[key] || 0) + 1
  }
  const summary = Object.entries(counts)
    .map(([k, v]) => `${k} ×${v}`)
    .join(' · ')
  const nodes = obj.nodes ?? []
  return {
    nodeCount: nodes.length,
    edgeCount: Array.isArray(obj.edges) ? obj.edges.length : 0,
    summary,
    hasContent: nodes.length > 0 || (obj.edges ?? []).length > 0
  }
}

const isPublishedRow = (row: AppAgentVersion) =>
  row.isPublished === 1 || currentApp.value?.publishedVersionId === row.id

const publishedVersionNo = computed(() => list.value.find((r) => isPublishedRow(r))?.version)

/** 版本快照中的可展示内容段落（工作流 DSL / 提示词配置） */
function snapshotParts(row: AppAgentVersion): { key: string; title: string; content: string; hint: string }[] {
  const parts: { key: string; title: string; content: string; hint: string }[] = []
  const info = dslInfo(row)
  if (info && info.hasContent) {
    parts.push({
      key: 'workflow',
      title: '工作流编排',
      content: prettyJson(row.workflowJson),
      hint: `${info.nodeCount} 个节点 · ${info.edgeCount} 条连线 · ${info.summary}`
    })
  }
  if (row.promptConfig && row.promptConfig.trim() && row.promptConfig !== '{}') {
    parts.push({
      key: 'prompt',
      title: '提示词配置',
      content: prettyJson(row.promptConfig),
      hint: ''
    })
  }
  return parts
}

/* ---------------- 查看 / 复制 ---------------- */
const detailVisible = ref(false)
const detailVersion = ref<AppAgentVersion | null>(null)
const detailTab = ref('workflow')
const detailTitle = computed(() =>
  detailVersion.value ? `v${detailVersion.value.version} · 版本快照内容` : '版本快照内容')

function openDetail(row: AppAgentVersion) {
  detailVersion.value = row
  const parts = snapshotParts(row)
  detailTab.value = parts[0]?.key ?? 'workflow'
  detailVisible.value = true
}

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

/* ---------------- 回滚 ---------------- */
async function rollback(row: AppAgentVersion) {
  if (!appId.value) return
  await ElMessageBox.confirm(
    `确认将 v${row.version}（${formatTime(row.createTime)}）快照回滚为当前草稿？\n回滚仅覆盖草稿配置，不会自动发布；请到编辑器确认后再重新发布上线。`,
    '回滚确认',
    { confirmButtonText: '回滚为草稿', cancelButtonText: '取消', type: 'warning' }
  )
  await appAgentApi.rollback(appId.value, row.id)
  ElMessage.success('已回滚为草稿')
}

function goEdit() {
  if (!appId.value) return
  router.push(`/app/agents/${appId.value}/edit`)
}

</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>版本历史</h2>
        <p class="page-sub">查看每次发布的配置快照，可将任意历史版本一键回滚为草稿</p>
      </div>
      <el-button v-if="appId" type="primary" plain :icon="EditPen" @click="goEdit">前往编辑器</el-button>
    </div>

    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span class="filter-label">选择应用</span>
          <el-select v-model="appId" placeholder="请选择应用" filterable style="width: 280px">
            <el-option v-for="a in apps" :key="a.id" :label="a.name" :value="a.id">
              <div class="app-option">
                <span>{{ a.name }}</span>
                <el-tag size="small" effect="plain">{{ appTypeLabel[a.type] || a.type }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </div>
        <div v-if="currentApp" class="filter-item app-meta">
          <el-tag size="small" effect="plain">{{ appTypeLabel[currentApp.type] || currentApp.type }}</el-tag>
          <span v-if="publishedVersionNo" class="online-tag">
            <span class="online-dot"></span> 当前线上 v{{ publishedVersionNo }}
          </span>
        </div>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="version-wrap">
      <!-- 已有版本：时间线 -->
      <div v-if="list.length" class="timeline">
        <div v-for="row in list" :key="row.id" class="timeline-item" :class="{ published: isPublishedRow(row) }">
          <div class="timeline-rail">
            <div class="rail-dot" :class="{ online: isPublishedRow(row) }">
              <el-icon v-if="isPublishedRow(row)" :size="12"><Promotion /></el-icon>
            </div>
            <div v-if="row !== list[list.length - 1]" class="rail-line" />
          </div>

          <div class="version-card card">
            <div class="version-head">
              <div class="version-id">
                <span class="version-badge">v{{ row.version }}</span>
                <el-tag v-if="isPublishedRow(row)" size="small" type="success" effect="light">当前线上</el-tag>
                <el-tag v-else size="small" effect="plain">历史快照</el-tag>
              </div>
              <div class="version-time">
                <el-icon :size="13"><Clock /></el-icon>
                {{ formatTime(row.createTime) }}
                <span class="operator">操作者 #{{ row.createdBy ?? '-' }}</span>
              </div>
            </div>

            <div v-if="snapshotParts(row).length" class="version-body">
              <div v-for="part in snapshotParts(row)" :key="part.key" class="part-row">
                <span class="part-title">
                  <el-icon :size="13"><Document /></el-icon>
                  {{ part.title }}
                </span>
                <span v-if="part.hint" class="part-hint">{{ part.hint }}</span>
              </div>
            </div>
            <div v-else class="version-empty">该快照未包含可展示的编排/提示词配置</div>

            <div class="version-actions">
              <el-button link type="primary" :icon="View" @click="openDetail(row)">查看内容</el-button>
              <el-button link :icon="CopyDocument" @click="copyText(prettyJson(row.workflowJson || row.promptConfig))">
                复制 JSON
              </el-button>
              <el-tooltip
                :disabled="!isPublishedRow(row)"
                content="该版本正在线上运行，无需回滚"
                placement="top"
              >
                <span>
                  <el-button
                    link
                    type="warning"
                    :disabled="isPublishedRow(row)"
                    @click="rollback(row)"
                  >
                    回滚为草稿
                  </el-button>
                </span>
              </el-tooltip>
            </div>
          </div>
        </div>
      </div>

      <!-- 无版本 -->
      <div v-else-if="!loading && apps.length" class="empty-card card">
        <el-icon :size="40" class="empty-icon"><Clock /></el-icon>
        <h3>暂无发布记录</h3>
        <p>「{{ currentApp?.name || '当前应用' }}」还没有发布版本，发布后将在此生成配置快照</p>
        <el-button v-if="appId" type="primary" :icon="EditPen" @click="goEdit">前往发布</el-button>
      </div>

      <!-- 无应用 -->
      <div v-else-if="!loading && !apps.length" class="empty-card card">
        <el-icon :size="40" class="empty-icon"><EditPen /></el-icon>
        <h3>还没有应用</h3>
        <p>先创建一个智能体应用，发布后可在此管理版本历史</p>
      </div>
    </div>

    <!-- 快照详情 -->
    <el-dialog v-model="detailVisible" :title="detailTitle" width="640px" destroy-on-close>
      <div v-if="detailVersion" class="detail-body">
        <el-tabs v-model="detailTab">
          <el-tab-pane v-if="snapshotParts(detailVersion).length" :label="snapshotParts(detailVersion)[0].title" :name="snapshotParts(detailVersion)[0].key">
            <pre class="code-view">{{ snapshotParts(detailVersion)[0].content }}</pre>
          </el-tab-pane>
          <el-tab-pane v-if="snapshotParts(detailVersion).some((p) => p.key === 'prompt')" label="提示词配置" name="prompt">
            <pre class="code-view">{{ snapshotParts(detailVersion).find((p) => p.key === 'prompt')!.content }}</pre>
          </el-tab-pane>
          <el-tab-pane label="原始快照" name="raw">
            <pre class="code-view">{{ prettyJson(JSON.stringify({ workflowJson: detailVersion.workflowJson, promptConfig: detailVersion.promptConfig })) }}</pre>
          </el-tab-pane>
        </el-tabs>
        <div class="detail-foot">
          <span class="head-extra">快照 JSON</span>
          <el-button link :icon="CopyDocument" @click="copyText(prettyJson(detailVersion.workflowJson || detailVersion.promptConfig))">复制</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
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
  gap: 12px;
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
.app-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.app-meta {
  gap: 8px;
  font-size: 12px;
}
.online-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #059669;
  font-size: 13px;
}
.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.18);
}

.version-wrap {
  min-height: 200px;
}
.timeline-item {
  display: flex;
  gap: 14px;
}
.timeline-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 20px;
  flex-shrink: 0;
}
.rail-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  border: 2px solid var(--el-border-color);
  flex-shrink: 0;
}
.rail-dot.online {
  background: #d1fae5;
  border-color: #10b981;
  color: #059669;
}
.rail-line {
  width: 2px;
  flex: 1;
  background: var(--el-border-color-lighter);
  margin: 4px 0;
}
.version-card {
  flex: 1;
  margin-bottom: 12px;
}
.card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 14px 16px;
}
.version-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.version-id {
  display: flex;
  align-items: center;
  gap: 8px;
}
.version-badge {
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.version-time {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.operator {
  color: var(--el-text-color-placeholder);
  margin-left: 6px;
}
.version-body {
  margin: 10px 0 2px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 10px;
}
.part-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
  padding: 2px 0;
}
.part-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 500;
}
.part-hint {
  color: var(--el-text-color-secondary);
}
.version-empty {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  margin: 10px 0 4px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 10px;
}
.version-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
}

.empty-card {
  text-align: center;
  padding: 48px 16px;
}
.empty-icon {
  color: var(--el-text-color-placeholder);
  margin-bottom: 8px;
}
.empty-card h3 {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
}
.empty-card p {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.detail-body {
  padding: 0 2px;
}
.code-view {
  margin: 0;
  padding: 12px;
  max-height: 420px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.detail-foot {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.head-extra {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
