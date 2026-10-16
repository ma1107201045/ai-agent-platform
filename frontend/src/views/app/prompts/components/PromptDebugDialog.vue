<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, VideoPause } from '@element-plus/icons-vue'
import { appPromptApi } from '@/api/app-prompt'
import { modelApi } from '@/api/model'
import type { ChatModelInfo, PromptTemplate } from '@/api/types'
import { copyText } from '@/utils/clipboard'
import { varSample } from '../promptMeta'

const debugVisible = ref(false)
const debugTemplate = ref<PromptTemplate | null>(null)
const debugForm = reactive({
  content: '',
  variableValues: {} as Record<string, string>,
  varNames: [] as string[],
  question: '用一句话总结这段内容，并给出建议。',
  modelId: 0
})
const chatModels = ref<ChatModelInfo[]>([])
/** 模型下拉加载中（打开调试试跑时异步拉取可用 LLM） */
const chatModelsLoading = ref(false)
const rendered = ref('')
const testing = ref(false)
const testResult = ref('')
/** 进行中的流式请求（停止按钮 / 关闭弹窗时中断） */
const debugAbort = ref<AbortController | null>(null)
/** 模型回复区滚动容器 */
const resultBoxRef = ref<HTMLElement | null>(null)
/** 是否自动跟随最新回复（用户上翻查看历史时暂停，回到底部后恢复） */
const resultFollow = ref(true)
/** 变量名 → 说明（来自模板 variables 定义，作为试跑输入框提示） */
const debugVarDesc = ref<Record<string, string>>({})

async function open(row: PromptTemplate) {
  debugTemplate.value = row
  debugForm.content = row.content
  debugForm.question = '用一句话总结这段内容，并给出建议。'
  debugForm.modelId = 0
  testResult.value = ''
  rendered.value = ''
  debugVarDesc.value = {}
  const names: string[] = []
  const vals: Record<string, string> = {}
  try {
    const defs: { name: string; desc?: string }[] = row.variables ? JSON.parse(row.variables) : []
    for (const d of defs) {
      if (d.name) {
        names.push(d.name)
        vals[d.name] = ''
      }
    }
  } catch {
    // 忽略非法 JSON
  }
  debugForm.varNames = names
  debugForm.variableValues = vals
  // 从后端解析变量说明（服务端宽松解析，非法定义返回空 map）
  if (row.variables) {
    try {
      debugVarDesc.value = await appPromptApi.parseVariables(row.variables)
    } catch {
      // 解析失败不阻塞调试
    }
  }
  debugVisible.value = true
  loadChatModels()
}

async function loadChatModels() {
  chatModelsLoading.value = true
  try {
    chatModels.value = await modelApi.chatModels()
    if (chatModels.value.length > 0) {
      debugForm.modelId = chatModels.value[0].id
    }
  } catch {
    // 无可用模型时不阻塞调试
  } finally {
    chatModelsLoading.value = false
  }
}

function ensureVariableValue(name: string) {
  if (!(name in debugForm.variableValues)) {
    debugForm.variableValues[name] = ''
  }
}

function appendVar(name: string) {
  if (!debugForm.varNames.includes(name)) {
    debugForm.varNames.push(name)
    ensureVariableValue(name)
  }
}

/** 提取调试内容里的 {{var}} 并自动补全变量 */
async function refreshVars() {
  if (!debugForm.content.trim()) return
  const names = await appPromptApi.extractVariables(debugForm.content)
  for (const n of names) {
    appendVar(n)
  }
}

async function doRender() {
  if (!debugForm.content.trim()) {
    ElMessage.warning('请输入要调试的正文')
    return
  }
  await refreshVars()
  const payload: Record<string, string> = {}
  for (const n of debugForm.varNames) {
    payload[n] = debugForm.variableValues[n] ?? ''
  }
  rendered.value = await appPromptApi.render(debugForm.content, payload)
}

