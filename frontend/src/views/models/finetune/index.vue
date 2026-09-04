<script setup lang="ts">
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {CircleCheck, Document, Refresh, Upload, Warning} from '@element-plus/icons-vue'

/* ---------------- 微调流程说明（静态，诚实标注） ---------------- */
interface FlowStep {
  title: string
  desc: string
  flag?: string
  flagClass?: 'step-doing' | 'step-wait' | 'step-plan'
}
const steps: FlowStep[] = [
  { title: '准备训练数据', desc: '整理对话 / 指令样本为 JSONL 单行对象' },
  { title: '格式校验', desc: '在下方工作台校验字段与结构并统计规模', flag: '进行中', flagClass: 'step-doing' },
  { title: '发起训练', desc: '提交至目标供应商微调控制台执行', flag: '待接入', flagClass: 'step-wait' },
  { title: '效果评估与发布', desc: '微调后效果对比与模型上线', flag: '规划中', flagClass: 'step-plan' }
]

/* ---------------- 供应商微调能力参考（人工维护，以官方为准） ---------------- */
interface VendorRow {
  vendor: string
  families: string
  apiCompatible: string
  dataFormat: string
  note: string
  doc: string
}
const vendors: VendorRow[] = [
  {
    vendor: '阿里云百炼',
    families: '通义千问 Qwen 系列',
    apiCompatible: 'OpenAI 兼容',
    dataFormat: 'Chat(messages) / 指令微调',
    note: '提供微调训练与评测服务，需开通对应模型权限并按量计费',
    doc: 'https://help.aliyun.com/zh/model-studio'
  },
  {
    vendor: '火山方舟',
    families: '豆包 Doubao 系列',
    apiCompatible: 'OpenAI 兼容',
    dataFormat: 'Chat(messages)',
    note: '支持基础微调与 LoRA 微调，训练数据需先在方舟侧清洗标注',
    doc: 'https://www.volcengine.com/docs/82379'
  },
  {
    vendor: '智谱 AI',
    families: 'GLM 系列',
    apiCompatible: 'OpenAI 兼容',
    dataFormat: 'Chat(messages)',
    note: '提供微调 API，数据格式与其在线训练工具对齐',
    doc: 'https://open.bigmodel.cn/'
  },
  {
    vendor: 'Moonshot',
    families: 'Kimi 系列',
    apiCompatible: 'OpenAI 兼容',
    dataFormat: 'Chat(messages)',
    note: '支持消息级微调，训练申请以官方平台实际开放为准',
    doc: 'https://platform.moonshot.cn/docs'
  }
]

/* ---------------- 训练数据校验引擎 ---------------- */
type IssueLevel = 'error' | 'warn'
interface RowIssue {
  level: IssueLevel
  message: string
}
interface RowResult {
  lineNo: number
  ok: boolean
  raw: string
  snippet: string
  issues: RowIssue[]
}
interface ValidateStats {
  total: number
  ok: number
  error: number
  warn: number
  tokens: number
  chatSamples: number
}

const DEFAULT_SYSTEM = '你是电商客服小蜜，回答简洁专业，语气友好。'
const sampleJsonl = [
  { messages: [{ role: 'system', content: DEFAULT_SYSTEM }, { role: 'user', content: '订单两天了还没发货，怎么回事？' }, { role: 'assistant', content: '很抱歉让您久等，麻烦提供一下订单号，我马上为您核实发货进度。' }] },
  { messages: [{ role: 'system', content: DEFAULT_SYSTEM }, { role: 'user', content: '可以修改收货地址吗？' }, { role: 'assistant', content: '可以的。订单未发货前支持修改，请在订单详情页操作，或告诉我新地址，我来协助登记。' }] },
  { messages: [{ role: 'system', content: DEFAULT_SYSTEM }, { role: 'user', content: '退款一般多久到账？' }, { role: 'assistant', content: '退款审核通过后，1-3 个工作日内原路退回，具体以支付渠道到账时间为准。' }] },
  { messages: [{ role: 'user', content: '给个好评模板呗' }, { role: 'assistant', content: '可以这样写：卖家服务很贴心，物流速度也快，整体购物体验很好，推荐！' }] }
].map((o) => JSON.stringify(o)).join('\n')

const ALLOWED_ROLES = new Set(['system', 'user', 'assistant', 'tool'])
const MAX_FILE_BYTES = 5 * 1024 * 1024

