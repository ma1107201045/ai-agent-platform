<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete, Plus, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import VarChips from './VarChips.vue'
import {
  DEFAULT_ADVANCED, NODE_TYPE_META, initialBranches, isMultiBranch, nextBranchKey
} from '@/utils/flow'
import type { VarItem } from '@/utils/flow'
import type { AppTool, ChatModelInfo, KnowledgeDataset, WorkflowNodeType } from '@/api/types'

/** 节点配置面板：按节点类型渲染差异化配置 + 通用执行策略 */
const props = defineProps<{
  /** Vue Flow 节点对象（直接修改其 data 完成编辑） */
  node: any
  /** 画布连线数组（可写：分支改名/删除时同步连线） */
  edges: any[]
  chatModels: ChatModelInfo[]
  rerankModels: ChatModelInfo[]
  datasets: KnowledgeDataset[]
  tools: AppTool[]
  /** 当前节点可引用的变量 */
  vars: VarItem[]
}>()

const type = computed<WorkflowNodeType>(() => props.node?.data?.nodeType)
const meta = computed(() => NODE_TYPE_META[type.value])

// ---------- 配置对象（与 node.data.config 双向绑定） ----------
const cfg = ref<Record<string, any>>({})

// ---------- HTTP Headers（JSON 文本 <-> 对象） ----------
// 必须先于下方 immediate watch 声明：watch 在 setup 期间同步执行回调并调用 syncHeadersText
const headersText = ref('')

function syncHeadersText() {
  const h = cfg.value?.headers
  headersText.value = h && typeof h === 'object' ? JSON.stringify(h, null, 2) : ''
}

function parseHeaders() {
  const t = headersText.value.trim()
  if (!t) {
    delete cfg.value.headers
    return
  }
  try {
    const parsed = JSON.parse(t)
    if (typeof parsed !== 'object' || Array.isArray(parsed) || parsed === null) {
      ElMessage.warning('Headers 必须是 JSON 对象')
      return
    }
    cfg.value.headers = parsed
  } catch {
    ElMessage.warning('Headers 必须是合法 JSON 对象')
  }
}

watch(
  () => props.node?.data,
  (data) => {
    if (!data) return
    if (!data.config || typeof data.config !== 'object') data.config = {}
    cfg.value = data.config
    // 补齐执行策略默认值（起止节点无执行策略）
    if (data.nodeType !== 'start' && data.nodeType !== 'end') {
      for (const [k, v] of Object.entries(DEFAULT_ADVANCED)) {
        if (cfg.value[k] === undefined || cfg.value[k] === null || cfg.value[k] === '') {
          cfg.value[k] = v
        }
      }
    }
    syncHeadersText()
  },
  { immediate: true }
)

watch(
  cfg,
  (v) => {
    if (props.node?.data) props.node.data.config = v
  },
  { deep: true, immediate: true }
)

// ---------- 开始节点：流程变量 ----------
const variables = computed<Array<{ name: string; value: string }>>(() => {
  const v = cfg.value.variables
  return Array.isArray(v) ? v : []
})

function addVariable() {
  const list = variables.value.slice()
  list.push({ name: '', value: '' })
  cfg.value.variables = list
}

function removeVariable(i: number) {
  cfg.value.variables = variables.value.filter((_, idx) => idx !== i)
}

// ---------- 条件节点：多分支 ----------
const multiBranch = computed(() => isMultiBranch(props.node?.data))
/** 分支列表：直接引用 config.branches 中的对象，保证表单编辑可写回 */
const branches = computed<Array<{ key: string; label: string; expression: string }>>(() => {
  const list = cfg.value.branches
  return Array.isArray(list) ? list : []
})

function toggleMulti(val: boolean) {
  if (val) {
    cfg.value.branches = initialBranches()
    delete cfg.value.expression
  } else {
    const keys = branches.value.map((b) => b.key)
    delete cfg.value.branches
    cfg.value.expression = ''
    removeEdgesOf(keys)
  }
  syncBranchEdgeLabels()
}

function addBranch() {
  const list = branches.value.slice()
  list.push({ key: nextBranchKey(props.node?.data), label: `分支${list.length + 1}`, expression: '' })
  cfg.value.branches = list
}

function removeBranch(i: number) {
  const list = branches.value.slice()
  const [removed] = list.splice(i, 1)
  cfg.value.branches = list
  if (removed) removeEdgesOf([removed.key])
}

/** 删除指定 handle 上的全部出边 */
function removeEdgesOf(keys: string[]) {
  if (!keys.length) return
  for (let i = props.edges.length - 1; i >= 0; i--) {
    const e = props.edges[i]
    if (e.source === props.node?.id && keys.includes(e.sourceHandle)) {
      props.edges.splice(i, 1)
    }
  }
}

