<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { modelApi } from '@/api/model'
import MarkdownContent from '@/components/MarkdownContent.vue'
import type { ChatMessage, ModelPlaygroundItem } from '@/api/types'

const router = useRouter()

const typeMeta: Record<string, { label: string; tag: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  llm: { label: '对话', tag: 'primary' },
  embedding: { label: '向量', tag: 'success' },
  rerank: { label: '重排', tag: 'warning' },
  image: { label: '图像', tag: 'info' },
  tts: { label: '语音合成', tag: 'danger' },
  asr: { label: '语音识别', tag: 'danger' }
}
const capabilityLabels: Record<string, string> = {
  function_call: '工具调用',
  vision: '视觉理解',
  stream: '流式输出',
  json: 'JSON 模式'
}

const models = ref<ModelPlaygroundItem[]>([])
const loading = ref(false)
const typeFilter = ref('llm')
const keyword = ref('')
const onlyUsable = ref(true)

const usable = (m: ModelPlaygroundItem) => m.modelStatus === 1 && m.providerStatus === 1

const typeOptions = computed(() => {
  const seen = new Set<string>()
  const counts: Record<string, number> = {}
  for (const m of models.value) {
    seen.add(m.modelType)
    counts[m.modelType] = (counts[m.modelType] || 0) + 1
  }
  const opts = [{ value: '', label: '全部', count: models.value.length }]
  for (const t of ['llm', 'embedding', 'rerank', 'image', 'tts', 'asr']) {
    if (seen.has(t)) opts.push({ value: t, label: typeMeta[t].label, count: counts[t] })
  }
  return opts
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return models.value.filter((m) => {
    if (typeFilter.value && m.modelType !== typeFilter.value) return false
    if (onlyUsable.value && !usable(m)) return false
    if (kw && !(m.modelName.toLowerCase().includes(kw) || m.providerName.toLowerCase().includes(kw))) return false
    return true
  })
})

const stats = computed(() => {
  const usableModels = models.value.filter(usable)
  return {
    total: models.value.length,
    usable: usableModels.length,
    chat: usableModels.filter((m) => m.modelType === 'llm').length,
    disabled: models.value.length - usableModels.length,
    providers: new Set(models.value.map((m) => m.providerName)).size
  }
})

async function load() {
  loading.value = true
  try {
    models.value = await modelApi.playgroundModels()
  } catch (e) {
    ElMessage.error('模型目录加载失败')
  } finally {
    loading.value = false
  }
}

const capLabel = (c: string) => capabilityLabels[c] || c
const toK = (n?: number) => (n ? `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k` : '—')

/* ---------------- 在线试玩 ---------------- */
interface Turn {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  error?: boolean
}

const drawerVisible = ref(false)
const activeModel = ref<ModelPlaygroundItem | null>(null)
const turns = ref<Turn[]>([])
const inputText = ref('')
const sending = ref(false)
const systemPrompt = ref('You are a helpful assistant.')
const temperature = ref(0.7)
let abortCtl: AbortController | null = null
let sessionMsgs: ChatMessage[] = []
let streamEndedByStop = false

const quickAsks = ['用一句话介绍你自己的能力', '帮我总结一下什么是大语言模型', '写一个冒泡排序的 Python 示例', '将这段话翻译成英文：你好，很高兴认识你']

function openChat(model: ModelPlaygroundItem) {
  activeModel.value = model
  turns.value = []
  sessionMsgs = []
  inputText.value = ''
  drawerVisible.value = true
}

function newSession() {
  abortCtl?.abort()
  turns.value = []
  sessionMsgs = []
  sending.value = false
  streamEndedByStop = false
}

function useQuick(text: string) {
  inputText.value = text
}

function scrollToBottom() {
  nextTick(() => {
    const box = document.querySelector('.pg-console-list')
    if (box) box.scrollTop = box.scrollHeight
  })
}

