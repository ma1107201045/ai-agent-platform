<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ArrowLeft, Promotion, User } from '@element-plus/icons-vue'
import type { AgentStep, TraceItem } from '@/api/types'
import MarkdownContent from '@/components/MarkdownContent.vue'

export interface ChatItem {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  error?: boolean
  trace?: TraceItem[]
  agentSteps?: AgentStep[]
  /** 本条消息的轨迹面板是否展开（每条消息独立，避免一条展开带动全部） */
  traceOpen?: boolean
}

/** 对话模式：direct 直连模型 / workflow 运行应用工作流 / agent 智能体（工具调用） */
export type ChatMode = 'direct' | 'workflow' | 'agent'

defineProps<{
  messages: ChatItem[]
  appName: string
  welcomeMessage: string
  mode: ChatMode
  suggestions: string[]
}>()

const emit = defineEmits<{ sendWith: [text: string] }>()

const nodeStatusColor: Record<string, string> = {
  success: 'success',
  skipped: 'info',
  error: 'danger'
}

const listRef = ref<HTMLElement | null>(null)

/** 流式输出时是否自动跟随到底部（用户上翻回看时暂停，滚回底部后恢复） */
const autoFollow = ref(true)

function onScroll() {
  const el = listRef.value
  if (!el) return
  autoFollow.value = el.scrollHeight - el.scrollTop - el.clientHeight < 48
}

/** 滚动到底部；force 用于发送新消息/打开会话等场景，否则仅在用户未上翻时跟随 */
function scrollToBottom(force = false) {
  if (!force && !autoFollow.value) return
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

defineExpose({ scrollToBottom })
</script>

<template>
  <div ref="listRef" class="message-list" @scroll.passive="onScroll">
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
        <div v-for="s in suggestions" :key="s" class="suggestion-chip" @click="emit('sendWith', s)">
          {{ s }}
          <el-icon class="suggest-arrow"><ArrowLeft /></el-icon>
        </div>
      </div>
    </div>

    <template v-for="(msg, idx) in messages" :key="idx">
      <div v-if="msg.role === 'user'" class="msg user">
        <div class="bubble user-bubble">{{ msg.content }}</div>
        <div class="avatar user-avatar">
          <el-icon><User /></el-icon>
        </div>
      </div>
      <div v-else class="msg assistant">
        <div class="avatar ai-avatar">
          <el-icon><Promotion /></el-icon>
        </div>
        <div class="ai-wrap">
          <div class="bubble ai-bubble" :class="{ error: msg.error }">
            <MarkdownContent v-if="msg.content" :content="msg.content" class="ai-md" />
            <span v-else-if="msg.loading" class="typing"><i /><i /><i /></span>
            <span v-if="msg.loading && msg.content" class="cursor" />
          </div>
          <!-- 工作流执行轨迹（每条消息独立展开状态） -->
          <el-collapse
            v-if="msg.trace && msg.trace.length"
            :model-value="msg.traceOpen ? ['trace'] : []"
            class="trace-collapse"
            @update:model-value="(v: string[]) => (msg.traceOpen = v.includes('trace'))"
          >
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
          <!-- 智能体工具调用轨迹（每条消息独立展开状态） -->
          <el-collapse
            v-if="msg.agentSteps && msg.agentSteps.length"
            :model-value="msg.traceOpen ? ['trace'] : []"
            class="trace-collapse"
            @update:model-value="(v: string[]) => (msg.traceOpen = v.includes('trace'))"
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
</template>

<style scoped>
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
  background: var(--bg-card);
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
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-top-left-radius: 4px;
  box-shadow: var(--shadow-card);
}
.ai-md {
  white-space: normal;
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
  0%,
  80%,
  100% {
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
</style>
