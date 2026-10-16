<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, VideoPause } from '@element-plus/icons-vue'
import { modelApi } from '@/api/model'
import type { ModelInfo, ModelProvider, Usage } from '@/api/types'
import { modelTypeLabel, modelTypeTag } from '../modelMeta'

const testVisible = ref(false)
const testMode = ref<'chat' | 'embed'>('chat')
const testModel = ref<ModelInfo | null>(null)
const testProviderName = ref('')
const testPrompt = ref('')
const testLoading = ref(false)
const testOutput = ref('')
const testError = ref('')
const testUsage = ref<Usage | null>(null)
const testEmbedText = ref('')
const testEmbedVectors = ref<number[][] | null>(null)
/** LLM 测试是否使用 SSE 流式输出 */
const testStream = ref(true)
/** 流式输出进行中（用于展示「停止」按钮） */
const testStreaming = ref(false)
const testAbort = ref<AbortController | null>(null)
/** 测试弹窗内滚动容器选择器（弹窗 teleport 到 body，用类名定位） */
const TEST_SCROLL_BOX = '.test-dialog .el-dialog__body'
/** 流式输出时是否自动跟随最新文字（用户上翻回看历史时暂停，回到底部后恢复） */
const testAutoFollow = ref(true)

function bindTestScrollFollow() {
  const box = document.querySelector(TEST_SCROLL_BOX) as HTMLElement | null
  if (!box || (box as { __followBound?: boolean }).__followBound) return
  ;(box as { __followBound?: boolean }).__followBound = true
  box.addEventListener(
    'scroll',
    () => {
      testAutoFollow.value = box.scrollHeight - box.scrollTop - box.clientHeight < 48
    },
    { passive: true }
  )
}

/** 把滚动容器滚到最底部，让最新输出的文字进入视口 */
function followTestScroll() {
  if (!testAutoFollow.value) return
  nextTick(() => {
    requestAnimationFrame(() => {
      const box = document.querySelector(TEST_SCROLL_BOX) as HTMLElement | null
      if (box) box.scrollTop = box.scrollHeight
    })
  })
}

const testDialogTitle = computed(() => {
  const name = testModel.value ? ` · ${testModel.value.name}` : ''
  return testMode.value === 'embed' ? `向量测试${name}` : `对话测试${name}`
})

/** 向量化结果摘要：条数 / 维度 / 首个向量前 8 维 */
const embedSummary = computed(() => {
  const vectors = testEmbedVectors.value
  if (!vectors?.length) return null
  const dim = (vectors[0] || []).length
  return { count: vectors.length, dim, head: (vectors[0] || []).slice(0, 8) }
})

function open(m: ModelInfo, provider: ModelProvider, mode: 'chat' | 'embed' = 'chat') {
  testMode.value = mode
  testModel.value = m
  testProviderName.value = provider.name
  testPrompt.value = ''
  testOutput.value = ''
  testError.value = ''
  testUsage.value = null
  testEmbedVectors.value = null
  testEmbedText.value = mode === 'embed' ? '智能体（Agent）是什么？\n请对这段话进行向量化' : ''
  testVisible.value = true
  testAutoFollow.value = true
  // 等弹窗渲染后：恢复自动跟随、置顶滚动、绑定一次滚动监听
  setTimeout(() => {
    bindTestScrollFollow()
    const box = document.querySelector(TEST_SCROLL_BOX) as HTMLElement | null
    if (box) box.scrollTop = 0
  }, 0)
}

async function runTest() {
  testError.value = ''
  testOutput.value = ''
  testUsage.value = null
  testEmbedVectors.value = null

  // ---------- Embedding：向量化 ----------
  if (testMode.value === 'embed') {
    const texts = testEmbedText.value
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
    if (!texts.length) {
      ElMessage.warning('请输入至少一条待向量化的文本')
      return
    }
    testLoading.value = true
    try {
      const resp = await modelApi.embed({ modelId: testModel.value!.id, texts })
      testEmbedVectors.value = resp.vectors || []
      testUsage.value = resp.usage || null
    } catch (e) {
      testError.value = e instanceof Error ? e.message : String(e)
    } finally {
      testLoading.value = false
    }
    return
  }

  // ---------- LLM：对话 ----------
  const prompt = testPrompt.value.trim()
  if (!prompt) {
    ElMessage.warning('请输入测试消息')
    return
  }

  // 非流式：一次性返回
  if (!testStream.value) {
    testLoading.value = true
    try {
      const resp = await modelApi.chat({ modelId: testModel.value!.id, prompt })
      testOutput.value = resp.content || '(空回复)'
      testUsage.value = resp.usage || null
    } catch (e) {
      testError.value = e instanceof Error ? e.message : String(e)
    } finally {
      testLoading.value = false
    }
    return
  }

  // 流式：SSE 实时渲染，可随时停止（AbortController 中断请求）
  testLoading.value = true
  testStreaming.value = true
  const controller = new AbortController()
  testAbort.value = controller
  try {
    await modelApi.chatStream(
      {
        modelId: testModel.value!.id,
        messages: [
          { role: 'system', content: 'You are a helpful assistant.' },
          { role: 'user', content: prompt }
        ]
      },
      (chunk) => {
        if (chunk.delta) {
          testOutput.value += chunk.delta
          followTestScroll()
        }
        if (chunk.usage) testUsage.value = chunk.usage
      },
      controller.signal
    )
    if (!testOutput.value.trim()) testOutput.value = '(空回复)'
  } catch (e) {
    // 用户主动停止：保留已输出内容，不视为错误
    if (!(e instanceof DOMException && e.name === 'AbortError')) {
      testError.value = e instanceof Error ? e.message : String(e)
    }
  } finally {
    testLoading.value = false
    testStreaming.value = false
    testAbort.value = null
  }
}

