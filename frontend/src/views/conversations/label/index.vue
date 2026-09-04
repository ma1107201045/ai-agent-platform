<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { feedbackApi } from '@/api/chat-feedback'
import type { LabelMessage, LabelStats } from '@/api/chat-feedback'
import { conversationApi } from '@/api/chat-conversation'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent } from '@/api/types'

// ---------------- 常量与映射 ----------------

const LABEL_TYPES: { value: string; label: string; tag: 'danger' | 'warning' | 'info' | 'success' }[] = [
  { value: 'correct', label: '回答准确', tag: 'success' },
  { value: 'incorrect', label: '回答错误', tag: 'danger' },
  { value: 'hallucination', label: '存在幻觉', tag: 'warning' },
  { value: 'off_topic', label: '答非所问', tag: 'info' },
  { value: 'vague', label: '表述含糊', tag: 'info' }
]
const LABEL_TYPE_MAP = Object.fromEntries(LABEL_TYPES.map((t) => [t.value, t]))

function fmtTime(t?: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

// ---------------- 数据加载 ----------------

const loading = ref(false)
const rows = ref<LabelMessage[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const appId = ref<number | undefined>(undefined)
/** 0未标注 1已标注 空=全部 */
const labeledFilter = ref<'' | 0 | 1>('')
const stats = ref<LabelStats | null>(null)

const appOptions = ref<AppAgent[]>([])
const labeledCount = computed(() => stats.value?.labeledCount ?? 0)
const coverageText = computed(() => (stats.value?.coverage ?? 0).toFixed(1) + '%')

async function load() {
  loading.value = true
  try {
    const data = await feedbackApi.messages({
      page: page.value,
      size: size.value,
      appId: appId.value || undefined,
      labeled: labeledFilter.value === '' ? undefined : labeledFilter.value,
      keyword: keyword.value.trim() || undefined
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
async function loadStats() {
  try {
    stats.value = await feedbackApi.stats()
  } catch {
    /* ignore */
  }
}
function onSearch() {
  page.value = 1
  load()
}

function openConversation(row: LabelMessage) {
  window.open(`/ops/conversations?conv=${row.conversationId}`, '_blank')
}

onMounted(() => {
  load()
  loadStats()
  appAgentApi
    .page({ page: 1, size: 500 })
    .then((d) => (appOptions.value = d.records.filter((a) => a.status === 1)))
    .catch(() => {})
})

// ---------------- 标注弹窗 ----------------

const dialogVisible = ref(false)
const saving = ref(false)
/** 被标注消息对应的原始用户提问 */
const userQuestion = ref('')
const current = ref<LabelMessage | null>(null)
const form = reactive<{
  rating: 'good' | 'bad'
  labelType: string
  correctedAnswer: string
  note: string
}>({ rating: 'good', labelType: '', correctedAnswer: '', note: '' })

async function openLabel(row: LabelMessage) {
  current.value = row
  userQuestion.value = ''
  form.rating = row.rating || 'good'
  form.labelType = row.labelType || ''
  form.correctedAnswer = row.correctedAnswer || ''
  form.note = row.note || ''
  dialogVisible.value = true
  // 异步补充上下文：取该会话中目标助手消息前最近的用户提问
  try {
    const messages = await conversationApi.messages(row.conversationId)
    const idx = messages.findIndex((m) => m.id === row.messageId)
    if (idx > 0) {
      for (let i = idx - 1; i >= 0; i--) {
        if (messages[i].role === 'user') {
          userQuestion.value = messages[i].content || ''
          break
        }
      }
    }
  } catch {
    /* ignore */
  }
}

async function save() {
  if (!current.value) return
  saving.value = true
  try {
    await feedbackApi.save({
      messageId: current.value.messageId,
      rating: form.rating,
      labelType: form.labelType || undefined,
      correctedAnswer: form.correctedAnswer.trim() || undefined,
      note: form.note.trim() || undefined
    })
    ElMessage.success('标注已保存')
    dialogVisible.value = false
    load()
    loadStats()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function unlabel(row: LabelMessage) {
  if (!row.feedbackId) return
  ElMessageBox.confirm('取消该条消息的标注？', '取消标注', { type: 'warning' })
    .then(async () => {
      await feedbackApi.remove(row.feedbackId!)
      ElMessage.success('已取消标注')
      load()
      loadStats()
    })
    .catch(() => {})
}

function labelState(row: LabelMessage) {
  if (!row.labeled) {
    return { label: '未标注', type: 'info' as const }
  }
  return row.rating === 'bad' ? { label: '差评', type: 'danger' as const } : { label: '好评', type: 'success' as const }
}
</script>

<template>
  <div class="page-container label-page">
    <!-- 头部 -->
    <div class="label-head">
      <div>
        <h2 class="head-title">对话标注</h2>
        <p class="head-desc">对智能体回答进行质量标注与纠错，沉淀优质样本回流评测与微调</p>
      </div>
      <div class="head-actions">
        <el-select
          v-model="appId"
          placeholder="按应用过滤"
          clearable
          filterable
          style="width: 200px"
          @change="onSearch"
        >
          <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
        </el-select>
        <el-select v-model="labeledFilter" placeholder="按标注状态过滤" clearable style="width: 150px" @change="onSearch">
          <el-option label="未标注" :value="0" />
          <el-option label="已标注" :value="1" />
        </el-select>
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索消息内容"
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
    </div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-label">助手消息</span>
        <span class="stat-num">{{ stats?.totalMessages ?? 0 }}</span>
        <span class="dim-text">可标注的回答总数</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">已标注</span>
        <span class="stat-num">{{ labeledCount }}</span>
        <span class="dim-text">覆盖率 {{ coverageText }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">好评</span>
        <span class="stat-num good">{{ stats?.goodCount ?? 0 }}</span>
        <span class="dim-text">回答质量良好</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">差评</span>
        <span class="stat-num bad">{{ stats?.badCount ?? 0 }}</span>
        <span class="dim-text">需要关注与优化</span>
      </div>
    </div>

    <!-- 消息列表 -->
    <el-card shadow="never" class="label-card">
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="回答内容" min-width="300">
          <template #default="{ row }">
            <div class="content-cell" :title="row.content">
              <el-icon class="content-icon"><ChatDotRound /></el-icon>
              <span class="content-text">{{ row.content || '（空回复）' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="会话 / 应用" width="190">
          <template #default="{ row }">
            <div class="conv-name" :title="row.conversationTitle">{{ row.conversationTitle }}</div>
            <div class="app-tag">
              <el-tag size="small" :type="row.appId > 0 ? 'primary' : 'info'" effect="light">
                {{ row.appName }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标注状态" width="130" align="center">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag :type="labelState(row).type" size="small" effect="dark">{{ labelState(row).label }}</el-tag>
              <el-tag v-if="row.labeled && row.labelType" size="small" effect="plain" class="type-tag">
                {{ LABEL_TYPE_MAP[row.labelType]?.label ?? row.labelType }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="消息时间" width="150">
          <template #default="{ row }">
            <span class="muted">{{ fmtTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标注信息" width="170">
          <template #default="{ row }">
            <template v-if="row.labeled">
              <div class="muted">{{ row.createdByName || '-' }}</div>
              <div class="muted">{{ fmtTime(row.feedbackTime) }}</div>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openLabel(row)">
              {{ row.labeled ? '查看 / 修改' : '标注' }}
            </el-button>
            <el-button v-if="row.labeled" link type="danger" @click="unlabel(row)">取消</el-button>
            <el-button v-if="row.labeled" link @click="openConversation(row)">对话</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="load"
      />
    </el-card>

    <!-- 标注弹窗 -->
    <el-dialog v-model="dialogVisible" title="消息标注" width="640px" :close-on-click-modal="false">
      <template v-if="current">
        <div class="qa-pair">
          <div v-if="userQuestion" class="qa-item user">
            <div class="qa-role">用户提问</div>
            <div class="qa-content">{{ userQuestion }}</div>
          </div>
          <div class="qa-item bot">
            <div class="qa-role">智能体回答</div>
            <div class="qa-content">{{ current.content || '（空回复）' }}</div>
          </div>
          <div class="qa-meta">
            <span>{{ current.appName }}</span>
            <span>·</span>
            <span>{{ current.conversationTitle }}</span>
            <span>·</span>
            <span class="muted">{{ fmtTime(current.createTime) }}</span>
          </div>
        </div>

        <el-form label-width="92px">
          <el-form-item label="整体评价" required>
            <el-radio-group v-model="form.rating">
              <el-radio-button value="good">
                <el-icon style="margin-right: 4px"><CircleCheck /></el-icon>好评 · 回答可用
              </el-radio-button>
              <el-radio-button value="bad">
                <el-icon style="margin-right: 4px"><WarningFilled /></el-icon>差评 · 需要修正
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="问题类型">
            <el-select v-model="form.labelType" clearable placeholder="选择问题归类（可选）" style="width: 100%">
              <el-option v-for="t in LABEL_TYPES" :key="t.value" :label="t.label" :value="t.value">
                <el-tag size="small" :type="t.tag" effect="plain" style="border: none">{{ t.label }}</el-tag>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="参考答案">
            <el-input
              v-model="form.correctedAnswer"
              type="textarea"
              :rows="4"
              placeholder="差评时可补充理想回答，用于评测打标与后续样本回流"
            />
          </el-form-item>
          <el-form-item label="备注说明">
            <el-input v-model="form.note" maxlength="200" show-word-limit placeholder="记录标注依据 / 期望改进点（可选）" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存标注</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.label-page {
  max-width: 1400px;
  margin: 0 auto;
}
.label-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
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
  align-items: center;
  gap: 8px;
}
.search-input {
  width: 220px;
}
.label-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
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
.stat-num.good {
  color: var(--el-color-success);
}
.stat-num.bad {
  color: var(--el-color-danger);
}
.dim-text,
.muted {
  font-size: 12px;
  color: var(--text-tertiary);
}

.content-cell {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}
.content-icon {
  flex: none;
  margin-top: 2px;
  color: var(--text-tertiary);
}
.content-text {
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.6;
  font-size: 13px;
}
.conv-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}
.app-tag .el-tag {
  font-weight: 400;
}
.status-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.type-tag {
  border-color: var(--border-color);
  color: var(--text-secondary);
}

/* 标注弹窗 */
.qa-pair {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  margin-bottom: 14px;
  overflow: hidden;
}
.qa-item {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
}
.qa-item:last-of-type {
  border-bottom: none;
}
.qa-item.user {
  background: var(--fill-lighter);
}
.qa-role {
  font-size: 11px;
  color: var(--text-tertiary);
  font-weight: 600;
  margin-bottom: 4px;
}
.qa-item.bot .qa-role {
  color: var(--brand-1);
}
.qa-content {
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 160px;
  overflow-y: auto;
}
.qa-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--fill-lighter);
  border-top: 1px solid var(--border-color);
}
@media (max-width: 1100px) {
  .stat-row {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