const PLACEHOLDER_JSONL =
  '每行粘贴一个 JSON 对象。例如：\n{"messages":[{"role":"system","content":"你是客服"},{"role":"user","content":"什么时候发货？"},{"role":"assistant","content":"您好，正在为您核实~"}]}'

const inputText = ref('')
const formatChoice = ref<'auto' | 'chat' | 'completion'>('auto')
const validated = ref<RowResult[]>([])
const issueRows = computed(() => validated.value.filter((r) => !r.ok))
const fileInput = ref<HTMLInputElement | null>(null)

function estimateTokens(text: string): number {
  if (!text) return 0
  let tokens = 0
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i)
    tokens += code > 0xff ? 1 : 0.25
  }
  return Math.round(tokens)
}

function extractContentText(value: unknown): string {
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value.map(extractContentText).join('')
  if (isPlainObject(value) && typeof value.text === 'string') return value.text
  return ''
}

function contentOfChatMessages(value: unknown): string {
  if (!Array.isArray(value)) return ''
  return value.map((m) => extractContentText(isPlainObject(m) ? m.content : '')).join('')
}

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v)
}

function validateLine(lineNo: number, raw: string): RowResult {
  const issues: RowIssue[] = []
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch (e) {
    return { lineNo, ok: false, raw, snippet: snippet(raw), issues: [{ level: 'error', message: `JSON 解析失败：${(e as Error).message}` }] }
  }
  if (!isPlainObject(parsed)) {
    return { lineNo, ok: false, raw, snippet: snippet(raw), issues: [{ level: 'error', message: '每行须为一个 JSON 对象' }] }
  }
  const messages = Array.isArray(parsed.messages) ? (parsed.messages as unknown[]) : undefined
  const prompt = typeof parsed.prompt === 'string' ? parsed.prompt : undefined
  const completion = typeof parsed.completion === 'string' ? parsed.completion : undefined
  const isChat = formatChoice.value !== 'completion' && (!!messages || formatChoice.value === 'chat')
  const isCompletion = formatChoice.value !== 'chat' && !messages && (!!prompt || !!completion || formatChoice.value === 'completion')

  if (isChat) {
    if (!messages) {
      issues.push({ level: 'error', message: '缺少 messages 数组（或与所选格式不一致）' })
    } else if (!messages.length) {
      issues.push({ level: 'error', message: 'messages 不能为空数组' })
    } else {
      const msgs = messages
      msgs.forEach((m, i) => {
        if (!isPlainObject(m)) {
          issues.push({ level: 'error', message: `messages[${i}] 须为对象` })
          return
        }
        const role = m.role
        if (typeof role !== 'string' || !ALLOWED_ROLES.has(role)) {
          issues.push({ level: 'error', message: `messages[${i}] role 非法（${String(role)}），应为 system/user/assistant/tool` })
        }
        const content = m.content
        const contentOk = Array.isArray(content)
          ? content.length > 0
          : typeof content === 'string' && content.trim().length > 0
        if (typeof content !== 'string' && !Array.isArray(content)) {
          issues.push({ level: 'error', message: `messages[${i}] content 应为字符串或内容数组` })
        } else if (!contentOk) {
          issues.push({ level: 'error', message: `messages[${i}] content 为空` })
        }
        if (role === 'tool' && m.tool_call_id === undefined) {
          issues.push({ level: 'warn', message: `messages[${i}] 为 tool 角色，建议补充 tool_call_id 字段` })
        }
      })
      // 顺序建议
      const firstRole = (msgs[0] as Record<string, unknown>).role
      if (firstRole === 'assistant') {
        issues.push({ level: 'warn', message: '首条消息为 assistant，通常应以 system / user 开头' })
      }
      const contentSample = contentOfChatMessages(messages)
      const tokens = estimateTokens(contentSample)
      if (tokens < 5) {
        issues.push({ level: 'warn', message: '对话总内容过短（约 <5 tokens），训练信号不足' })
      }
    }
  } else if (isCompletion) {
    if (!prompt) issues.push({ level: 'error', message: '缺少 prompt 字段（字符串）' })
    else if (!prompt.trim()) issues.push({ level: 'error', message: 'prompt 为空' })
    if (!completion) issues.push({ level: 'error', message: '缺少 completion 字段（字符串）' })
    else if (!completion.trim()) issues.push({ level: 'error', message: 'completion 为空' })
  } else {
    issues.push({
      level: 'error',
      message: '无法识别数据格式：应包含 messages 数组（Chat）或 prompt + completion 字段（补全），也可在下方手动指定格式'
    })
  }
  return { lineNo, ok: issues.length === 0, raw, snippet: snippet(raw), issues }
}