/** 停止流式输出（保留已生成内容） */
function stopTest() {
  testAbort.value?.abort()
}

// 关闭测试弹窗（关闭按钮 / 点击遮罩 / Esc）时，中断仍在进行的流式请求，
// 使 fetch 断开连接，通知服务端结束输出
watch(testVisible, (visible) => {
  if (!visible) testAbort.value?.abort()
})

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="testVisible"
    :title="testDialogTitle"
    width="640px"
    class="test-dialog"
    top="5vh"
  >
    <div class="test-meta">
      <el-tag size="small" :type="modelTypeTag(testModel?.modelType)" effect="light">
        {{ modelTypeLabel(testModel?.modelType) }}
      </el-tag>
      <span
        >供应商：<b>{{ testProviderName }}</b></span
      >
      <span class="mono">模型：{{ testModel?.name }}</span>
    </div>
    <template v-if="testMode === 'chat'">
      <el-input
        v-model="testPrompt"
        type="textarea"
        :rows="4"
        placeholder="输入一句测试消息，例如：你好，请用一句话介绍你自己"
      />
      <div class="test-options">
        <el-switch v-model="testStream" size="small" active-text="流式输出" />
        <span class="muted">实时逐字展示回复，可随时停止</span>
      </div>
    </template>
    <template v-else>
      <div class="embed-label">待向量化文本（每行一条）</div>
      <el-input
        v-model="testEmbedText"
        type="textarea"
        :rows="4"
        placeholder="例如：智能体（Agent）是什么？&#10;请对这段话进行向量化"
      />
    </template>
    <div class="test-actions">
      <template v-if="testStreaming">
        <el-button type="danger" plain @click="stopTest">
          <el-icon style="margin-right: 4px"><VideoPause /></el-icon>停止输出
        </el-button>
        <span class="muted">已输出 {{ testOutput.length }} 字符，点击停止将保留当前内容</span>
      </template>
      <el-button v-else type="primary" :loading="testLoading" class="btn-gradient" @click="runTest">
        {{ testLoading ? '请求中…' : '运行测试' }}
      </el-button>
    </div>

    <!-- 请求等待中的加载反馈（流式首 token 前 / 非流式 / 向量化期间展示） -->
    <div v-if="testLoading && !testOutput" class="test-loading">
      <el-icon class="test-loading-icon"><Loading /></el-icon>
      <span>{{ testStreaming ? '正在等待模型响应…' : '正在请求模型…' }}</span>
    </div>

    <div v-if="testError" class="test-error">
      <div class="test-error-title">调用失败</div>
      <pre class="mono">{{ testError }}</pre>
    </div>
    <div v-else-if="testMode === 'embed' && embedSummary" class="test-result">
      <div class="test-result-title">
        向量化成功
        <span v-if="testUsage" class="muted">（共 {{ testUsage.totalTokens }} tokens）</span>
      </div>
      <div class="embed-summary">
        共 {{ embedSummary?.count }} 条文本，向量维度 {{ embedSummary?.dim }}
      </div>
      <div class="embed-head">
        首个向量前 8 维：<span class="mono">[{{ embedSummary?.head?.join(', ') }}]</span>
      </div>
    </div>
    <div v-else-if="testOutput" class="test-result">
      <div class="test-result-title">
        模型回复
        <span v-if="testStreaming" class="stream-dot"></span>
        <span v-if="testStreaming" class="muted">生成中…</span>
        <span v-if="testUsage" class="muted">
          （{{ testUsage.promptTokens }}+{{ testUsage.completionTokens }} tokens）
        </span>
      </div>
      <pre>{{ testOutput }}</pre>
    </div>
  </el-dialog>
</template>

<style scoped>
/* 测试面板 */
.test-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
}
.embed-label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}
.test-options {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.stream-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin: 0 2px 1px 4px;
  border-radius: 50%;
  background: var(--el-color-primary);
  vertical-align: middle;
  animation: stream-pulse 1s ease-in-out infinite;
}
@keyframes stream-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.3;
    transform: scale(0.7);
  }
}
.embed-summary {
  margin-top: 8px;
  font-size: 13px;
}
.embed-head {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.test-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
  min-height: 32px;
}
.test-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--fill-lighter);
  border: 1px solid var(--border-color);
  color: var(--text-tertiary);
  font-size: 13px;
}
.test-loading-icon {
  animation: test-loading-rotate 1s linear infinite;
}
@keyframes test-loading-rotate {
  to {
    transform: rotate(360deg);
  }
}
.test-result,
.test-error {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
}
.test-result {
  background: var(--fill-lighter);
  border: 1px solid var(--border-color);
}
.test-result pre {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  color: var(--text-primary);
}
.test-result-title {
  font-weight: 600;
  font-size: 12px;
  color: var(--text-secondary);
}
.test-error {
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
}
.test-error-title {
  font-weight: 600;
  font-size: 12px;
  color: var(--el-color-danger);
}
.test-error pre {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-color-danger);
}
.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
</style>

<style>
/* 测试弹窗经 teleport 挂到 body，scoped 样式不会命中，必须用全局样式。
   兼容 class 落在 .el-dialog 面板或外层遮罩两种结构。 */
.test-dialog.el-dialog,
.test-dialog .el-dialog {
  display: flex;
  flex-direction: column;
  max-height: 72vh;
}
.test-dialog .el-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}
</style>
