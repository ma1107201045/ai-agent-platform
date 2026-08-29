<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ChatDotRound, Delete, EditPen, Promotion, User } from '@element-plus/icons-vue'
import { appApi } from '@/api/app'
import { conversationApi } from '@/api/conversation'
import { modelApi } from '@/api/model'
import type { AgentStep, ChatConversation, ChatModelInfo, TraceItem } from '@/api/types'
import MarkdownContent from '@/components/MarkdownContent.vue'

const route = useRoute()
const router = useRouter()
const appId = Number(route.params.id)

interface ChatItem {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  error?: boolean
  trace?: TraceItem[]
  agentSteps?: AgentStep[]
}

/** 对话模式：direct 直连模型 / workflow 运行应用工作流 / agent 智能体（工具调用） */
type ChatMode = 'direct' | 'workflow' | 'agent'

const appName = ref('')
const welcomeMessage = ref('')
const openingQuestions = ref<string[]>([])
const models = ref<ChatModelInfo[]>([])
const modelId = ref<number | null>(null)
const messages = ref<ChatItem[]>([])
const input = ref('')
const sending = ref(false)
const mode = ref<ChatMode>('direct')
const traceVisible = ref(false)
const listRef = ref<HTMLElement | null>(null)

/** 当前会话（null 表示尚未创建的新会话） */
const conversationId = ref<number | null>(null)
const conversationTitle = ref('')
const conversations = ref<ChatConversation[]>([])
const loadingConvs = ref(false)
const convPage = ref(1)
const convTotal = ref(0)
const convSize = 20

let abortCtrl: AbortController | null = null

const nodeStatusColor: Record<string, string> = {
  success: 'success',
  skipped: 'info',
  error: 'danger'
}

const suggestions = [
  '帮我介绍一下这个平台能做什么',
  '如何创建一个智能体应用？',
  '怎么接入大模型并开始对话？'
]

/** 优先使用应用配置的推荐问题，否则回退默认建议 */
const displayedSuggestions = computed(() =>
  openingQuestions.value.length > 0 ? openingQuestions.value : suggestions
)

async function load() {
  const app = await appApi.get(appId)
  appName.value = app.name
  welcomeMessage.value = app.welcomeMessage || ''
  if (app.openingQuestions) {
    try {
      openingQuestions.value = JSON.parse(app.openingQuestions) as string[]
    } catch {
      openingQuestions.value = []
    }
  }
  if (app.type === 'agent') {
    mode.value = 'agent'
  } else if (app.workflowJson) {
    mode.value = 'workflow'
  }
  models.value = await modelApi.chatModels()
  if (models.value.length > 0 && modelId.value === null) {
    modelId.value = models.value[0].id
  }
}

