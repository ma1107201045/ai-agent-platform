<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { appAgentApi } from '@/api/app-agent'
import { conversationApi } from '@/api/chat-conversation'
import { modelApi } from '@/api/model'
import type { AgentStep, ChatConversation, ChatModelInfo, TraceItem } from '@/api/types'
import ConversationSidebar from './components/ConversationSidebar.vue'
import MessageList from './components/MessageList.vue'
import type { ChatItem, ChatMode } from './components/MessageList.vue'
import ChatInputBar from './components/ChatInputBar.vue'

const route = useRoute()
const router = useRouter()
const appId = Number(route.params.id)

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
const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)

/** 当前会话（null 表示尚未创建的新会话） */
const conversationId = ref<number | null>(null)
const conversationTitle = ref('')
const conversations = ref<ChatConversation[]>([])
const loadingConvs = ref(false)
const convPage = ref(1)
const convTotal = ref(0)
const convSize = 20

let abortCtrl: AbortController | null = null

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
  const app = await appAgentApi.get(appId)
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
  messageListRef.value?.scrollToBottom()
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
  // 用 reactive 创建 assistant：SSE 回调中直接改其属性可触发视图增量渲染
  const assistant = reactive<ChatItem>({ role: 'assistant', content: '', loading: true })
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
      conversations.value.find((c) => c.id === cid) ||
      (await conversationApi.get(cid).catch(() => null))
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
      <el-button link @click="router.push('/app-agents')">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <div class="title">
        <span class="app-name">{{ appName }}</span>
        <el-tag size="small" effect="dark" class="mode-tag">对话</el-tag>
        <el-tag
          v-if="conversationId"
          size="small"
          type="info"
          effect="plain"
          class="conv-title-tag"
        >
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
      <ConversationSidebar
        :conversations="conversations"
        :loading="loadingConvs"
        :active-id="conversationId"
        :total="convTotal"
        @open="openConversation"
        @rename="renameConversation"
        @remove="removeConversation"
        @load-more="loadMoreConversations"
      />

      <!-- 消息区 -->
      <MessageList
        ref="messageListRef"
        v-model:trace-visible="traceVisible"
        :messages="messages"
        :app-name="appName"
        :welcome-message="welcomeMessage"
        :mode="mode"
        :suggestions="displayedSuggestions"
        @send-with="sendWith"
      />
    </div>

    <!-- 输入区 -->
    <ChatInputBar v-model="input" :sending="sending" @send="send" @stop="stop" />
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
  background: var(--bg-header);
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
</style>
