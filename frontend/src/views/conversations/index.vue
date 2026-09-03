<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, Delete, EditPen, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { appAgentApi } from '@/api/app-agent'
import { conversationApi } from '@/api/chat-conversation.ts'
import type {
  AppAgent,
  AgentStep,
  ChatConversation,
  ChatMessageRecord,
  TraceItem
} from '@/api/types'

const router = useRouter()

const modeLabels: Record<string, string> = { direct: '直连模型', workflow: '工作流', agent: '智能体' }

/* ---------- 应用 / 模式筛选 ---------- */
const apps = ref<AppAgent[]>([])
const appMap = computed(() => new Map(apps.value.map((a) => [a.id, a])))
const filterAppId = ref<number | null>(null)
const filterMode = ref('')

async function loadApps() {
  try {
    const data = await appAgentApi.page({ page: 1, size: 100 })
    apps.value = data.records
  } catch {
    /* 忽略加载失败 */
  }
}

/* ---------- 会话列表 ---------- */
const loading = ref(false)
const list = ref<ChatConversation[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

async function load() {
  loading.value = true
  try {
    const data = await conversationApi.page({
      appId: filterAppId.value ?? undefined,
      page: page.value,
      size: size.value
    })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  page.value = 1
  load()
}

function appName(row: ChatConversation) {
  return appMap.value.get(row.appId)?.name || `应用 #${row.appId}`
}

function continueChat(row: ChatConversation) {
  router.push(`/app-agents/${row.appId}/chat?conversationId=${row.id}`)
}

/* ---------- 重命名 / 删除 ---------- */
const renameVisible = ref(false)
const renameId = ref(0)
const renameTitle = ref('')
const renaming = ref(false)

function openRename(row: ChatConversation) {
  renameId.value = row.id
  renameTitle.value = row.title || `会话 #${row.id}`
  renameVisible.value = true
}

async function submitRename() {
  if (!renameTitle.value.trim()) return
  renaming.value = true
  try {
    await conversationApi.rename(renameId.value, renameTitle.value.trim())
    ElMessage.success('已重命名')
    renameVisible.value = false
    load()
  } finally {
    renaming.value = false
  }
}

async function remove(row: ChatConversation) {
  await ElMessageBox.confirm(
    `确认删除会话「${row.title || `#${row.id}`}」？该操作不可恢复。`,
    '删除确认',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' }
  )
  await conversationApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

/* ---------- 会话详情（消息时间线） ---------- */
const detailVisible = ref(false)
const detailTitle = ref('')
const detailMsgs = ref<
  { role: 'user' | 'assistant'; content: string; trace?: TraceItem[]; steps?: AgentStep[] }[]
>([])

async function openDetail(row: ChatConversation) {
  detailTitle.value = row.title || `会话 #${row.id}`
  detailMsgs.value = []
  detailVisible.value = true
  try {
    const msgs: ChatMessageRecord[] = await conversationApi.messages(row.id)
    detailMsgs.value = msgs.map((m) => {
      const base: {
        role: 'user' | 'assistant'
        content: string
        trace?: TraceItem[]
        steps?: AgentStep[]
      } = { role: m.role, content: m.content || '' }
      if (m.traceJson) {
        try {
          const parsed = JSON.parse(m.traceJson)
          if (row.mode === 'agent') base.steps = parsed as AgentStep[]
          else base.trace = parsed as TraceItem[]
        } catch {
          /* 忽略损坏的轨迹数据 */
        }
      }
      return base
    })
  } catch {
    /* 忽略加载失败 */
  }
}

onMounted(() => {
  loadApps()
  load()
})
</script>

<template>
  <div class="page-container conv-page">
    <div class="conv-head">
      <div>
        <h2 class="head-title">对话记录</h2>
        <p class="head-desc">检索全部应用的会话历史，支持重命名、回溯完整调用链路</p>
      </div>
      <el-button class="btn-gradient" @click="resetAndLoad">
        <el-icon><Refresh /></el-icon>&nbsp;刷新
      </el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="conv-filter">
      <el-select
        v-model="filterAppId"
        placeholder="全部应用"
        clearable
        style="width: 220px"
        @change="resetAndLoad"
      >
        <el-option v-for="a in apps" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-select
        v-model="filterMode"
        placeholder="全部模式"
        clearable
        style="width: 150px"
        @change="resetAndLoad"
      >
        <el-option
          v-for="(label, key) in modeLabels"
          :key="key"
          :label="label"
          :value="key"
        />
      </el-select>
      <span class="conv-total">共 {{ total }} 条会话</span>
    </div>

    <!-- 会话表格 -->
    <el-table
      v-loading="loading"
      :data="list"
      class="conv-table hover-card"
      @row-click="openDetail"
    >
      <el-table-column label="会话标题" min-width="240">
        <template #default="{ row }">
          <div class="conv-title">
            <el-icon :size="15" class="conv-title-icon"><ChatDotRound /></el-icon>
            <span class="conv-title-text">{{ row.title || `会话 #${row.id}` }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="所属应用" min-width="160">
        <template #default="{ row }">
          <span class="conv-app">{{ appName(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="模式" width="120">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ modeLabels[row.mode] || row.mode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column prop="updateTime" label="最近活跃" min-width="160" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="continueChat(row)">继续对话</el-button>
          <el-button link type="primary" @click.stop="openRename(row)">
            <el-icon :size="13"><EditPen /></el-icon>&nbsp;重命名
          </el-button>
          <el-button link type="danger" @click.stop="remove(row)">
            <el-icon :size="13"><Delete /></el-icon>&nbsp;删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="conv-empty">
          <el-icon :size="36" class="conv-empty-icon"><ChatDotRound /></el-icon>
          <p>暂无对话记录，去应用里开始一次对话吧</p>
        </div>
      </template>
    </el-table>

    <div class="conv-pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="resetAndLoad"
      />
    </div>

    <!-- 消息时间线抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="520px" destroy-on-close>
      <div class="trace-timeline">
        <div v-for="(m, i) in detailMsgs" :key="i" class="msg-item" :class="m.role">
          <div class="msg-head">
            <span class="msg-role">{{ m.role === 'user' ? '用户' : '智能体' }}</span>
          </div>
          <div class="msg-content">{{ m.content || '（无输出）' }}</div>
          <el-collapse v-if="m.trace?.length || m.steps?.length" class="trace-collapse">
            <el-collapse-item
              :title="m.steps?.length ? `工具调用轨迹 (${m.steps.length})` : `节点执行轨迹 (${m.trace?.length})`"
            >
              <div v-for="(t, j) in m.trace" :key="j" class="trace-item">
                <div class="trace-head">
                  <el-tag
                    size="small"
                    :type="t.status === 'success' ? 'success' : t.status === 'error' ? 'danger' : 'info'"
                  >
                    {{ t.status }}
                  </el-tag>
                  <span class="trace-label">{{ t.label }}</span>
                  <el-tag size="small" type="info" effect="plain">{{ t.nodeType }}</el-tag>
                  <span class="trace-cost">{{ t.costMs }}ms</span>
                </div>
                <div v-if="t.error" class="trace-error">{{ t.error }}</div>
                <div v-if="t.input" class="trace-text"><span class="trace-key">输入</span>{{ t.input }}</div>
                <div v-if="t.output" class="trace-text"><span class="trace-key">输出</span>{{ t.output }}</div>
              </div>
              <div v-for="(s, j) in m.steps" :key="j" class="trace-item">
                <div class="trace-head">
                  <el-tag size="small" type="success">调用</el-tag>
                  <span class="trace-label">{{ s.toolName }}</span>
                  <span class="trace-cost">{{ s.costMs }}ms</span>
                </div>
                <div v-if="s.arguments" class="trace-text"><span class="trace-key">参数</span>{{ s.arguments }}</div>
                <div v-if="s.result" class="trace-text"><span class="trace-key">结果</span>{{ s.result }}</div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <div v-if="!detailMsgs.length" class="trace-empty">该会话暂无消息记录</div>
      </div>
    </el-drawer>

    <!-- 重命名对话框 -->
    <el-dialog v-model="renameVisible" title="重命名会话" width="420px" destroy-on-close>
      <el-input
        v-model="renameTitle"
        placeholder="输入新的会话标题"
        maxlength="60"
        show-word-limit
        @keyup.enter="submitRename"
      />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="submitRename">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.conv-page {
  max-width: 1280px;
  margin: 0 auto;
}
.conv-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
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

.conv-filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0 16px;
}
.conv-total {
  font-size: 12.5px;
  color: var(--text-tertiary);
}

.conv-table {
  border-radius: var(--radius-lg);
}
.conv-table :deep(.el-table__row) {
  cursor: pointer;
}
.conv-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.conv-title-icon {
  color: var(--brand-1);
  flex-shrink: 0;
}
.conv-title-text {
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-app {
  color: var(--text-secondary);
  font-size: 13px;
}
.conv-empty {
  padding: 48px 0;
  text-align: center;
  color: var(--text-tertiary);
}
.conv-empty-icon {
  color: var(--text-tertiary);
  opacity: 0.5;
}
.conv-empty p {
  margin-top: 10px;
  font-size: 13px;
}

.conv-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ---------- 消息时间线（与运维页一致） ---------- */
.trace-timeline {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.msg-item {
  padding: 12px 14px;
  border-radius: var(--radius-md);
  background: var(--bg-page);
  border: 1px solid var(--border-color);
}
.msg-item.user {
  background: var(--brand-gradient-soft);
  border-color: transparent;
}
.msg-head {
  margin-bottom: 6px;
}
.msg-role {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-1);
}
.msg-item.user .msg-role {
  color: var(--text-tertiary);
}
.msg-content {
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}
.trace-collapse {
  margin-top: 8px;
  border: none;
  background: transparent;
}
.trace-item {
  padding: 8px 0;
  border-top: 1px dashed var(--border-color);
  font-size: 12.5px;
}
.trace-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.trace-label {
  font-weight: 600;
  color: var(--text-primary);
}
.trace-cost {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.trace-error {
  margin-top: 6px;
  color: var(--el-color-danger);
  font-size: 12px;
}
.trace-text {
  margin-top: 6px;
  color: var(--text-secondary);
  line-height: 1.5;
  word-break: break-all;
  max-height: 120px;
  overflow: auto;
  white-space: pre-wrap;
}
.trace-key {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  font-size: 11px;
  font-weight: 600;
}
.trace-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}
</style>