/** 格式化时间：ISO 字符串 → YYYY-MM-DD HH:mm */
function formatTime(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 加载会话列表（当前应用，分页） */
async function loadConversations() {
  loadingConvs.value = true
  try {
    const data = await conversationApi.page({ appId, page: 1, size: convSize })
    conversations.value = data.records
    convTotal.value = data.total
    convPage.value = 1
  } finally {
    loadingConvs.value = false
  }
}

/** 加载更多历史会话 */
async function loadMoreConversations() {
  if (loadingConvs.value) return
  loadingConvs.value = true
  try {
    const data = await conversationApi.page({ appId, page: convPage.value + 1, size: convSize })
    conversations.value = conversations.value.concat(data.records)
    convTotal.value = data.total
    convPage.value += 1
  } finally {
    loadingConvs.value = false
  }
}

/** 新建会话（本地状态） */
function newConversation() {
  conversationId.value = null
  conversationTitle.value = ''
  messages.value = []
  traceVisible.value = false
}

/** 打开历史会话 */
async function openConversation(conv: ChatConversation) {
  conversationId.value = conv.id
  conversationTitle.value = conv.title || ''
  mode.value = conv.mode as ChatMode
  if (conv.modelId) {
    modelId.value = conv.modelId
  }
  const list = await conversationApi.messages(conv.id)
  messages.value = list.map((m) => {
    const base: ChatItem = { role: m.role, content: m.content || '' }
    if (m.traceJson) {
      try {
        const parsed = JSON.parse(m.traceJson)
        if (conv.mode === 'agent') {
          base.agentSteps = parsed as AgentStep[]
        } else {
          base.trace = parsed as TraceItem[]
        }
      } catch {
        /* 忽略损坏的轨迹数据 */
      }
    }
    return base
  })
  scrollToBottom()
}

/** 切换模式 = 新建会话 */
async function switchMode(m: ChatMode) {
  if (m === mode.value) return
  if (conversationId.value !== null && messages.value.length > 0) {
    try {
      await ElMessageBox.confirm(
        '切换对话模式将开启新会话，当前会话已保存在历史中，确定切换？',
        '提示',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  mode.value = m
  newConversation()
}

/** 重命名会话 */
async function renameConversation(conv: ChatConversation) {
  try {
    const { value } = await ElMessageBox.prompt('输入新的会话名称', '重命名会话', {
      inputValue: conv.title || '',
      inputValidator: (v: string) => (v && v.trim() ? true : '名称不能为空'),
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    await conversationApi.rename(conv.id, value.trim())
    conv.title = value.trim()
    if (conversationId.value === conv.id) {
      conversationTitle.value = value.trim()
    }
    ElMessage.success('已重命名')
  } catch {
    /* 用户取消 */
  }
}

/** 删除会话 */
async function removeConversation(conv: ChatConversation) {
  try {
    await ElMessageBox.confirm(
      `确认删除会话「${conv.title || '未命名会话'}」？该操作不可恢复。`,
      '删除确认',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await conversationApi.remove(conv.id)
  conversations.value = conversations.value.filter((c) => c.id !== conv.id)
  if (conversationId.value === conv.id) {
    newConversation()
  }
  ElMessage.success('会话已删除')
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

/** 发送前确保会话已创建（首条消息作为标题） */
async function ensureConversation(text: string) {
  if (conversationId.value !== null) return
  const conv = await conversationApi.create({
    appId,
    title: text.length > 20 ? text.slice(0, 20) + '…' : text,
    mode: mode.value,
    modelId: mode.value === 'direct' || mode.value === 'agent' ? modelId.value : null
  })
  conversationId.value = conv.id
  conversationTitle.value = conv.title || ''
  conversations.value.unshift(conv)
}

function sendWith(text: string) {
  input.value = text
  send()
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  if ((mode.value === 'direct' || mode.value === 'agent') && !modelId.value) {
    ElMessage.warning('请先在模型管理中配置并启用一个对话模型')
    return
  }
  try {
    await ensureConversation(text)
  } catch {
    return
  }
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  const assistant: ChatItem = { role: 'assistant', content: '', loading: true }
  messages.value.push(assistant)
  sending.value = true
  scrollToBottom()

  try {
    if (mode.value === 'direct') {
      // SSE 流式：后端持久化用户消息与完整回答
      abortCtrl = new AbortController()
      const full = await conversationApi.streamMessage(
        conversationId.value!,
        { content: text, modelId: modelId.value },
        (chunk) => {
          assistant.content += chunk.delta || ''
          scrollToBottom()
        },
        abortCtrl.signal
      )
      assistant.content = full || assistant.content
      assistant.loading = false
    } else {
      // 工作流 / 智能体：非流式，返回节点执行轨迹或工具调用步骤
      const result = await conversationApi.send(conversationId.value!, { content: text })
      assistant.content = result.content || '（无输出）'
      if (result.traceJson) {
        try {
          const parsed = JSON.parse(result.traceJson)
          if (mode.value === 'agent') {
            assistant.agentSteps = parsed as AgentStep[]
          } else {
            assistant.trace = parsed as TraceItem[]
          }
          traceVisible.value = true
        } catch {
          /* 忽略损坏的轨迹数据 */
        }
      }
      assistant.loading = false
    }
  } catch (err: any) {
    if (err?.name === 'AbortError') {
      assistant.content = assistant.content || '（已停止）'
    } else {
      assistant.content = assistant.content || '请求失败，请检查模型配置或网络'
      assistant.error = true
    }
    assistant.loading = false
  } finally {
    sending.value = false
    abortCtrl = null
    scrollToBottom()
  }
}

function stop() {
  abortCtrl?.abort()
}

onMounted(async () => {
  await load()
  await loadConversations()
  const cid = Number(route.query.conversationId)
  if (cid) {
    const conv =
      conversations.value.find((c) => c.id === cid) || (await conversationApi.get(cid).catch(() => null))
    if (conv) {
      await openConversation(conv)
    }
  }
})
</script>

<template>
  <div class="chat-page">
    <!-- 头部 -->
    <div class="chat-header">
      <el-button link @click="router.push('/apps')">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <div class="title">
        <span class="app-name">{{ appName }}</span>
        <el-tag size="small" effect="dark" class="mode-tag">对话</el-tag>
        <el-tag v-if="conversationId" size="small" type="info" effect="plain" class="conv-title-tag">
          {{ conversationTitle || '未命名会话' }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-radio-group :model-value="mode" size="small" @update:model-value="switchMode">
          <el-radio-button value="direct">直连模型</el-radio-button>
          <el-radio-button value="workflow">运行工作流</el-radio-button>
          <el-radio-button value="agent">智能体</el-radio-button>
        </el-radio-group>
        <el-select
          v-if="mode === 'direct' || mode === 'agent'"
          v-model="modelId"
          size="small"
          placeholder="选择模型"
          style="width: 200px"
        >
          <el-option
            v-for="m in models"
            :key="m.id"
            :label="`${m.providerName} / ${m.modelName}`"
            :value="m.id"
          />
        </el-select>
        <el-button size="small" class="btn-gradient" @click="newConversation">新建会话</el-button>
      </div>
    </div>

    <div class="chat-body">
      <!-- 会话历史侧栏 -->
      <aside class="sidebar">
        <div class="sidebar-head">
          <el-icon :size="14"><ChatDotRound /></el-icon>
          <span>会话历史</span>
        </div>
        <el-scrollbar v-loading="loadingConvs" class="sidebar-scroll">
          <div
            v-for="c in conversations"
            :key="c.id"
            class="conv-item"
            :class="{ active: c.id === conversationId }"
            @click="openConversation(c)"
            @dblclick.stop="renameConversation(c)"
          >
            <div class="conv-title">{{ c.title || '未命名会话' }}</div>
            <div class="conv-meta">
              <el-tag
                size="small"
                :type="c.mode === 'workflow' ? 'warning' : c.mode === 'agent' ? 'success' : 'primary'"
                effect="plain"
              >
                {{ c.mode === 'workflow' ? '工作流' : c.mode === 'agent' ? '智能体' : '直连' }}
              </el-tag>
              <span class="conv-time">{{ formatTime(c.updateTime) }}</span>
              <span class="conv-ops">
                <el-icon class="conv-del conv-edit" @click.stop="renameConversation(c)"><EditPen /></el-icon>
                <el-icon class="conv-del" @click.stop="removeConversation(c)"><Delete /></el-icon>
              </span>
            </div>
          </div>
          <el-empty
            v-if="!loadingConvs && conversations.length === 0"
            description="暂无会话"
            :image-size="60"
          />
          <div v-if="conversations.length < convTotal" class="conv-more">
            <el-button link type="primary" :loading="loadingConvs" @click="loadMoreConversations">
              加载更多
            </el-button>
          </div>
        </el-scrollbar>
      </aside>

      <!-- 消息区 -->
      <div ref="listRef" class="message-list">
        <div v-if="messages.length === 0" class="welcome-empty">
          <div class="welcome-orb orb-a"></div>
          <div class="welcome-orb orb-b"></div>
          <div class="welcome-icon">
            <el-icon :size="30"><Promotion /></el-icon>
          </div>
          <h2 class="welcome-title">{{ welcomeMessage || `开始与 ${appName} 对话` }}</h2>
          <p class="welcome-sub">
            {{
              mode === 'workflow'
                ? '将按画布编排的工作流执行，历史会话已持久化'
                : mode === 'agent'
                  ? '将自主规划并调用工具完成任务，历史会话已持久化'
                  : '内容实时流式返回并自动保存'
            }}
          </p>
          <div class="suggestion-list">
            <div
              v-for="s in displayedSuggestions"
              :key="s"
              class="suggestion-chip"
              @click="sendWith(s)"
            >
              {{ s }}
              <el-icon class="suggest-arrow"><ArrowLeft /></el-icon>
            </div>
          </div>
        </div>

        <template v-for="(msg, idx) in messages" :key="idx">
          <div v-if="msg.role === 'user'" class="msg user">
            <div class="bubble user-bubble">{{ msg.content }}</div>
            <div class="avatar user-avatar"><el-icon><User /></el-icon></div>
          </div>
          <div v-else class="msg assistant">
            <div class="avatar ai-avatar"><el-icon><Promotion /></el-icon></div>
            <div class="ai-wrap">
              <div class="bubble ai-bubble" :class="{ error: msg.error }">
                <MarkdownContent v-if="msg.content" :content="msg.content" class="ai-md" />
                <span v-else-if="msg.loading" class="typing"><i /><i /><i /></span>
                <span v-if="msg.loading && msg.content" class="cursor" />
              </div>
              <!-- 工作流执行轨迹 -->
              <el-collapse v-if="msg.trace && msg.trace.length" v-model="traceVisible" class="trace-collapse">
                <el-collapse-item title="节点执行轨迹" name="trace">
                  <div v-for="(t, i) in msg.trace" :key="i" class="trace-item">
                    <div class="trace-head">
                      <el-tag size="small" :type="(nodeStatusColor[t.status] as any) || 'info'">
                        {{ t.status }}
                      </el-tag>
                      <span class="trace-label">{{ t.label }}</span>
                      <el-tag size="small" type="info" effect="plain">{{ t.nodeType }}</el-tag>
                      <span class="trace-cost">{{ t.costMs }}ms</span>
                    </div>
                    <div v-if="t.error" class="trace-error">{{ t.error }}</div>
                    <div v-if="t.input" class="trace-text">
                      <span class="trace-key">输入</span>{{ t.input }}
                    </div>
                    <div v-if="t.output" class="trace-text">
                      <span class="trace-key">输出</span>{{ t.output }}
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
              <!-- 智能体工具调用轨迹 -->
              <el-collapse
                v-if="msg.agentSteps && msg.agentSteps.length"
                v-model="traceVisible"
                class="trace-collapse"
              >
                <el-collapse-item title="工具调用轨迹" name="trace">
                  <div v-for="(s, i) in msg.agentSteps" :key="i" class="trace-item">
                    <div class="trace-head">
                      <el-tag size="small" type="success">调用</el-tag>
                      <span class="trace-label">{{ s.toolName }}</span>
                      <span class="trace-cost">{{ s.costMs }}ms</span>
                    </div>
                    <div v-if="s.arguments" class="trace-text">
                      <span class="trace-key">参数</span>{{ s.arguments }}
                    </div>
                    <div v-if="s.result" class="trace-text">
                      <span class="trace-key">结果</span>{{ s.result }}
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-bar">
      <div class="input-shell">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="send"
        />
        <div class="input-actions">
          <el-button v-if="sending" class="stop-btn" @click="stop">
            <span class="stop-square"></span>停止生成
          </el-button>
          <el-button
            v-else
            type="primary"
            class="send-btn btn-gradient"
            :disabled="!input.trim()"
            @click="send"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}

/* ---------- 头部 ---------- */
.chat-header {
  height: 56px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  flex-shrink: 0;
  z-index: 5;
}
.title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.app-name {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mode-tag {
  background: var(--brand-gradient);
  border: none;
}
.conv-title-tag {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.header-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}

/* ---------- 主体 ---------- */
.chat-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 会话侧栏 */
.sidebar {
  width: 250px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.9);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}
.sidebar-head {
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.sidebar-scroll {
  flex: 1;
  min-height: 0;
}
.conv-more {
  display: flex;
  justify-content: center;
  padding: 6px 0 12px;
}

.conv-item {
  margin: 4px 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}
.conv-item:hover {
  background: var(--bg-page);
}
.conv-item.active {
  background: var(--brand-gradient-soft);
  border-color: rgba(91, 108, 255, 0.3);
}
.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-meta {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.conv-time {
  font-size: 11px;
  color: var(--text-tertiary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-del {
  color: var(--text-tertiary);
  font-size: 14px;
  opacity: 0;
  transition: all 0.18s ease;
}
.conv-item:hover .conv-del {
  opacity: 1;
}
.conv-ops {
  display: flex;
  align-items: center;
  gap: 4px;
}
.conv-del:hover {
  color: #ef4444;
}
.conv-edit:hover {
  color: var(--brand-1, #5b6cff);
}

/* 消息区 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  background-image: radial-gradient(rgba(91, 108, 255, 0.05) 1px, transparent 1px);
  background-size: 22px 22px;
}

/* 欢迎空状态 */
.welcome-empty {
  position: relative;
  height: 100%;
  min-height: 380px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.welcome-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.4;
  animation: float 8s ease-in-out infinite;
}
.orb-a {
  width: 260px;
  height: 260px;
  background: rgba(91, 108, 255, 0.5);
  top: 6%;
  left: 20%;
}
.orb-b {
  width: 220px;
  height: 220px;
  background: rgba(139, 92, 246, 0.5);
  bottom: 8%;
  right: 18%;
  animation-delay: 3s;
}
@keyframes float {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-16px) scale(1.05);
  }
}
.welcome-icon {
  position: relative;
  z-index: 1;
  width: 72px;
  height: 72px;
  border-radius: 22px;
  background: var(--brand-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 28px rgba(91, 108, 255, 0.4);
  margin-bottom: 20px;
}
.welcome-title {
  position: relative;
  z-index: 1;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.welcome-sub {
  position: relative;
  z-index: 1;
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-tertiary);
}
.suggestion-list {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 10px;
  margin-top: 28px;
  flex-wrap: wrap;
  justify-content: center;
}
.suggestion-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--border-color);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: var(--shadow-card);
}
.suggestion-chip:hover {
  border-color: var(--brand-1);
  color: var(--brand-1);
  transform: translateY(-2px);
  box-shadow: var(--shadow-card-hover);
}
.suggest-arrow {
  font-size: 12px;
  transform: rotate(180deg);
  transition: transform 0.2s ease;
}
.suggestion-chip:hover .suggest-arrow {
  transform: rotate(180deg) translateX(3px);
}

/* 消息 */
.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  animation: msg-in 0.3s ease both;
}
@keyframes msg-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.msg.user {
  justify-content: flex-end;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
.user-avatar {
  background: var(--brand-gradient);
  color: #fff;
}
.ai-avatar {
  background: linear-gradient(135deg, #0ea5e9, #8b5cf6);
  color: #fff;
}
.ai-wrap {
  max-width: 72%;
}
.bubble {
  max-width: 100%;
  padding: 11px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.user-bubble {
  background: var(--brand-gradient);
  color: #fff;
  border-top-right-radius: 4px;
  box-shadow: 0 4px 12px rgba(91, 108, 255, 0.3);
}
.ai-bubble {
  background: #fff;
  border: 1px solid var(--border-color);
  border-top-left-radius: 4px;
  box-shadow: var(--shadow-card);
}
.ai-md {
  white-space: normal;
}
.ai-bubble.error {
  color: #ef4444;
  background: #fef2f2;
  border-color: #fecaca;
}
.typing {
  display: inline-flex;
  gap: 4px;
  padding: 4px 2px;
}
.typing i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand-1);
  animation: blink 1.2s infinite;
}
.typing i:nth-child(2) {
  animation-delay: 0.2s;
}
.typing i:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%, 80%, 100% {
    opacity: 0.25;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}
.cursor {
  display: inline-block;
  width: 2px;
  height: 15px;
  margin-left: 2px;
  background: var(--brand-1);
  vertical-align: -2px;
  animation: caret 0.9s step-end infinite;
}
@keyframes caret {
  50% {
    opacity: 0;
  }
}

/* ---------- 输入区 ---------- */
.input-bar {
  background: rgba(255, 255, 255, 0.9);
  border-top: 1px solid var(--border-color);
  padding: 14px 20px 16px;
  flex-shrink: 0;
}
.input-shell {
  max-width: 860px;
  margin: 0 auto;
  background: #fff;
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 10px 12px 10px 16px;
  transition: all 0.25s ease;
  box-shadow: var(--shadow-card);
}
.input-shell:focus-within {
  border-color: var(--brand-1);
  box-shadow: 0 0 0 4px rgba(91, 108, 255, 0.1), var(--shadow-card-hover);
}
.input-shell :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none !important;
  padding: 0;
  font-size: 14px;
  line-height: 1.6;
}
.input-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
.send-btn {
  border-radius: 10px;
  height: 34px;
  min-width: 84px;
}
.send-btn:disabled {
  background: #c8cdf5 !important;
  box-shadow: none;
  transform: none;
}
.stop-btn {
  border-radius: 10px;
  height: 34px;
  color: #ef4444;
  border-color: #fecaca;
}
.stop-btn:hover {
  background: #fef2f2;
}
.stop-square {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  background: currentColor;
  display: inline-block;
  margin-right: 6px;
}

/* ---------- 轨迹 ---------- */
.trace-collapse {
  margin-top: 10px;
  border: none;
  background: transparent;
}
.trace-item {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 10px;
  margin-bottom: 8px;
  background: #fafbfe;
}
.trace-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.trace-label {
  font-size: 13px;
  font-weight: 600;
}
.trace-cost {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-tertiary);
}
.trace-error {
  margin-top: 6px;
  font-size: 12px;
  color: #ef4444;
}
.trace-text {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}
.trace-key {
  display: inline-block;
  margin-right: 6px;
  padding: 0 5px;
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  border-radius: 4px;
  font-size: 11px;
}
</style>