/** 分支改名后同步连线标签 */
function syncBranchEdgeLabels() {
  for (const b of branches.value) {
    for (const e of props.edges) {
      if (e.source === props.node?.id && e.sourceHandle === b.key) {
        e.label = b.label || b.key
      }
    }
  }
}

watch(
  () => branches.value.map((b) => `${b.key}:${b.label}`).join('|'),
  () => syncBranchEdgeLabels()
)

const conditionPlaceholder =
  '支持比较表达式，字符串请加引号\n示例：{{input}} 非空即真\n示例：\'{{node1}}\' == \'成功\'\n示例：{{count}} >= 3\n示例：\'{{input}}\' contains \'关键\''

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']

/** 提示文案：Vue 文本节点中不能直接书写 {{}}，故以常量形式渲染 */
const TIP_VAR_USAGE = '变量渲染后写入全局，下游节点可用 {{变量名}} 引用；常用于抽取、改写用户输入。'
const TIP_CHUNK_PLACEHOLDERS = '可用占位：{{index}} {{content}} {{score}} {{documentId}}'
const TIP_OUTPUT_VAR = '填写后下游可用 {{别名}} 引用本节点输出'
const TIP_SCRIPT_VARS = '脚本内可直接使用 input（用户输入）、outputs（全部节点输出集合）以及各上游节点 id 变量。'
const TIP_CONDITION = '全部条件不成立时走「默认」分支；表达式为空的分支视为命中。'
const TIP_BRANCH_OFF = '表达式为真走「是」分支，为假走「否」分支。'
</script>