async function runTest() {
  if (!rendered.value) {
    ElMessage.warning('请先点击「渲染预览」生成完整提示词')
    return
  }
  if (!debugForm.modelId) {
    ElMessage.warning('请选择测试模型')
    return
  }
  // SSE 流式输出：增量展示模型回复（与模型管理「对话测试」一致，可随时停止）
  testResult.value = ''
  testing.value = true
  resultFollow.value = true
  const controller = new AbortController()
  debugAbort.value = controller
  try {
    await modelApi.chatStream(
      {
        modelId: debugForm.modelId,
        messages: [
          { role: 'system', content: rendered.value },
          { role: 'user', content: debugForm.question || '' }
        ]
      },
      (chunk) => {
        if (chunk.delta) {
          testResult.value += chunk.delta
          followResultScroll()
        }
      },
      controller.signal
    )
    if (!testResult.value.trim()) testResult.value = '(模型未返回内容)'
  } catch (e) {
    // 用户主动停止：保留已输出内容，不视为错误
    if (!(e instanceof DOMException && e.name === 'AbortError')) {
      testResult.value = e instanceof Error ? e.message : String(e)
    }
  } finally {
    testing.value = false
    debugAbort.value = null
    followResultScroll()
  }
}

/** 停止流式输出（保留已生成内容） */
function stopDebugStream() {
  debugAbort.value?.abort()
}

/** 回复区用户手动滚动：贴近底部才继续自动跟随最新内容 */
function onResultScroll() {
  const el = resultBoxRef.value
  if (!el) return
  resultFollow.value = el.scrollHeight - el.scrollTop - el.clientHeight < 48
}