async function send() {
  const text = inputText.value.trim()
  if (!text || sending.value || !activeModel.value) return
  turns.value.push({ role: 'user', content: text })
  sessionMsgs.push({ role: 'user', content: text })
  inputText.value = ''
  sending.value = true
  streamEndedByStop = false
  turns.value.push({ role: 'assistant', content: '', streaming: true })
  abortCtl = new AbortController()
  const payloadMsgs: ChatMessage[] = [{ role: 'system', content: systemPrompt.value }, ...sessionMsgs]
  scrollToBottom()
  try {
    const full = await modelApi.chatStream(
      {
        modelId: activeModel.value.modelId,
        messages: payloadMsgs,
        temperature: temperature.value,
        maxTokens: undefined
      },
      (chunk) => {
        const last = turns.value[turns.value.length - 1]
        if (last && last.streaming && chunk.delta) {
          last.content += chunk.delta
          scrollToBottom()
        }
      },
      abortCtl.signal
    )
    const last = turns.value[turns.value.length - 1]
    if (last) {
      last.streaming = false
      last.content = full || last.content || '（模型未返回内容）'
    }
    if (full) sessionMsgs.push({ role: 'assistant', content: full })
    streamEndedByStop = false
  } catch (e: unknown) {
    const last = turns.value[turns.value.length - 1]
    if (last) {
      last.streaming = false
      last.error = true
      const stopped = (e as Error)?.name === 'AbortError' || streamEndedByStop
      last.content = stopped ? '（已停止生成）' : `调用失败：${(e as Error)?.message || String(e)}`
    }
  } finally {
    sending.value = false
    abortCtl = null
  }
  scrollToBottom()
}