function snippet(raw: string): string {
  const single = raw.replace(/\s+/g, ' ').trim()
  return single.length > 90 ? `${single.slice(0, 90)}…` : single
}

function runValidate() {
  const text = inputText.value
  if (!text.trim()) {
    ElMessage.warning('请先粘贴训练数据或上传 JSONL 文件')
    return
  }
  const lines = text.split(/\r?\n/)
  const results: RowResult[] = []
  lines.forEach((line, idx) => {
    if (!line.trim()) return
    results.push(validateLine(idx + 1, line))
  })
  validated.value = results
  const bad = results.filter((r) => !r.ok)
  if (bad.length === 0) {
    ElMessage.success(`校验通过：${results.length} 行数据格式均合规`)
  } else {
    const errCount = bad.filter((r) => r.issues.some((i) => i.level === 'error')).length
    ElMessage.warning(`共 ${bad.length} 行存在问题（错误 ${errCount} 行）`)
  }
}

const stats = computed<ValidateStats>(() => {
  const validRows = validated.value.filter((r) => r.ok)
  const allIssues = validated.value.flatMap((r) => r.issues)
  let tokens = 0
  let chatSamples = 0
  for (const row of validRows) {
    try {
      const parsed = JSON.parse(row.raw) as Record<string, unknown>
      if (Array.isArray(parsed.messages)) {
        chatSamples++
        tokens += estimateTokens(contentOfChatMessages(parsed.messages))
      } else if (typeof parsed.prompt === 'string' && typeof parsed.completion === 'string') {
        tokens += estimateTokens(parsed.prompt + parsed.completion)
      }
    } catch {
      // 已逐行校验，解析失败行不计入
    }
  }
  return {
    total: validated.value.length,
    ok: validRows.length,
    error: allIssues.filter((i) => i.level === 'error').length,
    warn: allIssues.filter((i) => i.level === 'warn').length,
    tokens,
    chatSamples
  }
})

const checked = computed(() => validated.value.length > 0)

function fillSample() {
  inputText.value = sampleJsonl
  validated.value = []
}

function clearAll() {
  inputText.value = ''
  validated.value = []
  if (fileInput.value) fileInput.value.value = ''
}

function onPickFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > MAX_FILE_BYTES) {
    ElMessage.warning('文件超过 5MB，请拆分后上传或直接粘贴文本')
    if (fileInput.value) fileInput.value.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    inputText.value = String(reader.result ?? '')
    validated.value = []
    ElMessage.success(`已载入 ${file.name}（${file.size} 字节），点击「开始校验」`)
    if (fileInput.value) fileInput.value.value = ''
  }
  reader.onerror = () => {
    ElMessage.error('文件读取失败，请改为手动粘贴')
    if (fileInput.value) fileInput.value.value = ''
  }
  reader.readAsText(file, 'utf-8')
}

function formatText(m: RowResult['issues'][number]): string {
  return m.message
}

function formatStats() {
  const s = stats.value
  const errIssues = s.error
  if (errIssues > 0) return `共发现 ${errIssues} 个错误与 ${s.warn} 个提示`
  if (s.warn > 0) return `无硬性错误，存在 ${s.warn} 个优化提示`
  return '未发现格式问题，数据可用于训练'
}

function goToLine(lineNo: number) {
  const el = document.getElementById('ftn-input') as HTMLTextAreaElement | null
  if (!el) return
  el.focus()
  const text = el.value
  const parts = text.split('\n')
  let start = 0
  for (let i = 0; i < lineNo - 1 && i < parts.length; i++) start += parts[i].length + 1
  const targetLen = parts[lineNo - 1]?.length || 0
  el.setSelectionRange(start, Math.min(start + targetLen, text.length))
  el.scrollTop = Math.max(0, (lineNo - 7) * 21)
}

function levelBadge(l: IssueLevel) {
  return l === 'error' ? 'danger' : 'warning'
}
</script>

