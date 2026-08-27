<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Delete, Promotion, User } from '@element-plus/icons-vue'
import { appApi } from '@/api/app'
import { llmApi } from '@/api/llm'
import type { ChatMessage, ChatModelInfo } from '@/api/types'

const route = useRoute()
const router = useRouter()
const appId = Number(route.params.id)

interface ChatItem {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  error?: boolean
}

const appName = ref('')
const models = ref<ChatModelInfo[]>([])
const modelId = ref<number | null>(null)
const messages = ref<ChatItem[]>([])
const input = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement | null>(null)
let abortCtrl: AbortController | null = null

async function load() {
  const app = await appApi.get(appId)
  appName.value = app.name
  models.value = await llmApi.chatModels()
  if (models.value.length > 0 && modelId.value === null) {
    modelId.value = models.value[0].id
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

function buildMessages(): ChatMessage[] {
  const list: ChatMessage[] = []
  for (const m of messages.value) {
    if (m.role === 'assistant' && (m.loading || m.error)) continue
    list.push({ role: m.role, content: m.content })
  }
  return list
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  if (!modelId.value) {
    ElMessage.warning('请先在模型管理中配置并启用一个对话模型')
    return
  }
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  const assistant: ChatItem = { role: 'assistant', content: '', loading: true }
  messages.value.push(assistant)
  sending.value = true
  scrollToBottom()

  abortCtrl = new AbortController()
  try {
    const full = await llmApi.chatStream(
      { modelId: modelId.value, messages: buildMessages() },
      (chunk) => {
        assistant.content += chunk.content || ''
        scrollToBottom()
      },
      abortCtrl.signal
    )
    assistant.content = full
    assistant.loading = false
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

function clearChat() {
  messages.value = []
}

onMounted(load)
</script>

<template>
  <div class="chat-page">
    <!-- 头部 -->
    <div class="chat-header">
      <el-button link @click="router.push('/apps')">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <span class="title">{{ appName }} <el-tag size="small" type="success">对话调试</el-tag></span>
      <div class="header-right">
        <el-select v-model="modelId" size="small" placeholder="选择模型" style="width: 220px">
          <el-option
            v-for="m in models"
            :key="m.id"
            :label="`${m.providerName} / ${m.modelName}`"
            :value="m.id"
          />
        </el-select>
        <el-button size="small" :icon="Delete" @click="clearChat">清空会话</el-button>
      </div>
    </div>

    <!-- 消息区 -->
    <div ref="listRef" class="message-list">
      <el-empty
        v-if="messages.length === 0"
        description="开始对话吧，内容将实时流式返回"
        :image-size="90"
        class="empty"
      />
      <template v-for="(msg, idx) in messages" :key="idx">
        <div v-if="msg.role === 'user'" class="msg user">
          <div class="bubble user-bubble">{{ msg.content }}</div>
          <div class="avatar user-avatar"><el-icon><User /></el-icon></div>
        </div>
        <div v-else class="msg assistant">
          <div class="avatar ai-avatar"><el-icon><Promotion /></el-icon></div>
          <div class="bubble ai-bubble" :class="{ error: msg.error }">
            <template v-if="msg.content">{{ msg.content }}</template>
            <span v-else-if="msg.loading" class="typing"><i /><i /><i /></span>
            <span v-if="msg.loading && msg.content" class="cursor" />
          </div>
        </div>
      </template>
    </div>

    <!-- 输入区 -->
    <div class="input-bar">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        @keydown.enter.exact.prevent="send"
      />
      <div class="input-actions">
        <el-button v-if="sending" @click="stop">停止</el-button>
        <el-button v-else type="primary" :disabled="!input.trim()" @click="send">发送</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}
.chat-header {
  height: 50px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
}
.title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}
.empty {
  margin-top: 15%;
}
.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
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
}
.user-avatar {
  background: #409eff;
  color: #fff;
}
.ai-avatar {
  background: #67c23a;
  color: #fff;
}
.bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.user-bubble {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}
.ai-bubble {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-top-left-radius: 2px;
}
.ai-bubble.error {
  color: #f56c6c;
  background: #fef0f0;
}
.typing {
  display: inline-flex;
  gap: 4px;
  padding: 4px 2px;
}
.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
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
    opacity: 0.3;
  }
  40% {
    opacity: 1;
  }
}
.cursor {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  background: #409eff;
  vertical-align: -2px;
  animation: pulse 1s step-end infinite;
}
@keyframes pulse {
  50% {
    opacity: 0;
  }
}
.input-bar {
  background: #fff;
  border-top: 1px solid #e4e7ed;
  padding: 10px 16px;
}
.input-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