function stopStream() {
  streamEndedByStop = true
  abortCtl?.abort()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

onBeforeUnmount(() => abortCtl?.abort())
onMounted(load)
</script>

<template>
  <div class="page-container playground-page">
    <div class="pg-head">
      <div>
        <h2 class="head-title">模型广场</h2>
        <p class="head-desc">浏览已接入供应商的模型能力，选中对话模型即可在线流式试玩，验证效果后再接入业务</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Plus" @click="router.push('/models')">配置供应商 / 模型</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="pg-stats">
      <div class="stat-card hover-card">
        <div class="stat-num">{{ stats.total }}</div>
        <div class="stat-label">目录模型</div>
      </div>
      <div class="stat-card hover-card">
        <div class="stat-num usable">{{ stats.usable }}</div>
        <div class="stat-label">当前可用</div>
      </div>
      <div class="stat-card hover-card">
        <div class="stat-num chat">{{ stats.chat }}</div>
        <div class="stat-label">可对话</div>
      </div>
      <div class="stat-card hover-card">
        <div class="stat-num muted">{{ stats.disabled }}</div>
        <div class="stat-label">暂不可用</div>
      </div>
      <div class="stat-card hover-card">
        <div class="stat-num">{{ stats.providers }}</div>
        <div class="stat-label">供应商</div>
      </div>
    </div>

    <div class="pg-toolbar hover-card">
      <el-radio-group v-model="typeFilter">
        <el-radio-button v-for="opt in typeOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}<span class="cnt">{{ opt.count }}</span>
        </el-radio-button>
      </el-radio-group>
      <div class="pg-toolbar-right">
        <el-switch v-model="onlyUsable" active-text="仅看可用" />
        <el-input v-model="keyword" placeholder="搜索模型 / 供应商" :prefix-icon="Search" clearable style="width: 210px" />
      </div>
    </div>

    <div v-loading="loading" class="pg-grid">
      <el-card
        v-for="m in filtered"
        :key="m.modelId"
        shadow="never"
        class="model-card hover-card"
        :class="{ 'is-unusable': !usable(m) }"
      >
        <div class="mc-top">
          <div class="mc-avatar" :class="`mc-avatar-${typeMeta[m.modelType]?.tag || 'info'}`">
            {{ (m.providerName || '?').slice(0, 1).toUpperCase() }}
          </div>
          <div class="mc-info">
            <div class="mc-name-row">
              <span class="mc-model">{{ m.modelName }}</span>
              <el-tag :type="typeMeta[m.modelType]?.tag || 'info'" size="small" effect="plain" round>
                {{ typeMeta[m.modelType]?.label || m.modelType }}
              </el-tag>
            </div>
            <div class="mc-provider">{{ m.providerName }}<span v-if="m.providerType" class="muted"> · {{ m.providerType }}</span></div>
          </div>
          <span v-if="m.modelType === 'llm' && usable(m)" class="mc-live">可用</span>
          <el-tag v-else-if="m.modelType === 'llm'" type="danger" size="small" effect="dark">未启用</el-tag>
          <el-tag v-else-if="!usable(m)" size="small" type="info" effect="dark">供应商停用</el-tag>
        </div>

        <div class="mc-meta">
          <span class="chip muted">上下文 {{ toK(m.contextWindow) }}</span>
          <span class="chip muted">最大输出 {{ toK(m.maxTokens) }}</span>
        </div>
        <div v-if="m.capabilities?.length" class="mc-caps">
          <span v-for="c in m.capabilities" :key="c" class="cap-chip">{{ capLabel(c) }}</span>
        </div>

        <div class="mc-foot">
          <template v-if="m.modelType === 'llm'">
            <el-button
              type="primary"
              :disabled="!usable(m)"
              :icon="ChatDotRound"
              @click="openChat(m)"
            >在线试玩</el-button>
            <span v-if="!usable(m)" class="muted small">启用模型与其供应商后即可试玩</span>
          </template>
          <span v-else class="muted small">
            该模型用于{{ typeMeta[m.modelType]?.label || m.modelType }}任务，不支持对话试玩
          </span>
        </div>
      </el-card>
    </div>

    <el-empty
      v-if="!loading && !filtered.length"
      description="没有匹配的模型，请先在「供应商管理」中接入模型，或在广场中调整筛选"
    />

    <el-drawer
      v-model="drawerVisible"
      :size="'min(860px, 92vw)'"
      :with-header="false"
      destroy-on-close
      class="pg-drawer"
    >
      <template v-if="activeModel">
        <div class="pg-console-head">
          <div>
            <div class="pg-console-title">
              {{ activeModel.modelName }}
              <el-tag size="small" :type="typeMeta[activeModel.modelType]?.tag || 'info'" round>
                {{ activeModel.providerName }}
              </el-tag>
            </div>
            <div class="pg-console-sub muted">
              流式在线试玩 · 上下文 {{ toK(activeModel.contextWindow) }} · 支持多轮对话，发送内容会保存在当前会话
            </div>
          </div>
          <div>
            <el-button size="small" :icon="Plus" @click="newSession">新会话</el-button>
            <el-button size="small" @click="drawerVisible = false">关闭</el-button>
          </div>
        </div>

        <div class="pg-console-list">
          <div v-if="!turns.length" class="pg-console-empty">
            <div class="pg-console-empty-title">开始第一轮对话</div>
            <div class="pg-quick-wrap">
              <el-button
                v-for="q in quickAsks"
                :key="q"
                size="small"
                round
                plain
                @click="useQuick(q)"
              >{{ q }}</el-button>
            </div>
          </div>
          <div v-for="(t, i) in turns" :key="i" class="pg-turn" :class="t.role">
            <div class="pg-turn-bubble">
              <template v-if="t.role === 'user'">{{ t.content }}</template>
              <template v-else>
                <MarkdownContent v-if="t.content" :content="t.content" />
                <span v-else class="muted typing"><i />思考中…</span>
                <span v-if="t.streaming" class="typing-tail"><i /></span>
              </template>
            </div>
          </div>
        </div>

        <div class="pg-console-input">
          <div class="pg-params">
            <span class="muted small">系统提示词</span>
            <el-input
              v-model="systemPrompt"
              size="small"
              placeholder="为该会话设定角色 / 规则（可选）"
              clearable
            />
            <span class="muted small">温度 {{ temperature.toFixed(1) }}</span>
            <el-slider v-model="temperature" :min="0" :max="1.5" :step="0.1" style="width: 160px" />
          </div>
          <div class="pg-input-row">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              :disabled="sending"
              @keydown="onKeydown"
            />
            <el-button v-if="sending" class="pg-send" size="large" type="warning" @click="stopStream">
              停止
            </el-button>
            <el-button v-else class="pg-send" size="large" type="primary" :disabled="!inputText.trim()" @click="send">
              发送
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.playground-page {
  max-width: 1240px;
  margin: 0 auto;
}
.pg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.pg-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
  padding: 14px 8px;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
}
.stat-num.usable { color: #18a058; }
.stat-num.chat { color: #2080f0; }
.stat-num.muted { color: var(--text-tertiary); }
.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.pg-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  margin-bottom: 16px;
}
.pg-toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.cnt {
  margin-left: 4px;
  font-size: 12px;
  opacity: 0.75;
}
.pg-grid {
  min-height: 220px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
  align-items: stretch;
}
.model-card.is-unusable {
  opacity: 0.72;
  filter: grayscale(0.3);
}
.mc-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mc-avatar {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #fff;
}
.mc-avatar-primary { background: linear-gradient(135deg, #2080f0, #66b1ff); }
.mc-avatar-success { background: linear-gradient(135deg, #18a058, #63e2b7); }
.mc-avatar-warning { background: linear-gradient(135deg, #d03050, #e88080); }
.mc-avatar-info { background: linear-gradient(135deg, #909399, #c0c4cc); }
.mc-avatar-danger { background: linear-gradient(135deg, #d03050, #e88080); }
.mc-info {
  flex: 1;
  min-width: 0;
}
.mc-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.mc-model {
  font-weight: 700;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mc-provider {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-secondary);
}
.mc-live {
  flex: none;
  font-size: 12px;
  color: #18a058;
  background: rgba(24, 160, 88, 0.12);
  border-radius: 999px;
  padding: 1px 8px;
}
.mc-meta {
  display: flex;
  gap: 6px;
  margin: 12px 0 8px;
}
.chip {
  font-size: 12px;
  background: var(--bg-fill);
  border-radius: 6px;
  padding: 1px 8px;
}
.mc-caps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}
.cap-chip {
  font-size: 12px;
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  border-radius: 999px;
  padding: 1px 10px;
}
.mc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-top: 1px dashed var(--border-color);
  padding-top: 10px;
}
.small { font-size: 12px; }
.muted { color: var(--text-tertiary); }

/* console */
.pg-console-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}
.pg-console-title {
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}
.pg-console-sub {
  margin-top: 4px;
  font-size: 12px;
}
.pg-console-list {
  height: min(58vh, 520px);
  overflow-y: auto;
  padding: 14px 4px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.pg-console-empty {
  margin: auto;
  text-align: center;
  max-width: 640px;
}
.pg-console-empty-title {
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}
.pg-quick-wrap {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.pg-turn {
  display: flex;
}
.pg-turn.user {
  justify-content: flex-end;
}
.pg-turn-bubble {
  max-width: 82%;
  padding: 10px 14px;
  border-radius: 14px;
  line-height: 1.7;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}
.pg-turn.user .pg-turn-bubble {
  background: var(--color-primary);
  color: #fff;
  border-top-right-radius: 4px;
}
.pg-turn.assistant {
  justify-content: flex-start;
}
.pg-turn.assistant .pg-turn-bubble {
  background: var(--bg-fill);
  border-top-left-radius: 4px;
  white-space: normal;
}
.typing {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.typing i,
.typing-tail i {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: pg-blink 1s infinite;
}
.typing-tail { margin-left: 4px; }
@keyframes pg-blink {
  0%, 100% { opacity: 0.2; }
  50% { opacity: 1; }
}
.pg-console-input {
  border-top: 1px solid var(--border-color);
  padding-top: 10px;
}
.pg-params {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.pg-input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}
.pg-send {
  height: 62px;
}
</style>