<template>
  <div v-if="node?.data" class="node-config">
    <div class="config-node-head">
      <div class="node-icon" :style="{ background: meta.gradient }">
        <span class="node-icon-text">{{ meta.label.slice(0, 1) }}</span>
      </div>
      <div class="config-node-head-text">
        <div class="config-node-type">{{ meta.label }} · {{ meta.desc }}</div>
        <div class="config-node-name">{{ node.data.label }}</div>
      </div>
    </div>

    <el-form label-position="top" size="small">
      <el-form-item label="节点名称">
        <el-input v-model="node.data.label" />
      </el-form-item>

      <!-- ---------- 开始 ---------- -->
      <template v-if="type === 'start'">
        <el-divider content-position="left">流程变量</el-divider>
        <div class="kv-list">
          <div v-for="(v, i) in variables" :key="i" class="kv-row">
            <el-input v-model="v.name" placeholder="变量名" class="kv-name" />
            <el-input v-model="v.value" placeholder="变量值，支持 {{input}}" class="kv-value" />
            <el-button text circle :icon="Delete" @click="removeVariable(i)" />
          </div>
          <el-button size="small" plain :icon="Plus" @click="addVariable">添加变量</el-button>
        </div>
        <p class="field-tip">{{ TIP_VAR_USAGE }}</p>
        <el-form-item label="开场白">
          <el-input
            v-model="cfg.welcome"
            type="textarea"
            :rows="3"
            placeholder="对话开始时的欢迎语（前端展示用）"
          />
        </el-form-item>
      </template>

      <!-- ---------- 结束 ---------- -->
      <template v-else-if="type === 'end'">
        <el-form-item label="回答模板">
          <el-input
            v-model="cfg.answerTemplate"
            type="textarea"
            :rows="4"
            placeholder="留空则取上游节点输出；可引用 {{input}} 与 {{节点id}}"
          />
          <VarChips :vars="vars" :target="cfg" field="answerTemplate" />
        </el-form-item>
      </template>

      <!-- ---------- LLM ---------- -->
      <template v-else-if="type === 'llm'">
        <el-form-item label="模型">
          <el-select v-model="cfg.modelId" placeholder="选择对话模型" style="width: 100%">
            <el-option
              v-for="m in chatModels"
              :key="m.id"
              :label="`${m.providerName} / ${m.modelName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input
            v-model="cfg.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="设定模型角色与行为…"
          />
          <VarChips :vars="vars" :target="cfg" field="systemPrompt" />
        </el-form-item>
        <el-form-item label="用户提示词">
          <el-input
            v-model="cfg.userPrompt"
            type="textarea"
            :rows="3"
            placeholder="发送给模型的用户消息，默认 {{input}}"
          />
          <VarChips :vars="vars" :target="cfg" field="userPrompt" />
        </el-form-item>

        <el-divider content-position="left">采样参数</el-divider>
        <el-form-item label="温度">
          <el-slider v-model="cfg.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="Top P">
          <el-slider v-model="cfg.topP" :min="0" :max="1" :step="0.05" />
        </el-form-item>
        <el-form-item label="最大输出 Tokens">
          <el-input-number v-model="cfg.maxTokens" :min="0" :max="32768" :step="256" />
          <span class="field-tip inline">0 表示不限制</span>
        </el-form-item>
        <el-form-item label="输出格式">
          <el-select v-model="cfg.outputFormat" style="width: 100%">
            <el-option label="纯文本" value="text" />
            <el-option label="JSON 对象" value="json" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">知识库增强</el-divider>
        <el-form-item label="关联数据集">
          <el-select v-model="cfg.datasetId" placeholder="不使用知识库" clearable style="width: 100%">
            <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <template v-if="cfg.datasetId">
          <el-form-item label="检索词模板">
            <el-input v-model="cfg.queryTemplate" placeholder="默认 {{input}}，可引用上游节点输出" />
            <VarChips :vars="vars" :target="cfg" field="queryTemplate" />
          </el-form-item>
          <el-form-item label="召回数量">
            <el-input-number v-model="cfg.topK" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="重排模型">
            <el-select v-model="cfg.rerankModelId" placeholder="不使用重排" clearable style="width: 100%">
              <el-option
                v-for="m in rerankModels"
                :key="m.id"
                :label="`${m.providerName} / ${m.modelName}`"
                :value="m.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="相似度阈值">
            <el-slider v-model="cfg.scoreThreshold" :min="0" :max="1" :step="0.05" />
          </el-form-item>
          <el-form-item label="上下文模板">
            <el-input
              v-model="cfg.knowledgeTemplate"
              type="textarea"
              :rows="3"
              placeholder="留空使用默认；用 {{context}} 表示命中片段位置"
            />
          </el-form-item>
        </template>
      </template>

      <!-- ---------- Agent ---------- -->
      <template v-else-if="type === 'agent'">
        <el-form-item label="模型">
          <el-select v-model="cfg.modelId" placeholder="选择对话模型" style="width: 100%">
            <el-option
              v-for="m in chatModels"
              :key="m.id"
              :label="`${m.providerName} / ${m.modelName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input
            v-model="cfg.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="设定 Agent 角色与工具使用策略…"
          />
          <VarChips :vars="vars" :target="cfg" field="systemPrompt" />
        </el-form-item>
        <el-form-item label="用户提示词">
          <el-input v-model="cfg.userPrompt" type="textarea" :rows="3" placeholder="默认 {{input}}" />
          <VarChips :vars="vars" :target="cfg" field="userPrompt" />
        </el-form-item>
        <el-form-item label="可用工具">
          <el-select
            v-model="cfg.toolIds"
            multiple
            filterable
            placeholder="不选则使用应用绑定工具"
            style="width: 100%"
          >
            <el-option v-for="t in tools" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联数据集">
          <el-select
            v-model="cfg.datasetIds"
            multiple
            filterable
            placeholder="不选则使用应用绑定数据集"
            style="width: 100%"
          >
            <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大循环轮数">
          <el-input-number v-model="cfg.maxIterations" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="输出附带工具调用过程">
          <el-switch v-model="cfg.includeSteps" />
          <span class="field-tip inline">开启后节点输出末尾追加每步工具调用与结果</span>
        </el-form-item>
      </template>

      <!-- ---------- 条件分支 ---------- -->
      <template v-else-if="type === 'condition'">
        <el-form-item label="多分支模式">
          <el-switch :model-value="multiBranch" @update:model-value="toggleMulti" />
          <span class="field-tip inline">关闭为 是/否 二分分支，开启可配置任意条分支 + 默认分支</span>
        </el-form-item>

        <template v-if="multiBranch">
          <el-divider content-position="left">分支规则（自上而下命中即止）</el-divider>
          <div class="branch-list">
            <div v-for="(b, i) in branches" :key="b.key" class="branch-item">
              <div class="branch-item-head">
                <el-tag size="small" effect="light" type="primary">{{ b.key }}</el-tag>
                <el-input v-model="b.label" placeholder="分支名" size="small" class="branch-label" />
                <el-button text circle size="small" :icon="Delete" @click="removeBranch(i)" />
              </div>
              <el-input
                v-model="b.expression"
                type="textarea"
                :rows="2"
                :placeholder="conditionPlaceholder"
              />
              <VarChips :vars="vars" :target="b" field="expression" />
            </div>
            <el-button size="small" plain :icon="Plus" @click="addBranch">添加分支</el-button>
          </div>
          <p class="field-tip">{{ TIP_CONDITION }}</p>
        </template>
        <template v-else>
          <el-form-item label="判断条件">
            <el-input v-model="cfg.expression" type="textarea" :rows="2" :placeholder="conditionPlaceholder" />
            <VarChips :vars="vars" :target="cfg" field="expression" />
          </el-form-item>
          <p class="field-tip">{{ TIP_BRANCH_OFF }}</p>
        </template>
      </template>

      <!-- ---------- 表达式计算 ---------- -->
      <template v-else-if="type === 'code'">
        <el-form-item label="表达式脚本">
          <el-input
            v-model="cfg.code"
            type="textarea"
            :rows="10"
            placeholder="MVEL 表达式，可用 input / outputs / 各节点输出变量，最后 return 结果&#10;示例：return input.trim().toUpperCase()"
          />
          <VarChips :vars="vars" :target="cfg" field="code" />
        </el-form-item>
        <p class="field-tip">{{ TIP_SCRIPT_VARS }}</p>
      </template>

      <!-- ---------- HTTP ---------- -->
      <template v-else-if="type === 'http'">
        <el-form-item label="请求地址">
          <el-input v-model="cfg.url" placeholder="https://api.example.com/path（支持 {{input}} 变量）" />
          <VarChips :vars="vars" :target="cfg" field="url" />
        </el-form-item>
        <el-form-item label="请求方式">
          <el-select v-model="cfg.method" style="width: 100%">
            <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="自定义 Headers">
          <el-input
            v-model="headersText"
            type="textarea"
            :rows="3"
            placeholder='JSON 格式，如 {"X-Api-Key":"xxx"}，值支持 {{input}} 变量'
            @blur="parseHeaders"
          />
        </el-form-item>

        <el-divider content-position="left">鉴权</el-divider>
        <el-form-item label="鉴权方式">
          <el-select v-model="cfg.authType" style="width: 100%">
            <el-option label="无" value="none" />
            <el-option label="Bearer Token" value="bearer" />
            <el-option label="Basic" value="basic" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="cfg.authType === 'bearer'" label="Token">
          <el-input v-model="cfg.authToken" placeholder="Bearer Token" />
        </el-form-item>
        <template v-if="cfg.authType === 'basic'">
          <el-form-item label="用户名">
            <el-input v-model="cfg.authUsername" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="cfg.authPassword" type="password" show-password />
          </el-form-item>
        </template>

        <el-divider content-position="left">请求体</el-divider>
        <el-form-item label="请求体类型">
          <el-select v-model="cfg.bodyType" style="width: 100%">
            <el-option label="无（GET/DELETE 常用）" value="none" />
            <el-option label="JSON" value="json" />
            <el-option label="表单 form-urlencoded" value="form" />
            <el-option label="纯文本 raw" value="raw" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="cfg.bodyType !== 'none'" label="请求体模板">
          <el-input
            v-model="cfg.bodyTemplate"
            type="textarea"
            :rows="5"
            :placeholder="
              cfg.bodyType === 'form'
                ? 'JSON 对象或 k1=v1&k2=v2 形式，支持变量'
                : '请求体内容，支持变量插值'
            "
          />
          <VarChips :vars="vars" :target="cfg" field="bodyTemplate" />
        </el-form-item>

        <el-divider content-position="left">响应处理</el-divider>
        <el-form-item label="响应类型">
          <el-select v-model="cfg.responseType" style="width: 100%">
            <el-option label="纯文本（原样输出）" value="text" />
            <el-option label="JSON（可抽取字段）" value="json" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="cfg.responseType === 'json'" label="字段路径">
          <el-input v-model="cfg.jsonPath" placeholder="如 data.choices[0].text；留空返回完整 JSON" />
        </el-form-item>
        <el-form-item label="忽略非 2xx 状态码">
          <el-switch v-model="cfg.ignoreStatus" />
          <span class="field-tip inline">开启后即使返回 4xx/5xx 也作为正常输出继续流程</span>
        </el-form-item>
      </template>

      <!-- ---------- 模板 ---------- -->
      <template v-else-if="type === 'template'">
        <el-form-item label="模板内容">
          <el-input
            v-model="cfg.template"
            type="textarea"
            :rows="6"
            placeholder="支持 {{input}} 与 {{节点id}} 变量插值"
          />
          <VarChips :vars="vars" :target="cfg" field="template" />
        </el-form-item>
      </template>

      <!-- ---------- 知识库检索 ---------- -->
      <template v-else-if="type === 'knowledge'">
        <el-form-item label="关联数据集">
          <el-select v-model="cfg.datasetId" placeholder="请选择数据集" style="width: 100%">
            <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检索词模板">
          <el-input v-model="cfg.queryTemplate" placeholder="默认 {{input}}，可引用上游节点输出" />
          <VarChips :vars="vars" :target="cfg" field="queryTemplate" />
        </el-form-item>
        <el-form-item label="召回数量">
          <el-input-number v-model="cfg.topK" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="重排模型">
          <el-select v-model="cfg.rerankModelId" placeholder="不使用重排" clearable style="width: 100%">
            <el-option
              v-for="m in rerankModels"
              :key="m.id"
              :label="`${m.providerName} / ${m.modelName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="相似度阈值">
          <el-slider v-model="cfg.scoreThreshold" :min="0" :max="1" :step="0.05" />
        </el-form-item>

        <el-divider content-position="left">输出格式</el-divider>
        <el-form-item label="输出格式">
          <el-select v-model="cfg.outputFormat" style="width: 100%">
            <el-option label="文本拼接" value="text" />
            <el-option label="JSON 数组" value="json" />
          </el-select>
        </el-form-item>
        <template v-if="cfg.outputFormat !== 'json'">
          <el-form-item label="片段模板">
            <el-input
              v-model="cfg.itemTemplate"
              placeholder="默认 【片段 {{index}}】{{content}}"
            />
            <span class="field-tip">{{ TIP_CHUNK_PLACEHOLDERS }}</span>
          </el-form-item>
          <el-form-item label="片段分隔符">
            <el-input v-model="cfg.separator" placeholder="默认两个换行" />
          </el-form-item>
        </template>
      </template>

      <!-- ---------- 执行策略（通用，起止节点除外） ---------- -->
      <template v-if="type !== 'start' && type !== 'end'">
        <el-divider content-position="left">
          <span class="divider-with-tip">
            执行策略
            <el-tooltip
              content="节点级的重试、超时与错误处理，由执行引擎统一实施"
              placement="top"
            >
              <el-icon class="divider-tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
        </el-divider>
        <el-form-item label="失败重试次数">
          <el-input-number v-model="cfg.retries" :min="0" :max="5" />
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="cfg.timeoutSeconds" :min="0" :max="300" />
          <span class="field-tip inline">秒，0 表示不限制</span>
        </el-form-item>
        <el-form-item label="出错时">
          <el-select v-model="cfg.onError" style="width: 100%">
            <el-option label="中断流程（下游跳过）" value="fail" />
            <el-option label="忽略错误继续执行" value="continue" />
            <el-option label="使用兜底输出继续" value="fallback" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="cfg.onError === 'fallback'" label="兜底输出">
          <el-input v-model="cfg.errorFallback" placeholder="出错时作为本节点输出继续流程" />
          <VarChips :vars="vars" :target="cfg" field="errorFallback" />
        </el-form-item>
        <el-form-item label="输出变量别名">
          <el-input v-model="cfg.outputVar" placeholder="留空则用节点 ID 引用输出" />
          <span class="field-tip">{{ TIP_OUTPUT_VAR }}</span>
        </el-form-item>
      </template>

      <el-form-item label="节点备注">
        <el-input
          v-model="node.data.remark"
          type="textarea"
          :rows="2"
          placeholder="记录该节点的用途、注意事项（仅编辑端展示，不影响执行）"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.config-node-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 14px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--border-color);
  border-radius: 10px;
}
.node-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  box-shadow: inset 0 -2px 4px rgba(0, 0, 0, 0.1), 0 1px 3px rgba(0, 0, 0, 0.12);
}
.node-icon-text {
  font-size: 14px;
  font-weight: 700;
}
.config-node-head-text {
  min-width: 0;
}
.config-node-type {
  font-size: 11px;
  color: var(--text-tertiary);
}
.config-node-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.el-form-item) {
  margin-bottom: 14px;
}
:deep(.el-form-item__label) {
  font-size: 12.5px;
  color: var(--text-secondary);
  font-weight: 500;
}
:deep(.el-divider__text) {
  font-size: 12px;
  color: var(--brand-1);
}
.field-tip {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
.field-tip.inline {
  margin-left: 8px;
}
.field-tip code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  background: #f5f7fa;
  padding: 0 4px;
  border-radius: 4px;
}
.divider-with-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.divider-tip-icon {
  color: var(--text-tertiary);
}
.kv-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}
.kv-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kv-name {
  width: 88px;
  flex-shrink: 0;
}
.kv-value {
  flex: 1;
  min-width: 0;
}
.branch-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 10px;
}
.branch-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px;
  background: #fafbfc;
}
.branch-item-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.branch-label {
  flex: 1;
  min-width: 0;
}
</style>