/** 把回复区滚到底部，让最新输出的内容进入视口 */
function followResultScroll() {
  if (!resultFollow.value) return
  nextTick(() => {
    const el = resultBoxRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// 弹窗关闭（关闭按钮 / 点击遮罩 / Esc）时中断仍在进行的流式请求
watch(debugVisible, (visible) => {
  if (!visible) debugAbort.value?.abort()
})

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="debugVisible"
    title="调试试跑"
    width="880px"
    :close-on-click-modal="false"
    top="4vh"
    class="debug-dialog"
  >
    <template v-if="debugTemplate">
      <div class="debug-head">
        <span class="dh-name">{{ debugTemplate.name }}</span>
        <span class="dh-version">当前 v{{ debugTemplate.version }} · 试跑修改不会影响模板</span>
      </div>

      <div class="debug-grid">
        <!-- 左：编辑正文 -->
        <div class="debug-col debug-col--left">
          <div class="col-title">1. 提示词正文（可临时修改）</div>
          <el-input
            v-model="debugForm.content"
            type="textarea"
            :rows="11"
            :placeholder="'支持' + varSample('var') + '变量占位'"
            class="debug-content"
            @blur="refreshVars"
          />

          <div class="col-title var-title">2. 变量值</div>
          <div v-if="debugForm.varNames.length === 0" class="no-var">
            未发现变量，可直接点击「渲染预览」测试固定写法
          </div>
          <div v-for="name in debugForm.varNames" :key="name" class="var-row">
            <el-tooltip :content="debugVarDesc[name] || name" placement="top" :show-after="200">
              <span class="var-name">{{ varSample(name) }}</span>
            </el-tooltip>
            <el-input
              v-model="debugForm.variableValues[name]"
              size="small"
              :placeholder="debugVarDesc[name] || '请输入变量值'"
              @input="doRender"
            />
          </div>

          <el-button type="primary" plain class="render-btn" @click="doRender">渲染预览</el-button>
          <div class="col-title render-title">
            <span>渲染结果</span>
            <el-button
              v-if="rendered"
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              @click="copyText(rendered, '渲染结果已复制')"
            >
              复制
            </el-button>
          </div>
          <el-input
            :model-value="rendered"
            type="textarea"
            :rows="5"
            readonly
            class="rendered-box"
            placeholder="渲染后的完整提示词将显示在这里"
          />
        </div>

        <!-- 右：模型测试 -->
        <div class="debug-col debug-col--right">
          <div class="col-title">3. 模型测试</div>
          <div class="model-row">
            <el-select
              v-model="debugForm.modelId"
              :placeholder="
                chatModelsLoading
                  ? '模型加载中…'
                  : chatModels.length
                    ? '选择模型'
                    : '暂无可用模型，请先配置启用的 LLM 模型'
              "
              class="model-select"
              :loading="chatModelsLoading"
              :disabled="chatModelsLoading || testing"
            >
              <el-option
                v-for="m in chatModels"
                :key="m.id"
                :label="`${m.providerName} · ${m.modelName}`"
                :value="m.id"
              />
            </el-select>
            <el-button v-if="testing" type="danger" plain @click="stopDebugStream">
              <el-icon style="margin-right: 4px"><VideoPause /></el-icon>停止
            </el-button>
            <el-button v-else type="primary" class="btn-gradient" @click="runTest">运行</el-button>
          </div>
          <div class="col-title">测试问题</div>
          <el-input
            v-model="debugForm.question"
            type="textarea"
            :rows="3"
            placeholder="输入要发送给模型的问题"
          />
          <div class="col-title">
            模型回复
            <span v-if="testing" class="stream-dot" />
            <span v-if="testing" class="stream-text">生成中…</span>
          </div>
          <div ref="resultBoxRef" v-loading="testing" class="result-box" @scroll="onResultScroll">
            <pre v-if="testResult">{{ testResult }}</pre>
            <span v-else class="result-placeholder"
              >运行后在此流式查看模型回复，可反复修改正文/变量对比不同写法效果</span
            >
          </div>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.debug-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.dh-name {
  font-size: 15px;
  font-weight: 600;
}
.dh-version {
  font-size: 12px;
  color: var(--text-tertiary);
}
.debug-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  flex: 1 1 auto;
  min-height: 0;
}
.debug-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
/* 左列：内容超高时在列内滚动，避免弹窗整体出现滚动条 */
.debug-col--left {
  overflow-y: auto;
  padding-right: 2px;
}
/* 右列：只让「模型回复」卡片内部滚动，其余保持固定 */
.debug-col--right {
  overflow: hidden;
}
.debug-col--right > * {
  flex-shrink: 0;
}
.debug-col--right .result-box {
  flex-shrink: 1;
}
.col-title {
  font-size: 13px;
  font-weight: 600;
  margin: 12px 0 6px;
  color: var(--text-primary);
}
.col-title:first-child {
  margin-top: 0;
}
.var-title {
  margin-top: 14px;
}
.no-var {
  font-size: 12px;
  color: var(--text-tertiary);
  background: var(--bg-fill, #f5f7fa);
  padding: 8px 12px;
  border-radius: 8px;
}
.var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.var-name {
  font-size: 12px;
  color: var(--brand-1);
  background: var(--brand-gradient-soft);
  padding: 4px 8px;
  border-radius: 6px;
  white-space: nowrap;
  font-family: var(--font-mono, monospace);
}
.render-btn {
  margin-top: 10px;
}
.rendered-box {
  font-size: 12px;
}
.model-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.model-select {
  flex: 1;
}
.result-box {
  /* 占满右列剩余空间；内容多时内部滚动（fits 窗口，不把弹窗撑出视口） */
  flex: 0 1 auto;
  min-height: 100px;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: var(--bg-fill, #f5f7fa);
  border-radius: 10px;
  padding: 12px;
  position: relative;
}
.result-box pre {
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.result-placeholder {
  font-size: 12px;
  color: var(--text-tertiary);
}
/* 流式输出进行中的指示：脉冲圆点 + 文案 */
.stream-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-left: 6px;
  border-radius: 50%;
  background: var(--brand-1, #5b6cff);
  vertical-align: middle;
  animation: stream-pulse 1s ease-in-out infinite;
}
.stream-text {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary);
  vertical-align: middle;
}
@keyframes stream-pulse {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}
.render-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>

<style>
/* 调试试跑弹窗经 teleport 挂到 body，scoped 样式不会命中，必须用全局样式。
   兼容 class 落在 .el-dialog 面板或外层遮罩两种结构。 */
.debug-dialog.el-dialog,
.debug-dialog .el-dialog {
  display: flex;
  flex-direction: column;
  max-height: 92vh;
}
/* body 整体不再滚动（滚动收进左右两列），高度撑满弹窗剩余空间 */
.debug-dialog .el-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.debug-dialog .debug-head {
  flex-shrink: 0;
}
</style>