<template>
  <div class="page-container ftn-page">
    <!-- 块1：头部 -->
    <div class="ftn-head">
      <div>
        <h2 class="head-title">模型微调</h2>
        <p class="head-desc">
          训练执行与计费在模型供应商侧完成。平台当前提供数据准备能力：整理并校验训练数据，为供应商侧微调做好准备
        </p>
      </div>
      <div class="head-actions">
        <el-button :icon="Document" @click="fillSample">加载合规示例</el-button>
        <el-button :icon="Refresh" @click="clearAll">清空</el-button>
      </div>
    </div>

    <!-- 块2：流程时间线 -->
    <el-card shadow="never" class="hover-card ftn-card">
      <el-steps :active="1" align-center>
        <el-step v-for="s in steps" :key="s.title" :title="s.title">
          <template #description>
            <span class="step-desc">{{ s.desc }}</span>
            <span v-if="s.flag" class="step-badge" :class="s.flagClass">{{ s.flag }}</span>
          </template>
        </el-step>
      </el-steps>
    </el-card>

    <!-- 块3：数据录入工作台 -->
    <el-card shadow="never" class="hover-card ftn-card">
      <template #header>
        <div class="card-head">
          <div>
            <span class="card-title">训练数据校验工作台</span>
            <span class="muted ftn-tip">支持 OpenAI Chat（messages）与补全（prompt/completion）两种 JSONL 格式</span>
          </div>
          <el-radio-group v-model="formatChoice" size="small">
            <el-radio-button value="auto">自动识别</el-radio-button>
            <el-radio-button value="chat">Chat(messages)</el-radio-button>
            <el-radio-button value="completion">补全(prompt)</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div class="ftn-work">
        <el-input
          id="ftn-input"
          v-model="inputText"
          type="textarea"
          :rows="12"
          resize="vertical"
          class="ftn-textarea mono"
          :placeholder="PLACEHOLDER_JSONL"
        />
        <div class="ftn-side">
          <label class="ftn-upload">
            <input ref="fileInput" type="file" accept=".jsonl,.json,.ndjson,.txt" class="hidden-input" @change="onPickFile" />
            <el-icon><Upload /></el-icon>
            <div class="ftn-upload-title">上传 JSONL 文件</div>
            <div class="muted small">.jsonl / .json / .txt，≤ 5MB<br />超过 5MB 可拆分上传或粘贴文本</div>
          </label>
          <el-button type="primary" size="large" class="ftn-check-btn" @click="runValidate">开始校验</el-button>
          <el-button v-if="inputText" text @click="fillSample">换成合规示例</el-button>
        </div>
      </div>
    </el-card>

    <!-- 块4：统计卡（校验后显示） -->
    <div v-if="checked" class="ftn-stats">
      <div class="stat-card hover-card"><div class="stat-num">{{ stats.total }}</div><div class="stat-label">总行数</div></div>
      <div class="stat-card hover-card"><div class="stat-num ok">{{ stats.ok }}</div><div class="stat-label">有效行</div></div>
      <div class="stat-card hover-card">
        <div class="stat-num" :class="stats.error > 0 ? 'err' : 'ok'">{{ stats.error }}</div>
        <div class="stat-label">错误</div>
      </div>
      <div class="stat-card hover-card"><div class="stat-num warn">{{ stats.warn }}</div><div class="stat-label">提示</div></div>
      <div class="stat-card hover-card"><div class="stat-num">{{ stats.tokens.toLocaleString() }}</div><div class="stat-label">预估 token</div></div>
      <div class="stat-card hover-card"><div class="stat-num">{{ stats.chatSamples }}</div><div class="stat-label">对话样本</div></div>
    </div>

    <!-- 块5：校验结果 -->
    <div v-if="checked" class="ftn-card">
      <div v-if="!issueRows.length" class="result-success hover-card">
        <el-icon class="rs-icon"><CircleCheck /></el-icon>
        <div>
          <div class="rs-title">校验通过</div>
          <div class="muted small">{{ formatStats() }}。可将该文件提交至目标供应商的微调控制台发起训练（当前平台未接入训练服务）。</div>
        </div>
      </div>
      <el-card v-else shadow="never" class="hover-card">
        <template #header>
          <div class="card-head">
            <div class="card-title" :class="stats.error > 0 ? 'has-err' : ''">
              <el-icon><Warning /></el-icon>
              {{ formatStats() }}
            </div>
            <span class="muted small">点击行可定位到文本对应位置</span>
          </div>
        </template>
        <el-table :data="issueRows" size="small" max-height="360" @row-click="(r: RowResult) => goToLine(r.lineNo)">
          <el-table-column label="行号" width="80">
            <template #default="{ row }"><span class="mono row-no">{{ row.lineNo }}</span></template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="row.issues.length ? levelBadge(row.issues[0].level) : 'info'" size="small" effect="light">
                {{ row.issues.length ? row.issues[0].level === 'error' ? '错误' : '提示' : '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="snippet" label="行内容片段" min-width="260" show-overflow-tooltip />
          <el-table-column label="问题说明" min-width="260">
            <template #default="{ row }">
              <div v-for="(iss, i) in row.issues" :key="i" class="issue-line" :class="iss.level">
                <span class="issue-badge">{{ iss.level === 'error' ? '错误' : '提示' }}</span>{{ formatText(iss) }}
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 块6：供应商微调能力参考 -->
    <el-card shadow="never" class="hover-card ftn-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">供应商微调能力参考</span>
          <span class="muted small">以下为 OpenAI 兼容体系常见服务商微调能力汇总，数据可能随官方调整，请以官方文档为准</span>
        </div>
      </template>
      <el-table :data="vendors" size="default">
        <el-table-column prop="vendor" label="供应商" width="130">
          <template #default="{ row }"><span class="vendor-name">{{ row.vendor }}</span></template>
        </el-table-column>
        <el-table-column prop="families" label="模型族" min-width="150" />
        <el-table-column prop="apiCompatible" label="API 兼容" width="130" />
        <el-table-column prop="dataFormat" label="数据格式" width="170" />
        <el-table-column prop="note" label="说明" min-width="250" />
        <el-table-column label="官方入口" width="130">
          <template #default="{ row }">
            <el-link type="primary" :href="row.doc" target="_blank" rel="noopener">查看官方文档</el-link>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 块7：边界提示 -->
    <div class="ftn-footnote">
      <el-icon><Warning /></el-icon>
      <div>
        训练执行、权限开通与费用结算均在对应供应商侧完成，训练接口的适配已在路线图中；平台当前可用的能力为
        <b>训练数据准备与格式校验</b>，输出合规数据后再前往供应商控制台发起训练。
      </div>
    </div>
  </div>
</template>

<style scoped>
.ftn-page {
  max-width: 1240px;
  margin: 0 auto;
}
.ftn-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.ftn-card {
  margin-bottom: 16px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}
.card-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
}
.card-title.has-err {
  color: #d03050;
}
.ftn-tip {
  margin-left: 10px;
  font-size: 12px;
}
.muted {
  color: var(--text-tertiary);
}
.small {
  font-size: 12px;
}
.mono {
  font-family: var(--font-mono, 'JetBrains Mono', Consolas, monospace);
}
.step-badge {
  display: inline-flex;
  margin-left: 6px;
  font-size: 12px;
  border-radius: 999px;
  padding: 1px 10px;
  white-space: nowrap;
  vertical-align: middle;
}
.step-doing {
  color: #2080f0;
  background: rgba(32, 128, 240, 0.12);
}
.step-wait {
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.14);
}
.step-plan {
  color: var(--text-tertiary);
  background: var(--bg-fill);
}
.ftn-work {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
}
.ftn-textarea :deep(.el-textarea__inner) {
  font-size: 13px;
  line-height: 1.65;
}
.ftn-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ftn-upload {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-fill);
  color: var(--text-secondary);
  cursor: pointer;
  text-align: center;
  padding: 18px 12px;
  transition: all 0.2s;
}
.ftn-upload:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
}
.ftn-upload-title {
  font-size: 14px;
  font-weight: 600;
}
.hidden-input {
  display: none;
}
.ftn-check-btn {
  width: 100%;
}
.ftn-stats {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
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
.stat-num.ok {
  color: #18a058;
}
.stat-num.err {
  color: #d03050;
}
.stat-num.warn {
  color: #e6a23c;
}
.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.result-success {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 24px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(24, 160, 88, 0.35);
  background: rgba(24, 160, 88, 0.06);
}
.rs-icon {
  font-size: 34px;
  color: #18a058;
}
.rs-title {
  font-size: 16px;
  font-weight: 700;
  color: #18a058;
}
.row-no {
  color: var(--text-secondary);
  font-weight: 600;
}
.issue-line {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.5;
  padding: 1px 0;
  white-space: normal;
}
.issue-line.error {
  color: #d03050;
}
.issue-line.warn {
  color: #e6a23c;
}
.issue-badge {
  flex: none;
  font-size: 11px;
  border-radius: 4px;
  padding: 0 5px;
  margin-top: 2px;
}
.issue-line.error .issue-badge {
  background: rgba(208, 48, 80, 0.12);
}
.issue-line.warn .issue-badge {
  background: rgba(230, 162, 60, 0.15);
}
.vendor-name {
  font-weight: 600;
}
.ftn-footnote {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  background: rgba(230, 162, 60, 0.08);
  border: 1px solid rgba(230, 162, 60, 0.3);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
</style>
