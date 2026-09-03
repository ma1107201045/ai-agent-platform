<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Promotion, User } from '@element-plus/icons-vue'
import { publicApi } from '@/api/portal-public.ts'
import type { PublicAppInfo, PublicChatResult } from '@/api/portal-public.ts'
import type { AgentStep, TraceItem } from '@/api/types'

const route = useRoute()
const appId = Number(route.params.id)

interface ChatItem {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  error?: boolean
  trace?: TraceItem[]
  agentSteps?: AgentStep[]
}

const appInfo = ref<PublicAppInfo | null>(null)
const loaded = ref(false)
const loadError = ref('')
const messages = ref<ChatItem[]>([])
const input = ref('')
const sending = ref(false)
const traceVisible = ref(false)
const listRef = ref<HTMLElement | null>(null)

const nodeStatusColor: Record<string, string> = {
  success: 'success',
  skipped: 'info',
  error: 'danger'
}

const suggestions = ['你好，介绍一下你的能力', '你能帮我做什么？']

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

function sendWith(text: string) {
  input.value = text
  send()
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  const assistant: ChatItem = { role: 'assistant', content: '', loading: true }
  messages.value.push(assistant)
  sending.value = true
  scrollToBottom()

  try {
    const history = messages.value
      .filter((m) => !m.loading && !m.error)
      .map((m) => ({ role: m.role, content: m.content }))
    const result: PublicChatResult = await publicApi.chat(appId, history)
    assistant.content = result.answer || '（无输出）'
    if (result.detail) {
      if (appInfo.value?.type === 'agent') {
        assistant.agentSteps = result.detail as AgentStep[]
      } else {
        assistant.trace = result.detail as TraceItem[]
      }
      traceVisible.value = true
    }
  } catch (err: any) {
    assistant.content = err?.message || '请求失败，请稍后重试'
    assistant.error = true
  } finally {
    assistant.loading = false
    sending.value = false
    scrollToBottom()
  }
}

onMounted(async () => {
  try {
    appInfo.value = await publicApi.getApp(appId)
    if (appInfo.value.welcomeMessage) {
      messages.value.push({ role: 'assistant', content: appInfo.value.welcomeMessage })
    }
  } catch (err: any) {
    loadError.value = err?.message || '应用不存在或未发布'
  } finally {
    loaded.value = true
  }
})
</script>

<template>
  <div class="pub-page">
    <div class="pub-header">
      <div class="pub-logo">A</div>
      <div class="pub-title">
        <div class="pub-name">{{ appInfo?.name || '智能体' }}</div>
        <div class="pub-sub">{{ appInfo?.description || '已发布应用 · 对外对话' }}</div>
      </div>
    </div>

    <div class="pub-body">
      <div v-if="!loaded" class="pub-loading">加载中…</div>
      <div v-else-if="loadError" class="pub-error">
        <el-empty :description="loadError" :image-size="80" />
      </div>
      <template v-else>
        <div ref="listRef" class="pub-list">
          <div v-if="messages.length === 0" class="pub-welcome">
            <div class="welcome-icon"><el-icon :size="26"><Promotion /></el-icon></div>
            <h2>开始与「{{ appInfo?.name }}」对话</h2>
            <p>无需登录，点击下方问题或直接输入</p>
            <div class="pub-suggestions">
              <div v-for="s in suggestions" :key="s" class="pub-chip" @click="sendWith(s)">{{ s }}</div>
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
                  <template v-if="msg.content">{{ msg.content }}</template>
                  <span v-else-if="msg.loading" class="typing"><i /><i /><i /></span>
                </div>
                <!-- 工作流轨迹 -->
                <el-collapse v-if="msg.trace && msg.trace.length" v-model="traceVisible" class="trace-collapse">
                  <el-collapse-item title="执行轨迹" name="trace">
                    <div v-for="(t, i) in msg.trace" :key="i" class="trace-item">
                      <div class="trace-head">
                        <el-tag size="small" :type="(nodeStatusColor[t.status] as any) || 'info'">
                          {{ t.status }}
                        </el-tag>
                        <span class="trace-label">{{ t.label }}</span>
                        <span class="trace-cost">{{ t.costMs }}ms</span>
                      </div>
                      <div v-if="t.error" class="trace-error">{{ t.error }}</div>
                      <div v-if="t.input" class="trace-text"><span class="trace-key">输入</span>{{ t.input }}</div>
                      <div v-if="t.output" class="trace-text"><span class="trace-key">输出</span>{{ t.output }}</div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
                <!-- 工具调用轨迹 -->
                <el-collapse v-if="msg.agentSteps && msg.agentSteps.length" v-model="traceVisible" class="trace-collapse">
                  <el-collapse-item title="工具调用" name="trace">
                    <div v-for="(s, i) in msg.agentSteps" :key="i" class="trace-item">
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
            </div>
          </template>
        </div>

        <div class="pub-input-bar">
          <div class="pub-input-shell">
            <el-input
              v-model="input"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="send"
            />
            <div class="input-actions">
              <el-button type="primary" class="send-btn" :disabled="!input.trim() || sending" @click="send">
                {{ sending ? '思考中…' : '发送' }}
              </el-button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.pub-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}

.pub-header {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
}
.pub-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(91, 108, 255, 0.4);
}
.pub-name {
  font-size: 15px;
  font-weight: 600;
}
.pub-sub {
  font-size: 12px;
  color: var(--text-tertiary);
}

.pub-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.pub-loading,
.pub-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.pub-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
}
.pub-welcome {
  text-align: center;
  padding: 60px 0 30px;
}
.welcome-icon {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: var(--brand-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  box-shadow: 0 10px 28px rgba(91, 108, 255, 0.4);
}
.pub-welcome h2 {
  font-size: 19px;
  margin: 0 0 6px;
}
.pub-welcome p {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0 0 20px;
}
.pub-suggestions {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}
.pub-chip {
  padding: 8px 16px;
  border-radius: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}
.pub-chip:hover {
  border-color: var(--brand-1);
  color: var(--brand-1);
  transform: translateY(-2px);
}

.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
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
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
.user-avatar {
  background: var(--brand-gradient);
}
.ai-avatar {
  background: linear-gradient(135deg, #0ea5e9, #8b5cf6);
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
}
.ai-bubble {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-top-left-radius: 4px;
}
.ai-bubble.error {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-8);
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
  0%, 80%, 100% { opacity: 0.25; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

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
  background: var(--fill-lighter);
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
  color: var(--el-color-danger);
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

.pub-input-bar {
  flex-shrink: 0;
  padding: 14px 20px 18px;
  background: var(--bg-header);
  border-top: 1px solid var(--border-color);
}
.pub-input-shell {
  max-width: 860px;
  margin: 0 auto;
  background: var(--bg-card);
  border: 1.5px solid var(--border-color);
  border-radius: 14px;
  padding: 10px 12px 10px 16px;
}
.pub-input-shell:focus-within {
  border-color: var(--brand-1);
  box-shadow: 0 0 0 4px rgba(91, 108, 255, 0.1);
}
.pub-input-shell :deep(.el-textarea__inner) {
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
  background: var(--brand-gradient);
  border: none;
}
</style>
