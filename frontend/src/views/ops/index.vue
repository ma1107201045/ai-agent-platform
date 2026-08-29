<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChatDotRound, DataAnalysis, MagicStick, Promotion } from '@element-plus/icons-vue'
import { appApi } from '@/api/app'
import { conversationApi } from '@/api/conversation'
import type {
  App,
  AgentStep,
  AppStats,
  ChatConversation,
  ChatMessageRecord,
  TraceItem
} from '@/api/types'

const tab = ref('overview')

/* ---------- 数据看板 ---------- */
const loadingApps = ref(false)
const apps = ref<App[]>([])
const stats = ref<Record<number, AppStats>>({})

const totalApps = computed(() => apps.value.length)
const publishedApps = computed(() => apps.value.filter((a) => a.status === 1).length)
const totalConversations = computed(() =>
  Object.values(stats.value).reduce((s, v) => s + v.conversationCount, 0)
)
const totalMessages = computed(() =>
  Object.values(stats.value).reduce((s, v) => s + v.messageCount, 0)
)

async function loadOverview() {
  loadingApps.value = true
  try {
    const data = await appApi.page({ page: 1, size: 100 })
    apps.value = data.records
    if (apps.value.length) {
      stats.value = await appApi.batchStats(apps.value.map((a) => a.id))
    }
  } finally {
    loadingApps.value = false
  }
}

const typeLabels: Record<string, string> = { chatflow: '对话流', workflow: '工作流', agent: '智能体' }

/* ---------- 链路追踪 ---------- */
const loadingConvs = ref(false)
const conversations = ref<ChatConversation[]>([])
const detailVisible = ref(false)
const detailTitle = ref('')
const detailMsgs = ref<
  { role: 'user' | 'assistant'; content: string; trace?: TraceItem[]; steps?: AgentStep[] }[]
>([])

const modeLabels: Record<string, string> = { direct: '直连模型', workflow: '工作流', agent: '智能体' }

async function loadConversations() {
  loadingConvs.value = true
  try {
    const data = await conversationApi.page({ page: 1, size: 50 })
    conversations.value = data.records
  } finally {
    loadingConvs.value = false
  }
}

async function openDetail(row: ChatConversation) {
  detailTitle.value = row.title || `会话 #${row.id}`
  detailMsgs.value = []
  detailVisible.value = true
  try {
    const msgs: ChatMessageRecord[] = await conversationApi.messages(row.id)
    detailMsgs.value = msgs.map((m) => {
      const base: { role: 'user' | 'assistant'; content: string; trace?: TraceItem[]; steps?: AgentStep[] } = {
        role: m.role,
        content: m.content || ''
      }
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
  loadOverview()
  loadConversations()
})
</script>

<template>
  <div class="page-container ops-page">
    <div class="ops-head">
      <div>
        <h2 class="head-title">运维与评估</h2>
        <p class="head-desc">监控应用运行状态，回溯完整调用链路，保障线上稳定性</p>
      </div>
    </div>

    <el-tabs v-model="tab" class="ops-tabs">
      <!-- 数据看板 -->
      <el-tab-pane label="数据看板" name="overview">
        <div v-loading="loadingApps" class="stat-grid">
          <div class="stat-card hover-card">
            <div class="stat-icon" style="background: rgba(91, 108, 255, 0.12); color: #5b6cff">
              <el-icon :size="22"><MagicStick /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ totalApps }}</div>
              <div class="stat-label">应用总数</div>
            </div>
          </div>
          <div class="stat-card hover-card">
            <div class="stat-icon" style="background: rgba(16, 185, 129, 0.12); color: #10b981">
              <el-icon :size="22"><Promotion /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ publishedApps }}</div>
              <div class="stat-label">已发布应用</div>
            </div>
          </div>
          <div class="stat-card hover-card">
            <div class="stat-icon" style="background: rgba(139, 92, 246, 0.12); color: #8b5cf6">
              <el-icon :size="22"><ChatDotRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ totalConversations }}</div>
              <div class="stat-label">会话总数</div>
            </div>
          </div>
          <div class="stat-card hover-card">
            <div class="stat-icon" style="background: rgba(14, 165, 233, 0.12); color: #0ea5e9">
              <el-icon :size="22"><DataAnalysis /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ totalMessages }}</div>
              <div class="stat-label">消息总数</div>
            </div>
          </div>
        </div>

        <el-table v-loading="loadingApps" :data="apps" class="ops-table" style="margin-top: 20px">
          <el-table-column prop="name" label="应用名称" min-width="180" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ typeLabels[row.type] || row.type }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" size="small" type="success" effect="light">已发布</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">草稿</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="会话数" width="100">
            <template #default="{ row }">{{ stats[row.id]?.conversationCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="消息数" width="100">
            <template #default="{ row }">{{ stats[row.id]?.messageCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" min-width="160" />
        </el-table>
      </el-tab-pane>

      <!-- 链路追踪 -->
      <el-tab-pane label="链路追踪" name="traces">
        <el-table v-loading="loadingConvs" :data="conversations" class="ops-table" @row-click="openDetail">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="会话标题" min-width="220">
            <template #default="{ row }">{{ row.title || `会话 #${row.id}` }}</template>
          </el-table-column>
          <el-table-column label="模式" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ modeLabels[row.mode] || row.mode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="160" />
          <el-table-column prop="updateTime" label="最近活跃" min-width="160" />
        </el-table>
        <p class="ops-tip">点击任意会话，回溯完整的消息链路与工具调用轨迹</p>
      </el-tab-pane>
    </el-tabs>

    <!-- 链路详情抽屉 -->
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
  </div>
</template>

<style scoped>
.ops-page {
  max-width: 1280px;
  margin: 0 auto;
}
.ops-head {
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
.ops-tabs {
  margin-top: 8px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
}
.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}
.stat-label {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--text-tertiary);
}

.ops-table :deep(.el-table__row) {
  cursor: pointer;
}
.ops-tip {
  margin-top: 12px;
  font-size: 12.5px;
  color: var(--text-tertiary);
}

/* ---------- 链路详情 ---------- */
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
  color: #ef4444;
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
