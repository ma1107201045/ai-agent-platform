<script setup lang="ts">
import { computed, markRaw, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Aim, ArrowLeft, CircleCheck, Clock, Close, CopyDocument, Cpu, Delete, Document,
  Expand, Files, Fold, Link as LinkIcon, MagicStick, Notebook, Promotion, QuestionFilled,
  Rank, RefreshLeft, RefreshRight, Share, VideoPlay
} from '@element-plus/icons-vue'
import { VueFlow, useVueFlow, Handle, Position } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { appApi } from '@/api/app'
import { knowledgeApi } from '@/api/knowledge'
import { llmApi } from '@/api/llm'
import { toolApi } from '@/api/tool'
import type { AppVersion, AppTool, ChatModelInfo, KnowledgeDataset, RunResult, TraceItem, WorkflowNodeType } from '@/api/types'
import {
  NODE_TYPE_META, dslToFlow, flowToDsl, genNodeId
} from '@/utils/flow'

const route = useRoute()
const router = useRouter()
const appId = Number(route.params.id)

// ---------- 画布 ----------
const nodes = ref<Array<any>>([])
const edges = ref<Array<any>>([])
const { addNodes, removeNodes, onConnect, setCenter, fitView } = useVueFlow()

// ---------- 历史（撤销/重做） ----------
const MAX_HISTORY = 50
const historyStack = ref<Array<{ nodes: any[]; edges: any[] }>>([])
const historyIndex = ref(-1)
let restoring = false

/** 记录当前画布状态到历史栈 */
function snapshot() {
  if (restoring) return
  historyStack.value = historyStack.value.slice(0, historyIndex.value + 1)
  historyStack.value.push({
    nodes: JSON.parse(JSON.stringify(nodes.value)),
    edges: JSON.parse(JSON.stringify(edges.value))
  })
  if (historyStack.value.length > MAX_HISTORY) historyStack.value.shift()
  historyIndex.value = historyStack.value.length - 1
}

function restore(state: { nodes: any[]; edges: any[] }) {
  restoring = true
  nodes.value = JSON.parse(JSON.stringify(state.nodes))
  edges.value = JSON.parse(JSON.stringify(state.edges))
  restoring = false
  selectedNodeId.value = null
  selectedEdgeId.value = null
  editBaseline = ''
}

function undo() {
  if (historyIndex.value <= 0) return
  historyIndex.value -= 1
  restore(historyStack.value[historyIndex.value])
}

function redo() {
  if (historyIndex.value >= historyStack.value.length - 1) return
  historyIndex.value += 1
  restore(historyStack.value[historyIndex.value])
}

// ---------- 复制 / 粘贴 ----------
const clipboard = ref<Array<{ id: string; data: any }> | null>(null)

/** 复制当前选中的节点（含节点间内部连线关系） */
function copySelected() {
  const sel = nodes.value.filter((n) => n.selected)
  if (sel.length === 0) {
    ElMessage.warning('请先选中要复制的节点（支持框选 / Ctrl 多选）')
    return
  }
  clipboard.value = sel.map((n) => ({
    id: n.id,
    data: JSON.parse(JSON.stringify(n.data))
  }))
  ElMessage.success(`已复制 ${sel.length} 个节点，按 Ctrl+V 粘贴`)
}

/** 粘贴剪贴板节点到画布（自动重映射节点 id 与内部连线） */
function pasteClipboard() {
  if (!clipboard.value || clipboard.value.length === 0) {
    ElMessage.warning('剪贴板为空，请先复制节点')
    return
  }
  const idMap: Record<string, string> = {}
  const baseX = 60 + (Math.random() * 60)
  const baseY = 60 + (Math.random() * 60)
  const newNodes = clipboard.value.map((src, i) => {
    const id = genNodeId()
    idMap[src.id] = id
    const data = JSON.parse(JSON.stringify(src.data))
    delete data.runStatus
    delete data.runCost
    delete data.runError
    delete data.selected
    return {
      id,
      type: 'flow-node',
      position: { x: baseX + i * 40, y: baseY + i * 40 },
      data
    }
  })
  const idSet = new Set(clipboard.value.map((c) => c.id))
  const newEdges: Array<any> = []
  for (const e of edges.value) {
    if (idSet.has(e.source) && idSet.has(e.target)) {
      newEdges.push({
        id: `edge-${Date.now()}-${newEdges.length}`,
        source: idMap[e.source],
        target: idMap[e.target],
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle,
        label: e.label,
        ...(e.sourceHandle === 'true' || e.sourceHandle === 'false'
          ? branchLabelStyle(e.sourceHandle)
          : {})
      })
    }
  }
  addNodes(newNodes)
  edges.value = [...edges.value, ...newEdges]
  snapshot()
  ElMessage.success(`已粘贴 ${newNodes.length} 个节点`)
}

// ---------- 数据 ----------
const appName = ref('')
const appType = ref('chatflow')
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const chatModels = ref<ChatModelInfo[]>([])
const datasets = ref<KnowledgeDataset[]>([])
const allTools = ref<AppTool[]>([])
const boundToolIds = ref<number[]>([])
const boundDatasetIds = ref<number[]>([])
const welcomeMessage = ref('')
const openingQuestionsText = ref('')
const saving = ref(false)
const urlPlaceholder = 'https://api.example.com/path（支持 {{input}} 变量）'
const headersPlaceholder = 'JSON 格式，如 X-Api-Key: xxx，支持 {{input}} 变量'
const conditionPlaceholder =
  '支持比较表达式，字符串请加引号&#10;示例：{{input}} 非空即真&#10;示例：\'{{node1}}\' == \'成功\'&#10;示例：{{count}} >= 3&#10;示例：\'{{input}}\' contains \'关键\''
const defaultConfigs: Partial<Record<WorkflowNodeType, Record<string, unknown>>> = {
  template: { template: '{{input}}' },
  knowledge: { topK: 3, queryTemplate: '{{input}}' },
  agent: { maxIterations: 6 }
}
const publishing = ref(false)

// ---------- 面板折叠 ----------
const configCollapsed = ref(false)

/** 左侧节点面板分组（按功能归类，不影响拖拽与数据模型） */
const paletteGroups: Array<{ title: string; types: WorkflowNodeType[] }> = [
  { title: '流程控制', types: ['start', 'end', 'condition'] },
  { title: '处理节点', types: ['llm', 'agent', 'template', 'code'] },
  { title: '外部数据', types: ['http', 'knowledge'] }
]

/** 确保画布存在开始/结束节点（空画布进入 / 被删光后恢复） */
function ensureStartEnd() {
  const hasStart = nodes.value.some((n) => n.data.nodeType === 'start')
  const hasEnd = nodes.value.some((n) => n.data.nodeType === 'end')
  if (hasStart && hasEnd) return
  const add = (type: 'start' | 'end', x: number, y: number) => {
    const id = genNodeId()
    nodes.value.push({
      id,
      type: 'flow-node',
      position: { x, y },
      data: { label: NODE_TYPE_META[type].label, nodeType: type, config: {} }
    })
    return id
  }
  const bothEmpty = !hasStart && !hasEnd
  const startId = hasStart
    ? nodes.value.find((n) => n.data.nodeType === 'start')!.id
    : add('start', 120, 260)
  const endId = hasEnd
    ? nodes.value.find((n) => n.data.nodeType === 'end')!.id
    : add('end', 460, 260)
  if (bothEmpty) {
    edges.value.push({ id: genNodeId('edge'), source: startId, target: endId })
  }
  snapshot()
}

/** 点击节点右侧「+」：弹出节点选择浮层（Dify 式） */
const addMenuFor = ref<{ x: number; y: number; sourceId: string; handle: string | null } | null>(null)

function openAddMenu(e: MouseEvent, sourceId: string, handle: string | null) {
  e.stopPropagation()
  e.preventDefault()
  // 菜单 240x330 左右，靠近视口边缘时回弹，避免溢出
  let x = e.clientX
  let y = e.clientY
  if (x > window.innerWidth - 250) x = window.innerWidth - 250
  if (y > window.innerHeight - 340) y = Math.max(8, window.innerHeight - 340)
  addMenuFor.value = { x, y, sourceId, handle }
}

function closeAddMenu() {
  addMenuFor.value = null
}

function onAddNodeClick(type: WorkflowNodeType) {
  if (!addMenuFor.value) return
  addNodeFromSource(addMenuFor.value.sourceId, addMenuFor.value.handle, type)
  addMenuFor.value = null
}

/** 从指定节点出点新增节点并自动连线（Dify 式插入） */
function addNodeFromSource(sourceId: string, sourceHandle: string | null, type: WorkflowNodeType) {
  if (type === 'start') return
  const src = nodes.value.find((n) => n.id === sourceId)
  if (!src) return
  const newId = genNodeId()
  const NODE_W = 210
  const GAP_X = 130
  const x = Math.round(src.position.x + NODE_W + GAP_X)
  let y = Math.round(src.position.y)
  if (sourceHandle === 'true') y -= 60
  else if (sourceHandle === 'false') y += 60
  nodes.value.push({
    id: newId,
    type: 'flow-node',
    position: { x, y },
    data: {
      label: NODE_TYPE_META[type].label,
      nodeType: type,
      config: { ...(defaultConfigs[type] ?? {}) }
    }
  })
  const endNode = nodes.value.find((n) => n.data.nodeType === 'end')
  const srcOuts = edges.value.filter((e) => e.source === sourceId)
  const directEnd = srcOuts.filter((e) => e.target === endNode?.id)
  // source 唯一出边直连 end → 替换为 source→new→end；否则新节点平连 end
  if (srcOuts.length === 1 && directEnd.length === 1 && endNode) {
    edges.value = edges.value.filter((e) => e.id !== directEnd[0].id)
    edges.value.push({ id: genNodeId('edge'), source: newId, target: endNode.id })
  } else if (endNode) {
    edges.value.push({ id: genNodeId('edge'), source: newId, target: endNode.id })
  }
  const isBranch = sourceHandle === 'true' || sourceHandle === 'false'
  edges.value.push({
    id: genNodeId('edge'),
    source: sourceId,
    target: newId,
    sourceHandle: sourceHandle ?? undefined,
    label: isBranch ? (sourceHandle === 'true' ? '是' : '否') : undefined,
    ...(isBranch ? branchLabelStyle(sourceHandle as 'true' | 'false') : {})
  })
  snapshot()
  // 选中新节点并定位
  selectedNodeId.value = newId
  const newNode = nodes.value.find((n) => n.id === newId)
  if (newNode) {
    editBaseline = JSON.stringify(newNode.data)
    nextTick(() => {
      setCenter(newNode.position.x + NODE_W / 2, newNode.position.y + 40, { zoom: 0.9 })
    })
  }
}

const selectedNode = computed(() =>
  nodes.value.find((n) => n.id === selectedNodeId.value) ?? null
)
const selectedData = computed(() => selectedNode.value?.data ?? null)
const isStartNode = computed(() => selectedData.value?.nodeType === 'start')
const hasSelection = computed(
  () => nodes.value.some((n) => n.selected) || edges.value.some((e) => e.selected)
)
const selectedEdge = computed(() => edges.value.find((e) => e.id === selectedEdgeId.value) ?? null)
const selectedEdgeEnds = computed(() => {
  const e = selectedEdge.value
  if (!e) return null
  const s = nodes.value.find((n) => n.id === e.source)
  const t = nodes.value.find((n) => n.id === e.target)
  return {
    srcType: s?.data?.nodeType ?? 'start',
    srcLabel: s?.data?.label ?? e.source,
    tgtType: t?.data?.nodeType ?? 'end',
    tgtLabel: t?.data?.label ?? e.target,
    branch: e.sourceHandle === 'true' ? '是' : e.sourceHandle === 'false' ? '否' : null
  }
})
const selectedCount = computed(() => nodes.value.filter((n) => n.selected).length)
const shortcuts = [
  { keys: 'Ctrl + Z', desc: '撤销' },
  { keys: 'Ctrl + Shift + Z', desc: '重做' },
  { keys: 'Ctrl + C', desc: '复制选中节点' },
  { keys: 'Ctrl + V', desc: '粘贴节点' },
  { keys: 'Delete', desc: '删除选中节点 / 连线' },
  { keys: '拖拽画布空白', desc: '框选多个节点' },
  { keys: 'Ctrl / Cmd + 点击', desc: '追加多选' },
  { keys: '对齐 / 分布', desc: '工具栏对齐、等距分布选中节点' },
  { keys: '自动布局', desc: '一键按流程层级重排全部节点' }
]

// ---------- 未保存修改追踪 ----------
const dirty = ref(false)
const hydrated = ref(false)
let editBaseline = '' // 当前选中节点的内容基线（区分编辑与切换选中）

watch(
  [nodes, edges, boundToolIds, boundDatasetIds, welcomeMessage, openingQuestionsText],
  () => {
    if (hydrated.value) dirty.value = true
  },
  { deep: true }
)
watch(selectedData, (val) => {
  if (!hydrated.value) return
  if (JSON.stringify(val ?? null) !== editBaseline) dirty.value = true
}, { deep: true })

/** HTTP 节点 Headers 文本（JSON 字符串 <-> config.headers 对象） */
const headersText = ref('')

function syncHeadersText() {
  const h = selectedData.value?.config?.headers
  headersText.value = h && typeof h === 'object' ? JSON.stringify(h, null, 2) : ''
}

function parseHeaders() {
  const t = headersText.value.trim()
  if (!t) {
    if (selectedData.value?.config) delete selectedData.value.config.headers
    return
  }
  try {
    selectedData.value.config.headers = JSON.parse(t)
  } catch {
    ElMessage.warning('Headers 必须是合法 JSON 对象')
  }
}

// 动态图标组件缓存
const iconMap: Record<string, any> = {}
function iconOf(name: string) {
  if (!iconMap[name]) {
    const map: Record<string, any> = {
      Promotion, CircleCheck, Cpu, MagicStick, Share, Document, Link: LinkIcon
    }
    iconMap[name] = markRaw(map[name] || Cpu)
  }
  return iconMap[name]
}

function metaOf(type: WorkflowNodeType) {
  return NODE_TYPE_META[type]
}

/** MiniMap 节点颜色：按节点类型取主题色 */
function miniMapNodeColor(node: any) {
  const type = node?.data?.nodeType as WorkflowNodeType | undefined
  return type && NODE_TYPE_META[type] ? NODE_TYPE_META[type].color : '#d1d5db'
}

// ---------- 加载 ----------
async function loadApp() {
  hydrated.value = false
  const app = await appApi.get(appId)
  appName.value = app.name
  appType.value = app.type || 'chatflow'
  if (app.toolIds) {
    try {
      boundToolIds.value = JSON.parse(app.toolIds) as number[]
    } catch {
      boundToolIds.value = []
    }
  }
  if (app.datasetIds) {
    try {
      boundDatasetIds.value = JSON.parse(app.datasetIds) as number[]
    } catch {
      boundDatasetIds.value = []
    }
  }
  welcomeMessage.value = app.welcomeMessage || ''
  if (app.openingQuestions) {
    try {
      openingQuestionsText.value = (JSON.parse(app.openingQuestions) as string[]).join('\n')
    } catch {
      openingQuestionsText.value = ''
    }
  }
  const { nodes: ns, edges: es } = dslToFlow(app.workflowJson)
  nodes.value = ns
  edges.value = es
  syncBranchEdges()
  if (ns.length === 0) {
    // Dify 式：空画布预置 开始/结束 节点并直连
    const startId = genNodeId()
    const endId = genNodeId()
    nodes.value.push({
      id: startId,
      type: 'flow-node',
      position: { x: 120, y: 260 },
      data: { label: '开始', nodeType: 'start', config: {} }
    })
    nodes.value.push({
      id: endId,
      type: 'flow-node',
      position: { x: 460, y: 260 },
      data: { label: '结束', nodeType: 'end', config: {} }
    })
    edges.value.push({ id: genNodeId('edge'), source: startId, target: endId })
  }
  nextTick(() => {
    hydrated.value = true
    dirty.value = false
    if (ns.length === 0) {
      setCenter(290, 300, { zoom: 0.85 })
    } else {
      fitView({ padding: 0.2, duration: 300 })
    }
  })
}

async function loadModels() {
  chatModels.value = await llmApi.chatModels()
}

async function loadTools() {
  try {
    allTools.value = await toolApi.enabled()
  } catch {
    allTools.value = []
  }
}

async function loadDatasets() {
  try {
    datasets.value = (await knowledgeApi.datasetPage({ size: 100 })).records
  } catch {
    datasets.value = []
  }
}

// ---------- 节点操作 ----------
function onNodeClick({ node }: any) {
  selectedNodeId.value = node.id
  selectedEdgeId.value = null
  // 同步更新内容基线：避免 watch(selectedData) 把"切换选中"误判为"编辑"
  editBaseline = JSON.stringify(node.data ?? null)
  nextTick(syncHeadersText)
}

function onPaneClick() {
  selectedNodeId.value = null
  selectedEdgeId.value = null
  headersText.value = ''
}

function onEdgeClick({ edge }: any) {
  selectedEdgeId.value = edge.id
  selectedNodeId.value = null
  headersText.value = ''
}

/** 节点拖动结束：记录一次历史快照 */
function onNodeDragStop() {
  snapshot()
}

onConnect((params: any) => {
  const srcType = nodes.value.find((n) => n.id === params.source)?.data?.nodeType
  const tgtType = nodes.value.find((n) => n.id === params.target)?.data?.nodeType
  if (srcType === 'end') {
    ElMessage.warning('「结束」节点不能连接出边')
    return
  }
  if (tgtType === 'start') {
    ElMessage.warning('「开始」节点不能有入边')
    return
  }
  if (params.source === params.target) {
    ElMessage.warning('不能将节点连接到自身')
    return
  }
  const dup = edges.value.some(
    (e) =>
      e.source === params.source &&
      e.target === params.target &&
      e.sourceHandle === params.sourceHandle &&
      e.targetHandle === params.targetHandle
  )
  if (dup) {
    ElMessage.warning('已存在相同的连线')
    return
  }
  const isBranch = params.sourceHandle === 'true' || params.sourceHandle === 'false'
  edges.value.push({
    id: genNodeId('edge'),
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    targetHandle: params.targetHandle,
    label: isBranch ? (params.sourceHandle === 'true' ? '是' : '否') : undefined,
    ...(isBranch ? branchLabelStyle(params.sourceHandle) : {})
  })
  snapshot()
})

// ---------- 条件分支双出边 ----------
/** condition 分支出边样式（true=绿 / false=红），Vue Flow 内置 label 渲染 */
function branchLabelStyle(handle: string) {
  const color = handle === 'true' ? '#67c23a' : '#f56c6c'
  return {
    labelStyle: {
      color,
      fontWeight: '600',
      fontSize: '11px',
      background: '#ffffff',
      border: `1px solid ${color}`,
      borderRadius: '8px',
      padding: '1px 6px'
    },
    labelShowBg: false,
    labelBgStyle: { fill: 'transparent' }
  }
}

/** 为 condition 分支边补齐默认标签与样式（历史数据 / 回滚后无 labelStyle 时调用） */
function syncBranchEdges() {
  for (const e of edges.value) {
    if (e.sourceHandle === 'true' || e.sourceHandle === 'false') {
      if (!e.label) e.label = e.sourceHandle === 'true' ? '是' : '否'
      Object.assign(e, branchLabelStyle(e.sourceHandle))
    }
  }
}

function removeSelected() {
  const selNodes = nodes.value.filter((n) => n.selected).map((n) => n.id)
  const selEdges = edges.value.filter((e) => e.selected).map((e) => e.id)
  if (selNodes.length === 0 && selEdges.length === 0) return
  if (selNodes.length > 0) {
    removeNodes(selNodes)
    edges.value = edges.value.filter(
      (e) => !selNodes.includes(e.source) && !selNodes.includes(e.target)
    )
  }
  if (selEdges.length > 0) {
    edges.value = edges.value.filter((e) => !selEdges.includes(e.id))
  }
  selectedNodeId.value = null
  selectedEdgeId.value = null
  snapshot()
}

function handleKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement
  if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return
  const mod = e.ctrlKey || e.metaKey
  if (mod && (e.key === 'z' || e.key === 'Z')) {
    e.preventDefault()
    e.shiftKey ? redo() : undo()
    return
  }
  if (mod && (e.key === 'y' || e.key === 'Y')) {
    e.preventDefault()
    redo()
    return
  }
  if (mod && (e.key === 'c' || e.key === 'C')) {
    e.preventDefault()
    copySelected()
    return
  }
  if (mod && (e.key === 'v' || e.key === 'V')) {
    e.preventDefault()
    pasteClipboard()
    return
  }
  if (e.key === 'Delete' || e.key === 'Backspace') {
    removeSelected()
  }
}

// ---------- 节点配置校验 ----------
interface NodeWarning {
  severity: 'error' | 'warn'
  text: string
}

/** 节点配置完整性检查：llm/http/code 缺关键配置为 error，condition 空表达式为 warn */
function nodeWarnings(data: any): NodeWarning[] {
  const cfg = data?.config ?? {}
  const list: NodeWarning[] = []
  const str = (v: unknown) => (v == null ? '' : String(v))
  switch (data?.nodeType) {
    case 'llm':
      if (!cfg.modelId) list.push({ severity: 'error', text: '未配置模型' })
      break
    case 'agent':
      if (!cfg.modelId) list.push({ severity: 'error', text: '未配置模型' })
      break
    case 'http':
      if (!str(cfg.url).trim()) list.push({ severity: 'error', text: '未配置请求地址' })
      break
    case 'code':
      if (!str(cfg.code).trim()) list.push({ severity: 'error', text: '未配置代码脚本' })
      break
    case 'condition':
      if (!str(cfg.expression).trim()) list.push({ severity: 'warn', text: '未配置判断条件，默认走 true 分支' })
      break
    case 'template':
      if (!str(cfg.template).trim()) list.push({ severity: 'error', text: '未配置模板内容' })
      break
    case 'knowledge':
      if (!cfg.datasetId) list.push({ severity: 'error', text: '未选择数据集' })
      break
  }
  return list
}

function hasNodeError(data: any): boolean {
  return nodeWarnings(data).some((w) => w.severity === 'error')
}

/** 运行/发布前统一校验，返回存在 error 级缺失的节点列表 */
function validateWorkflow(): Array<{ node: any; text: string }> {
  const errors: Array<{ node: any; text: string }> = []
  for (const n of nodes.value) {
    for (const w of nodeWarnings(n.data)) {
      if (w.severity === 'error') errors.push({ node: n, text: `「${n.data?.label || '未命名'}」${w.text}` })
    }
  }
  return errors
}

// ---------- 结构健康检查 ----------
/** 检查流程结构问题：start/end 缺失或重复、孤立节点、循环依赖（返回警告文案列表） */
function checkStructure(): string[] {
  const issues: string[] = []
  const labelOf = (id: string) => nodes.value.find((n) => n.id === id)?.data?.label || id

  const startCount = nodes.value.filter((n) => n.data.nodeType === 'start').length
  const endCount = nodes.value.filter((n) => n.data.nodeType === 'end').length
  if (startCount === 0) issues.push('缺少「开始」节点，将默认从第一个节点开始执行')
  if (startCount > 1) issues.push('存在多个「开始」节点，仅第一个生效')
  if (endCount === 0) issues.push('缺少「结束」节点，流程执行到无出边的节点即终止')
  if (endCount > 1) issues.push('存在多个「结束」节点')

  // 孤立节点
  const hasIn = new Set(edges.value.map((e) => e.target))
  const hasOut = new Set(edges.value.map((e) => e.source))
  for (const n of nodes.value) {
    if (!hasIn.has(n.id) && !hasOut.has(n.id) && nodes.value.length > 1) {
      issues.push(`节点「${labelOf(n.id)}」未与任何节点连接（孤立节点）`)
    }
  }

  // 循环依赖检测（DFS 三色标记）
  const adj: Record<string, string[]> = {}
  for (const e of edges.value) (adj[e.source] ??= []).push(e.target)
  const color: Record<string, number> = {}
  const inCycle = new Set<string>()
  const dfs = (u: string, path: string[]) => {
    color[u] = 1
    for (const v of adj[u] ?? []) {
      if (color[v] === 1) {
        const startIdx = path.indexOf(v)
        path.slice(startIdx).concat(v).forEach((c) => inCycle.add(c))
      } else if (!color[v]) {
        dfs(v, path.concat(v))
      }
    }
    color[u] = 2
  }
  for (const n of nodes.value) if (!color[n.id]) dfs(n.id, [n.id])
  if (inCycle.size > 0) {
    const names = [...inCycle].map((id) => `「${labelOf(id)}」`).join('、')
    issues.push(`检测到循环依赖：${names}，执行到环时将被截断`)
  }
  return issues
}

// ---------- 对齐 / 分布 / 自动布局 ----------
type AlignMode = 'left' | 'hcenter' | 'right' | 'top' | 'vcenter' | 'bottom'
type DistributeMode = 'horizontal' | 'vertical'

/** 读取选中节点包围盒（尺寸未就绪时使用默认值） */
function selectedBoxes() {
  return nodes.value
    .filter((n) => n.selected)
    .map((n) => ({
      n,
      x: n.position.x,
      y: n.position.y,
      w: n.dimensions?.width || 210,
      h: n.dimensions?.height || 64
    }))
}

/** 对齐选中节点：以包围盒为基准，left/hcenter/right 对齐 X 轴，top/vcenter/bottom 对齐 Y 轴 */
function alignNodes(mode: AlignMode) {
  const boxes = selectedBoxes()
  if (boxes.length < 2) {
    ElMessage.warning('请至少选中 2 个节点再对齐')
    return
  }
  const minX = Math.min(...boxes.map((b) => b.x))
  const maxX = Math.max(...boxes.map((b) => b.x + b.w))
  const minY = Math.min(...boxes.map((b) => b.y))
  const maxY = Math.max(...boxes.map((b) => b.y + b.h))
  const avgCX = boxes.reduce((s, b) => s + b.x + b.w / 2, 0) / boxes.length
  const avgCY = boxes.reduce((s, b) => s + b.y + b.h / 2, 0) / boxes.length
  for (const b of boxes) {
    switch (mode) {
      case 'left': b.n.position.x = minX; break
      case 'hcenter': b.n.position.x = avgCX - b.w / 2; break
      case 'right': b.n.position.x = maxX - b.w; break
      case 'top': b.n.position.y = minY; break
      case 'vcenter': b.n.position.y = avgCY - b.h / 2; break
      case 'bottom': b.n.position.y = maxY - b.h; break
    }
  }
  snapshot()
  ElMessage.success('对齐完成')
}

/** 均匀分布选中节点：沿 X 轴（horizontal）或 Y 轴（vertical）保持首尾不动、间隙相等 */
function distributeNodes(mode: DistributeMode) {
  const boxes = selectedBoxes()
  if (boxes.length < 3) {
    ElMessage.warning('请至少选中 3 个节点再分布')
    return
  }
  if (mode === 'horizontal') {
    const sorted = [...boxes].sort((a, b) => a.x - b.x)
    const totalW = sorted.reduce((s, b) => s + b.w, 0)
    const span = sorted[sorted.length - 1].x + sorted[sorted.length - 1].w - sorted[0].x
    const gap = (span - totalW) / (sorted.length - 1)
    let x = sorted[0].x
    for (const b of sorted) {
      b.n.position.x = x
      x += b.w + gap
    }
  } else {
    const sorted = [...boxes].sort((a, b) => a.y - b.y)
    const totalH = sorted.reduce((s, b) => s + b.h, 0)
    const span = sorted[sorted.length - 1].y + sorted[sorted.length - 1].h - sorted[0].y
    const gap = (span - totalH) / (sorted.length - 1)
    let y = sorted[0].y
    for (const b of sorted) {
      b.n.position.y = y
      y += b.h + gap
    }
  }
  snapshot()
  ElMessage.success('分布完成')
}

/** 自动布局：从「开始」（无则首个节点）BFS 分层，按层逐列排列、层内纵向错开 */
function autoLayout() {
  if (nodes.value.length === 0) {
    ElMessage.warning('画布为空')
    return
  }
  const startId = nodes.value.find((n) => n.data.nodeType === 'start')?.id ?? nodes.value[0].id
  const depthMap: Record<string, number> = {}
  const queue: string[] = [startId]
  depthMap[startId] = 0
  const visited = new Set([startId])
  const adj: Record<string, string[]> = {}
  for (const e of edges.value) (adj[e.source] ??= []).push(e.target)
  while (queue.length) {
    const id = queue.shift()!
    for (const t of adj[id] ?? []) {
      if (!visited.has(t)) {
        visited.add(t)
        depthMap[t] = depthMap[id] + 1
        queue.push(t)
      }
    }
  }
  // 未可达节点（环外孤立 / 回边）追加到末尾层，保证全部落位
  let maxDepth = Math.max(0, ...Object.values(depthMap))
  for (const n of nodes.value) {
    if (!visited.has(n.id)) {
      visited.add(n.id)
      depthMap[n.id] = ++maxDepth
    }
  }
  const layers: Record<number, string[]> = {}
  for (const [id, d] of Object.entries(depthMap)) (layers[d] ??= []).push(id)
  const W = 210
  const H = 70
  const GAP_X = 120
  const GAP_Y = 40
  const MARGIN = 40
  for (const d of Object.keys(layers).map(Number).sort((a, b) => a - b)) {
    const ids = layers[d]
    const x = MARGIN + d * (W + GAP_X)
    ids.forEach((id, i) => {
      const n = nodes.value.find((x) => x.id === id)
      if (n) {
        n.position.x = Math.round(x)
        n.position.y = Math.round(MARGIN + i * (H + GAP_Y))
      }
    })
  }
  snapshot()
  ElMessage.success('已按流程层级完成自动布局')
}

// ---------- 保存 / 发布 ----------
async function saveDraft() {
  saving.value = true
  try {
    if (appType.value === 'agent') {
      await appApi.update(appId, {
        toolIds: JSON.stringify(boundToolIds.value),
        datasetIds: JSON.stringify(boundDatasetIds.value),
        welcomeMessage: welcomeMessage.value,
        openingQuestions: JSON.stringify(
          openingQuestionsText.value.split('\n').map((q) => q.trim()).filter(Boolean)
        )
      })
    } else {
      await appApi.update(appId, { workflowJson: flowToDsl(nodes.value, edges.value) })
    }
    ElMessage.success('草稿已保存')
    dirty.value = false
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (appType.value !== 'agent' && nodes.value.length === 0) {
    ElMessage.warning('画布为空，请先拖入节点编排工作流再发布')
    return
  }
  if (appType.value !== 'agent') {
    const errors = validateWorkflow()
    const structure = checkStructure()
    const allWarnings = [...structure, ...errors.map((e) => e.text)]
    if (allWarnings.length > 0) {
      const proceed = await ElMessageBox.confirm(
        `发布前检查到以下问题：\n${allWarnings.join('\n')}\n\n仍要继续发布吗？`,
        '发布前检查',
        { confirmButtonText: '仍要发布', cancelButtonText: '去完善', type: 'warning' }
      ).catch(() => false)
      if (!proceed) return
    }
  }
  await ElMessageBox.confirm('发布后将生成新版本快照并设为线上版本，确定发布？', '发布确认', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    type: 'warning'
  })
  publishing.value = true
  try {
    const workflowJson =
      appType.value === 'agent' ? JSON.stringify({ nodes: [], edges: [] }) : flowToDsl(nodes.value, edges.value)
    await appApi.publish(appId, { workflowJson, promptConfig: '' })
    ElMessage.success('发布成功')
  } finally {
    publishing.value = false
  }
}

function goChat() {
  router.push(`/apps/${appId}/chat`)
}

// ---------- 版本历史 ----------
const versionsVisible = ref(false)
const versions = ref<AppVersion[]>([])
const loadingVersions = ref(false)
const rollingBack = ref(false)

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 19)
}

async function openVersions() {
  versionsVisible.value = true
  loadingVersions.value = true
  try {
    versions.value = await appApi.versions(appId)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载版本列表失败')
  } finally {
    loadingVersions.value = false
  }
}

async function rollbackTo(version: AppVersion) {
  await ElMessageBox.confirm(
    `将把 v${version.version} 的工作流恢复到当前画布（草稿），不会自动发布，确认回滚？`,
    '回滚确认',
    { confirmButtonText: '回滚', cancelButtonText: '取消', type: 'warning' }
  )
  rollingBack.value = true
  try {
    await appApi.rollback(appId, version.id)
    ElMessage.success(`已回滚到 v${version.version}，请检查后重新发布`)
    versionsVisible.value = false
    await loadApp()
    clearRunState()
  } catch (e: any) {
    ElMessage.error(e?.message || '回滚失败')
  } finally {
    rollingBack.value = false
  }
}

// ---------- 运行调试 ----------
const debugVisible = ref(false)
const debugInput = ref('')
const running = ref(false)
const runResult = ref<RunResult | null>(null)
const highlightedNodeId = ref<string | null>(null)
const lastRunFailed = ref(false)

function toggleDebug() {
  debugVisible.value = !debugVisible.value
}

/** 清空画布运行态与调试结果 */
function clearRunState() {
  runResult.value = null
  highlightedNodeId.value = null
  lastRunFailed.value = false
  for (const n of nodes.value) {
    n.data = { ...n.data, runStatus: undefined, runCost: undefined, runError: undefined }
  }
  for (const e of edges.value) {
    e.class = ''
    e.style = undefined
  }
}

/** trace 状态 -> 节点着色 class */
function nodeRunClass(data: any) {
  const s = data?.runStatus
  if (s === 'success') return 'run-success'
  if (s === 'error') return 'run-error'
  if (s === 'skipped') return 'run-skipped'
  return ''
}

/** 将运行 trace 映射到画布节点 / 边 */
function applyTraceToCanvas(trace: TraceItem[]) {
  const statusMap: Record<string, string> = {}
  const costMap: Record<string, number> = {}
  const errMap: Record<string, string> = {}
  for (const t of trace) {
    statusMap[t.nodeId] = t.status
    costMap[t.nodeId] = t.costMs
    if (t.error) errMap[t.nodeId] = t.error
  }
  for (const n of nodes.value) {
    n.data = {
      ...n.data,
      runStatus: statusMap[n.id] || 'idle',
      runCost: costMap[n.id],
      runError: errMap[n.id]
    }
  }
  // 高亮执行路径上的边（trace 相邻节点间的连线）
  const executed = new Set<string>()
  for (let i = 0; i < trace.length - 1; i++) {
    const src = trace[i].nodeId
    const tgt = trace[i + 1].nodeId
    const edge = edges.value.find((e) => e.source === src && e.target === tgt)
    if (edge) executed.add(edge.id)
  }
  for (const e of edges.value) {
    const on = executed.has(e.id)
    e.class = on ? 'executed-edge' : ''
    e.style = on ? { stroke: '#67c23a', strokeWidth: 2.5 } : undefined
  }
}

// ---------- {{}} 变量自动补全 ----------
interface VarItem {
  text: string
  desc: string
}

/** 递归收集某节点的全部上游节点 id（沿 edges 反向查找） */
function upstreamNodeIds(nodeId: string): string[] {
  const ids = new Set<string>()
  const walk = (id: string) => {
    for (const e of edges.value) {
      if (e.target === id && !ids.has(e.source)) {
        ids.add(e.source)
        walk(e.source)
      }
    }
  }
  walk(nodeId)
  return [...ids]
}

/** 当前节点可插入的变量项：{{input}} + 上游有输出节点的变量（code 脚本为裸变量名） */
function varItemsFor(nodeId: string): VarItem[] {
  const isCode = selectedData.value?.nodeType === 'code'
  const wrap = (k: string) => (isCode ? k : `{{${k}}}`)
  const items: VarItem[] = [{ text: wrap('input'), desc: '用户输入' }]
  if (isCode) items.push({ text: 'outputs', desc: '全部节点输出集合' })
  for (const id of upstreamNodeIds(nodeId)) {
    const n = nodes.value.find((x) => x.id === id)
    const t = n?.data?.nodeType
    if (t !== 'llm' && t !== 'agent' && t !== 'http' && t !== 'code' && t !== 'template' && t !== 'knowledge')
      continue // start/end/condition 无输出
    items.push({ text: wrap(id), desc: `节点「${n?.data?.label || id}」的输出` })
  }
  return items
}

/** 点击变量项，追加插入到配置字段末尾 */
function insertVar(field: string, item: VarItem) {
  if (!selectedData.value) return
  if (!selectedData.value.config) selectedData.value.config = {}
  const cur = selectedData.value.config[field]
  const base = cur == null ? '' : String(cur)
  const sep = base && !/\s$/.test(base) ? ' ' : ''
  selectedData.value.config[field] = base + sep + item.text
}

/** 高亮并定位节点 */
function highlightNode(nodeId: string) {
  const node = nodes.value.find((n) => n.id === nodeId)
  if (!node) return
  highlightedNodeId.value = nodeId
  setCenter(node.position.x, node.position.y, { zoom: 0.9 })
}

async function runDebug() {
  const text = debugInput.value.trim()
  if (!text) {
    ElMessage.warning('请输入测试消息')
    return
  }
  if (nodes.value.length === 0) {
    ElMessage.warning('画布为空，请先拖入节点编排工作流')
    return
  }
  const errors = validateWorkflow()
  if (errors.length > 0) {
    ElMessage.error(`存在未配置的节点，请先完善：\n${errors.map((e) => e.text).join('\n')}`)
    highlightNode(errors[0].node.id)
    return
  }
  const structure = checkStructure()
  if (structure.length > 0) {
    ElMessage.warning(`流程结构提示：\n${structure.join('\n')}`)
  }
  running.value = true
  try {
    await saveDraft() // 先保存草稿，确保后端运行的是当前画布内容
    clearRunState()
    const result = await appApi.run(appId, [{ role: 'user', content: text }])
    runResult.value = result
    applyTraceToCanvas(result.trace)
    const failed = result.trace.find((t) => t.status === 'error')
    if (failed) {
      lastRunFailed.value = true
      highlightNode(failed.nodeId)
      ElMessage.error(`节点「${failed.label}」执行失败：${failed.error || '未知错误'}`)
    } else {
      ElMessage.success(`运行完成，共执行 ${result.trace.length} 个节点`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '运行失败')
  } finally {
    running.value = false
  }
}

const nodeStatusColor: Record<string, 'success' | 'danger' | 'info'> = {
  success: 'success',
  error: 'danger',
  skipped: 'info'
}

/** 浏览器关闭 / 刷新前提示未保存 */
function onBeforeUnload(e: BeforeUnloadEvent) {
  if (!dirty.value) return
  e.preventDefault()
  e.returnValue = ''
}

onBeforeRouteLeave(() => {
  if (dirty.value) {
    return window.confirm('当前有未保存的修改，离开后将丢失，确定离开吗？')
  }
})

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('beforeunload', onBeforeUnload)
  document.addEventListener('click', closeAddMenu)
  loadApp()
  loadModels()
  loadDatasets()
  loadTools()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('click', closeAddMenu)
})
</script>

<template>
  <div class="editor">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button link @click="router.push('/apps')">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <span class="app-name">
        {{ appName }}
        <span v-if="dirty" class="dirty-dot" title="有未保存的修改" />
        <el-tag size="small" :type="appType === 'agent' ? 'success' : 'info'">
          {{ appType === 'agent' ? '智能体' : '编排' }}
        </el-tag>
      </span>
      <div class="toolbar-actions">
        <div class="toolbar-edit-group">
          <el-button
            :icon="RefreshLeft"
            plain circle
            title="撤销 (Ctrl+Z)"
            :disabled="historyIndex <= 0"
            @click="undo"
          />
          <el-button
            :icon="RefreshRight"
            plain circle
            title="重做 (Ctrl+Shift+Z)"
            :disabled="historyIndex >= historyStack.length - 1"
            @click="redo"
          />
          <el-button :icon="CopyDocument" plain circle title="复制选中 (Ctrl+C)" @click="copySelected" />
          <el-button :icon="Files" plain circle title="粘贴 (Ctrl+V)" @click="pasteClipboard" />
        </div>
        <div class="toolbar-layout-group">
          <el-dropdown trigger="click" @command="(c: any) => alignNodes(c)">
            <el-button
              :icon="Aim"
              plain circle
              title="对齐选中节点"
              :disabled="selectedCount < 2"
            />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="left">左对齐</el-dropdown-item>
                <el-dropdown-item command="hcenter">水平居中</el-dropdown-item>
                <el-dropdown-item command="right">右对齐</el-dropdown-item>
                <el-dropdown-item command="top">顶对齐</el-dropdown-item>
                <el-dropdown-item command="vcenter">垂直居中</el-dropdown-item>
                <el-dropdown-item command="bottom">底对齐</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown trigger="click" @command="(c: any) => distributeNodes(c)">
            <el-button
              :icon="Rank"
              plain circle
              title="均匀分布选中节点"
              :disabled="selectedCount < 3"
            />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="horizontal">水平等距分布</el-dropdown-item>
                <el-dropdown-item command="vertical">垂直等距分布</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button :icon="MagicStick" plain circle title="自动布局全部节点" @click="autoLayout" />
        </div>
        <el-popover placement="bottom-end" :width="280" trigger="hover" popper-class="shortcut-popover">
          <template #reference>
            <el-button :icon="QuestionFilled" plain circle title="快捷键" />
          </template>
          <div class="shortcut-list">
            <div v-for="s in shortcuts" :key="s.keys" class="shortcut-item">
              <span class="shortcut-keys">{{ s.keys }}</span>
              <span class="shortcut-desc">{{ s.desc }}</span>
            </div>
          </div>
        </el-popover>
        <el-button :icon="VideoPlay" type="primary" plain class="debug-btn" @click="toggleDebug">
          {{ debugVisible ? '收起调试' : '运行调试' }}
        </el-button>
        <el-button :icon="Promotion" plain @click="goChat">对话调试</el-button>
        <el-button v-if="appType !== 'agent'" :icon="Clock" plain @click="openVersions">历史版本</el-button>
        <el-button :icon="CopyDocument" :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button :icon="CircleCheck" class="btn-gradient" :loading="publishing" @click="publish">发布</el-button>
        <el-button
          :icon="Delete"
          type="danger"
          plain
          circle
          title="删除选中 (Delete)"
          :disabled="!hasSelection"
          @click="removeSelected"
        />
      </div>
    </div>

    <!-- 智能体应用：工具/知识库绑定配置 -->
    <div v-if="appType === 'agent'" class="agent-config">
      <el-card shadow="never" class="agent-card">
        <template #header>
          <div class="agent-card-head">
            <span>绑定工具</span>
            <el-button link type="primary" @click="router.push('/tools')">前往工具管理</el-button>
          </div>
        </template>
        <p class="agent-tip">
          智能体会根据对话内容自主决定调用哪些工具。请从可用工具中选择本应用可以使用的工具。
        </p>
        <el-checkbox-group v-model="boundToolIds" class="tool-check-list">
          <el-checkbox v-for="t in allTools" :key="t.id" :value="t.id" class="tool-check">
            <div class="tool-check-body">
              <div class="tool-check-name">
                <code class="tool-code">{{ t.name }}</code>
                <el-tag size="small" :type="t.type === 'http' ? 'warning' : 'info'" effect="plain">
                  {{ t.type === 'http' ? 'HTTP' : '代码' }}
                </el-tag>
              </div>
              <div class="tool-check-desc">{{ t.description }}</div>
            </div>
          </el-checkbox>
        </el-checkbox-group>
        <el-empty
          v-if="allTools.length === 0"
          description="暂无可用工具，请先在「工具管理」中创建"
          :image-size="60"
        />
      </el-card>

      <el-card shadow="never" class="agent-card">
        <template #header>
          <div class="agent-card-head">
            <span>绑定知识库</span>
            <el-button link type="primary" @click="router.push('/data/knowledge')">前往知识库管理</el-button>
          </div>
        </template>
        <p class="agent-tip">
          智能体会基于用户提问检索绑定数据集中的内容，并将命中的资料作为上下文辅助回答（RAG）。
        </p>
        <el-checkbox-group v-model="boundDatasetIds" class="tool-check-list">
          <el-checkbox v-for="d in datasets" :key="d.id" :value="d.id" class="tool-check">
            <div class="tool-check-body">
              <div class="tool-check-name">
                <code class="tool-code">{{ d.name }}</code>
                <el-tag size="small" type="success" effect="plain">知识库</el-tag>
              </div>
              <div class="tool-check-desc">{{ d.description || '暂无描述' }}</div>
            </div>
          </el-checkbox>
        </el-checkbox-group>
        <el-empty
          v-if="datasets.length === 0"
          description="暂无数据集，请先在「知识库」中创建"
          :image-size="60"
        />
      </el-card>

      <el-card shadow="never" class="agent-card">
        <template #header>
          <div class="agent-card-head">
            <span>应用设置</span>
          </div>
        </template>
        <el-form label-position="top" size="small">
          <el-form-item label="开场白">
            <el-input
              v-model="welcomeMessage"
              type="textarea"
              :rows="3"
              placeholder="对话开始时的欢迎语，留空则使用默认文案"
            />
          </el-form-item>
          <el-form-item label="推荐问题">
            <el-input
              v-model="openingQuestionsText"
              type="textarea"
              :rows="4"
              placeholder="每行一个推荐问题，展示在聊天页欢迎区，点击即可提问"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <div class="agent-actions">
        <el-button :icon="CopyDocument" :loading="saving" @click="saveDraft">保存配置</el-button>
        <el-button :icon="CircleCheck" class="btn-gradient" :loading="publishing" @click="publish">发布</el-button>
      </div>
    </div>

    <template v-else>
    <div class="editor-body">
      <!-- 中间画布 -->
      <div class="canvas-wrap">
        <div v-if="hydrated && nodes.length === 0" class="canvas-empty">
          <el-empty description="画布是空的，点击开始节点「+」添加流程节点" :image-size="88">
            <div class="canvas-empty-actions">
              <el-button type="primary" plain size="small" @click="ensureStartEnd">
                恢复 开始/结束 节点
              </el-button>
            </div>
          </el-empty>
          <div class="canvas-empty-tips">
            <span>· 开始/结束定义流程边界</span>
            <span>· 点击节点右侧「+」快速插入并连线</span>
            <span>· 条件分支 + 并行出边实现复杂流程</span>
          </div>
        </div>
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-viewport="{ zoom: 0.85 }"
          :min-zoom="0.2"
          :max-zoom="2"
          :selection-on-drag="true"
          :multi-selection-key-code="['Meta', 'Ctrl']"
          :edges-updatable="false"
          class="flow"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @edge-click="onEdgeClick"
          @node-drag-stop="onNodeDragStop"
        >
          <template #node-flow-node="{ data, selected, id }">
            <div
              class="flow-node"
              :class="[data.nodeType, { selected }, nodeRunClass(data), { 'run-highlight': id === highlightedNodeId }]"
            >
              <span v-if="data.runStatus === 'success'" class="run-badge run-badge-success">✓</span>
              <span v-else-if="data.runStatus === 'error'" class="run-badge run-badge-error">!</span>
              <span
                v-else-if="!data.runStatus && nodeWarnings(data).length"
                class="node-warn-badge"
                :class="{ warn: !hasNodeError(data) }"
                :title="nodeWarnings(data).map((w) => w.text).join('；')"
                >⚠</span
              >
              <div class="node-head">
                <div class="node-icon" :style="{ background: metaOf(data.nodeType).gradient }">
                  <el-icon :size="16" color="#fff">
                    <component :is="iconOf(metaOf(data.nodeType).icon)" />
                  </el-icon>
                </div>
                <div class="node-head-text">
                  <span class="node-title">{{ data.label }}</span>
                  <span class="node-desc">{{ metaOf(data.nodeType).desc }}</span>
                </div>
                <el-tooltip v-if="data.remark" :content="data.remark" placement="top" :show-after="200">
                  <span class="node-remark"><el-icon :size="12"><Notebook /></el-icon></span>
                </el-tooltip>
              </div>
              <span v-if="data.runCost !== undefined" class="run-cost">{{ data.runCost }}ms</span>
              <div v-if="data.runError" class="run-error-msg" :title="data.runError">{{ data.runError }}</div>
              <Handle type="target" :position="Position.Left" class="node-handle"></Handle>
              <template v-if="data.nodeType === 'condition'">
                <span class="branch-tag branch-tag-true">是</span>
                <Handle
                  type="source"
                  :position="Position.Right"
                  :id="'true'"
                  class="node-handle handle-branch handle-branch-true"
                ></Handle>
                <span
                  class="node-add-btn add-btn-branch add-btn-true"
                  title="添加节点"
                  @click.stop="openAddMenu($event, id, 'true')"
                  @mousedown.stop
                >+</span>
                <span class="branch-tag branch-tag-false">否</span>
                <Handle
                  type="source"
                  :position="Position.Right"
                  :id="'false'"
                  class="node-handle handle-branch handle-branch-false"
                ></Handle>
                <span
                  class="node-add-btn add-btn-branch add-btn-false"
                  title="添加节点"
                  @click.stop="openAddMenu($event, id, 'false')"
                  @mousedown.stop
                >+</span>
              </template>
              <template v-else>
                <Handle type="source" :position="Position.Right" class="node-handle"></Handle>
                <span
                  v-if="data.nodeType !== 'end'"
                  class="node-add-btn"
                  title="添加节点"
                  @click.stop="openAddMenu($event, id, null)"
                  @mousedown.stop
                >+</span>
              </template>
            </div>
          </template>
          <Background :gap="24" pattern-color="#d5dbe3" :line-width="1" />
          <Controls position="bottom-left" class="flow-controls" />
          <MiniMap
            pannable
            zoomable
            position="bottom-right"
            class="minimap"
            :node-color="miniMapNodeColor"
            mask-color="rgba(250, 251, 252, 0.75)"
          />
        </VueFlow>

        <!-- 节点右侧「+」弹出的节点选择浮层（Dify 式） -->
        <div
          v-if="addMenuFor"
          class="add-node-menu"
          :style="{ left: addMenuFor.x + 'px', top: addMenuFor.y + 'px' }"
          @click.stop
        >
          <div class="add-node-menu-title">添加节点</div>
          <div v-for="g in paletteGroups" :key="g.title" class="add-node-group">
            <div class="add-node-group-title">{{ g.title }}</div>
            <div
              v-for="t in g.types"
              :key="t"
              class="add-node-item"
              :class="{ disabled: t === 'start' }"
              @click="t !== 'start' && onAddNodeClick(t)"
            >
              <div class="add-node-item-icon" :style="{ background: metaOf(t).gradient }">
                <el-icon :size="12" color="#fff">
                  <component :is="iconOf(metaOf(t).icon)" />
                </el-icon>
              </div>
              <div class="add-node-item-text">
                <div class="add-node-item-label">{{ metaOf(t).label }}</div>
                <div class="add-node-item-desc">{{ metaOf(t).desc }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧配置面板 -->
      <aside class="config-panel" :class="{ collapsed: configCollapsed }">
        <div class="panel-head config-panel-head">
          <h4>{{ selectedData ? '节点配置' : selectedEdgeEnds ? '连线信息' : '配置' }}</h4>
          <el-button
            text
            circle
            size="small"
            class="panel-fold-btn"
            :icon="configCollapsed ? Expand : Fold"
            :title="configCollapsed ? '展开配置面板' : '折叠配置面板'"
            @click="configCollapsed = !configCollapsed"
          />
        </div>
        <template v-if="!configCollapsed">
        <template v-if="selectedData">
          <div class="config-node-head">
            <div
              class="node-icon"
              :style="{ background: metaOf(selectedData.nodeType).gradient }"
            >
              <el-icon :size="16" color="#fff">
                <component :is="iconOf(metaOf(selectedData.nodeType).icon)" />
              </el-icon>
            </div>
            <div class="config-node-head-text">
              <div class="config-node-type">{{ metaOf(selectedData.nodeType).label }}</div>
              <div class="config-node-name">{{ selectedData.label }}</div>
            </div>
          </div>
          <el-form label-position="top" size="small">
            <el-form-item label="节点名称">
              <el-input v-model="selectedData.label" />
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'llm'" label="模型">
              <el-select v-model="selectedData.config.modelId" placeholder="选择对话模型" style="width: 100%">
                <el-option
                  v-for="m in chatModels"
                  :key="m.id"
                  :label="`${m.providerName} / ${m.modelName}`"
                  :value="m.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'llm'" label="系统提示词">
              <el-input
                v-model="selectedData.config.systemPrompt"
                type="textarea"
                :rows="5"
                placeholder="设定模型角色与行为…"
              />
              <div class="var-chips">
                <span class="var-chips-title">可用变量</span>
                <span
                  v-for="v in varItemsFor(selectedNode.id)"
                  :key="v.text"
                  class="var-chip"
                  :title="v.desc"
                  @click="insertVar('systemPrompt', v)"
                  >{{ v.text }}</span
                >
              </div>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'llm'" label="温度">
              <el-slider v-model="selectedData.config.temperature" :min="0" :max="2" :step="0.1" />
            </el-form-item>

            <el-divider v-if="selectedData.nodeType === 'llm'" content-position="left">知识库检索</el-divider>
            <el-form-item v-if="selectedData.nodeType === 'llm'" label="关联数据集">
              <el-select
                v-model="selectedData.config.datasetId"
                placeholder="不使用知识库"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="d in datasets"
                  :key="d.id"
                  :label="d.name"
                  :value="d.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'llm' && selectedData.config.datasetId" label="召回数量">
              <el-input-number v-model="selectedData.config.topK" :min="1" :max="10" />
            </el-form-item>

            <template v-if="selectedData.nodeType === 'agent'">
              <el-form-item label="模型">
                <el-select v-model="selectedData.config.modelId" placeholder="选择对话模型" style="width: 100%">
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
                  v-model="selectedData.config.systemPrompt"
                  type="textarea"
                  :rows="5"
                  placeholder="设定 Agent 角色与行为，支持变量引用…"
                />
                <div class="var-chips">
                  <span class="var-chips-title">可用变量</span>
                  <span
                    v-for="v in varItemsFor(selectedNode.id)"
                    :key="v.text"
                    class="var-chip"
                    :title="v.desc"
                    @click="insertVar('systemPrompt', v)"
                    >{{ v.text }}</span
                  >
                </div>
              </el-form-item>
              <el-form-item label="可用工具">
                <el-select
                  v-model="selectedData.config.toolIds"
                  multiple
                  filterable
                  placeholder="不选则使用应用绑定工具"
                  style="width: 100%"
                >
                  <el-option
                    v-for="t in allTools"
                    :key="t.id"
                    :label="t.name"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="关联数据集">
                <el-select
                  v-model="selectedData.config.datasetId"
                  placeholder="不选则使用应用绑定数据集"
                  clearable
                  style="width: 100%"
                >
                  <el-option
                    v-for="d in datasets"
                    :key="d.id"
                    :label="d.name"
                    :value="d.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="selectedData.config.datasetId" label="召回数量">
                <el-input-number v-model="selectedData.config.topK" :min="1" :max="10" />
              </el-form-item>
              <el-form-item label="最大循环轮数">
                <el-input-number v-model="selectedData.config.maxIterations" :min="1" :max="20" />
              </el-form-item>
            </template>

            <el-form-item v-if="selectedData.nodeType === 'http'" label="请求地址">
              <el-input v-model="selectedData.config.url" :placeholder="urlPlaceholder" />
              <div class="var-chips">
                <span class="var-chips-title">可用变量</span>
                <span
                  v-for="v in varItemsFor(selectedNode.id)"
                  :key="v.text"
                  class="var-chip"
                  :title="v.desc"
                  @click="insertVar('url', v)"
                  >{{ v.text }}</span
                >
              </div>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="请求方式">
              <el-select v-model="selectedData.config.method" style="width: 100%">
                <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="自定义 Headers">
              <el-input
                v-model="headersText"
                type="textarea"
                :rows="3"
                :placeholder="headersPlaceholder"
                @blur="parseHeaders"
              />
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="鉴权方式">
              <el-select v-model="selectedData.config.authType" style="width: 100%">
                <el-option label="无" value="none" />
                <el-option label="Bearer Token" value="bearer" />
                <el-option label="Basic" value="basic" />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="selectedData.nodeType === 'http' && selectedData.config.authType === 'bearer'"
              label="Token"
            >
              <el-input v-model="selectedData.config.authToken" placeholder="Bearer Token" />
            </el-form-item>
            <template v-if="selectedData.nodeType === 'http' && selectedData.config.authType === 'basic'">
              <el-form-item label="用户名">
                <el-input v-model="selectedData.config.authUsername" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="selectedData.config.authPassword" type="password" show-password />
              </el-form-item>
            </template>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="失败重试次数">
              <el-input-number v-model="selectedData.config.retries" :min="0" :max="5" />
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'template'" label="模板内容">
              <el-input
                v-model="selectedData.config.template"
                type="textarea"
                :rows="6"
                placeholder="支持 {{input}} 与 {{节点id}} 变量插值&#10;示例：&#10;用户问题：{{input}}&#10;参考知识：{{node1}}"
              />
              <div class="var-chips">
                <span class="var-chips-title">可用变量</span>
                <span
                  v-for="v in varItemsFor(selectedNode.id)"
                  :key="v.text"
                  class="var-chip"
                  :title="v.desc"
                  @click="insertVar('template', v)"
                  >{{ v.text }}</span
                >
              </div>
            </el-form-item>

            <el-divider v-if="selectedData.nodeType === 'knowledge'" content-position="left">检索配置</el-divider>
            <el-form-item v-if="selectedData.nodeType === 'knowledge'" label="关联数据集">
              <el-select
                v-model="selectedData.config.datasetId"
                placeholder="请选择数据集"
                style="width: 100%"
              >
                <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'knowledge'" label="召回数量">
              <el-input-number v-model="selectedData.config.topK" :min="1" :max="10" />
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'knowledge'" label="检索词模板">
              <el-input
                v-model="selectedData.config.queryTemplate"
                placeholder="默认 {{input}}，可引用上游节点输出"
              />
              <div class="var-chips">
                <span class="var-chips-title">可用变量</span>
                <span
                  v-for="v in varItemsFor(selectedNode.id)"
                  :key="v.text"
                  class="var-chip"
                  :title="v.desc"
                  @click="insertVar('queryTemplate', v)"
                  >{{ v.text }}</span
                >
              </div>
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'code'" label="表达式脚本">
              <el-input
                v-model="selectedData.config.code"
                type="textarea"
                :rows="10"
                placeholder="MVEL 表达式，可用 input 与各节点输出变量，最后 return 结果&#10;示例：&#10;return input.trim().toUpperCase()&#10;示例：&#10;return input.length() > 10 ? '长文本' : '短文本'"
              />
              <div class="var-chips">
                <span class="var-chips-title">可用变量</span>
                <span
                  v-for="v in varItemsFor(selectedNode.id)"
                  :key="v.text"
                  class="var-chip"
                  :title="v.desc"
                  @click="insertVar('code', v)"
                  >{{ v.text }}</span
                >
              </div>
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'condition'" label="判断条件">
              <el-input
                v-model="selectedData.config.expression"
                type="textarea"
                :rows="2"
                :placeholder="conditionPlaceholder"
              />
              <div class="var-chips">
                <span class="var-chips-title">可用变量</span>
                <span
                  v-for="v in varItemsFor(selectedNode.id)"
                  :key="v.text"
                  class="var-chip"
                  :title="v.desc"
                  @click="insertVar('expression', v)"
                  >{{ v.text }}</span
                >
              </div>
            </el-form-item>

            <el-form-item v-if="isStartNode" label="开场白">
              <el-input
                v-model="selectedData.config.welcome"
                type="textarea"
                :rows="3"
                placeholder="对话开始时的欢迎语"
              />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input
                v-model="selectedData.remark"
                type="textarea"
                :rows="2"
                placeholder="记录该节点的用途、注意事项（仅编辑端展示，不影响执行）"
              />
            </el-form-item>
          </el-form>
        </template>
        <template v-else-if="selectedEdgeEnds">
          <h4>连线信息</h4>
          <div class="edge-info">
            <div class="edge-route">
              <div class="edge-end">
                <div
                  class="node-icon"
                  :style="{ background: metaOf(selectedEdgeEnds.srcType).gradient }"
                >
                  <el-icon :size="14" color="#fff">
                    <component :is="iconOf(metaOf(selectedEdgeEnds.srcType).icon)" />
                  </el-icon>
                </div>
                <span class="edge-end-label">{{ selectedEdgeEnds.srcLabel }}</span>
              </div>
              <span class="edge-arrow">→</span>
              <div class="edge-end">
                <div
                  class="node-icon"
                  :style="{ background: metaOf(selectedEdgeEnds.tgtType).gradient }"
                >
                  <el-icon :size="14" color="#fff">
                    <component :is="iconOf(metaOf(selectedEdgeEnds.tgtType).icon)" />
                  </el-icon>
                </div>
                <span class="edge-end-label">{{ selectedEdgeEnds.tgtLabel }}</span>
              </div>
            </div>
            <div v-if="selectedEdgeEnds.branch" class="edge-branch">
              <el-tag :type="selectedEdgeEnds.branch === '是' ? 'success' : 'danger'" size="small" effect="light">
                条件分支：{{ selectedEdgeEnds.branch }}
              </el-tag>
              <span class="edge-branch-tip">表达式为真走「是」，为假走「否」</span>
            </div>
            <el-form label-position="top" size="small" class="edge-label-form">
              <el-form-item label="连线标签">
                <el-input
                  v-model="selectedEdge.label"
                  placeholder="留空显示默认（条件分支显示 是/否）"
                  clearable
                />
              </el-form-item>
            </el-form>
            <p class="edge-tip">数据沿此连线流转，上一节点的输出会作为下一节点的输入。</p>
            <el-button type="danger" plain :icon="Delete" @click="removeSelected">删除该连线</el-button>
          </div>
        </template>
        <template v-else>
          <el-empty description="选中节点后在此配置" :image-size="80" />
        </template>
        </template>
        <div v-else class="config-collapsed-tip" title="展开配置面板">配置</div>
      </aside>
    </div>

    <!-- 运行调试面板 -->
    <div v-if="debugVisible" class="debug-panel">
      <div class="debug-head">
        <div class="debug-title">
          <el-icon><VideoPlay /></el-icon>
          运行调试
        </div>
        <div class="debug-head-actions">
          <el-button size="small" type="primary" :icon="VideoPlay" :loading="running" @click="runDebug">
            运行
          </el-button>
          <el-button size="small" text :icon="Close" @click="debugVisible = false">收起</el-button>
        </div>
      </div>
      <div class="debug-body">
        <div class="debug-input-area">
          <el-input
            v-model="debugInput"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入测试消息后点击「运行」，将按当前画布执行并实时标注节点状态"
            @keydown.enter.exact.prevent="runDebug"
          />
        </div>
        <div class="debug-result-area">
          <template v-if="runResult">
            <div class="debug-answer">
              <span class="debug-answer-label">最终回答</span>
              <div class="debug-answer-text">{{ runResult.answer }}</div>
            </div>
            <div class="debug-trace-head">
              <span>执行轨迹（点击定位节点）</span>
              <el-tag v-if="lastRunFailed" size="small" type="danger" effect="light">运行失败</el-tag>
              <el-tag v-else size="small" type="success" effect="light">运行成功</el-tag>
            </div>
            <div class="debug-trace-list">
              <div
                v-for="(t, i) in runResult.trace"
                :key="i"
                class="debug-trace-item"
                :class="{ active: t.nodeId === highlightedNodeId }"
                @click="highlightNode(t.nodeId)"
              >
                <span class="dt-index">{{ i + 1 }}</span>
                <el-tag size="small" :type="nodeStatusColor[t.status] || 'info'" effect="light">
                  {{ t.status }}
                </el-tag>
                <span class="dt-label" :title="t.label">{{ t.label }}</span>
                <span class="dt-cost">{{ t.costMs }}ms</span>
                <span v-if="t.error" class="dt-error" :title="t.error">⚠ {{ t.error }}</span>
              </div>
            </div>
          </template>
          <el-empty v-else description="输入测试消息后点击「运行」，结果将在此展示" :image-size="52" />
        </div>
      </div>
    </div>

    <!-- 版本历史弹窗 -->
    <el-dialog v-model="versionsVisible" title="历史版本" width="560px">
      <div v-loading="loadingVersions" min-height="120">
        <el-empty
          v-if="!loadingVersions && versions.length === 0"
          description="暂无发布版本，点击「发布」生成版本快照"
          :image-size="70"
        />
        <div v-else class="version-list">
          <div v-for="v in versions" :key="v.id" class="version-item">
            <div class="version-info">
              <div class="version-head">
                <span class="version-no">v{{ v.version }}</span>
                <el-tag v-if="v.isPublished === 1" size="small" type="success" effect="light">当前线上</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">历史</el-tag>
              </div>
              <div class="version-time">{{ formatTime(v.createTime) }} · {{ v.createdBy || '未知' }}</div>
            </div>
            <el-button size="small" :disabled="rollingBack" @click="rollbackTo(v)">
              回滚到该版本
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="versionsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}
.toolbar {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}
.app-name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.dirty-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  box-shadow: 0 0 0 3px rgba(245, 108, 108, 0.18);
  flex-shrink: 0;
}
.toolbar-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.debug-btn {
  --el-color-primary: var(--brand-1);
}
.editor-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.config-panel h4 {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 600;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-fold-btn {
  color: var(--text-tertiary);
}
.panel-fold-btn:hover {
  color: var(--brand-1);
}
.config-panel.collapsed .panel-head {
  justify-content: center;
  margin-bottom: 10px;
}
.config-panel.collapsed .panel-head h4 {
  display: none;
}
.config-collapsed-tip {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0 auto;
  padding: 10px 4px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  user-select: none;
  cursor: pointer;
  transition: all 0.2s ease;
}
.config-collapsed-tip:hover {
  color: var(--brand-1);
  border-color: var(--brand-1);
}

/* 画布（Dify 式：浅灰底 + 灰色点阵） */
.canvas-wrap {
  flex: 1;
  min-width: 0;
  position: relative;
  background-color: #fafbfc;
  background-image: radial-gradient(rgba(148, 163, 184, 0.4) 1px, transparent 1px);
  background-size: 22px 22px;
}
.flow {
  width: 100%;
  height: 100%;
}
.flow :deep(.vue-flow__edge-path) {
  stroke: #c2c8d1;
  stroke-width: 1.5;
}
.flow :deep(.vue-flow__edge.selected .vue-flow__edge-path),
.flow :deep(.vue-flow__edge:hover .vue-flow__edge-path) {
  stroke: #2970ff;
  stroke-width: 2;
}
.flow :deep(.vue-flow__edge.animated .vue-flow__edge-path) {
  stroke-dasharray: 6 4;
  animation: vue-flow-dashdraw 0.6s linear infinite;
}
.canvas-empty {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  pointer-events: none;
}
.canvas-empty :deep(.el-empty) {
  pointer-events: auto;
}
.canvas-empty-tips {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
  line-height: 1.7;
}

/* 节点卡片（Dify 式：渐变圆形图标 + 标题/描述，选中蓝框） */
.flow-node {
  position: relative;
  width: 210px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e2e5ea;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(17, 24, 39, 0.06);
  transition: all 0.2s ease;
}
.flow-node:hover {
  border-color: #2970ff;
  box-shadow: 0 4px 14px rgba(41, 112, 255, 0.16);
}
.flow-node.selected {
  border-color: #2970ff;
  box-shadow: 0 0 0 2px rgba(41, 112, 255, 0.32), 0 4px 14px rgba(41, 112, 255, 0.18);
}
.node-head {
  display: flex;
  align-items: center;
  gap: 10px;
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
.node-head-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.node-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-desc {
  margin-top: 1px;
  font-size: 11px;
  color: #98a2b3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-handle {
  width: 10px !important;
  height: 10px !important;
  background: #2970ff !important;
  border: 2px solid #fff !important;
  box-shadow: 0 0 0 1px rgba(41, 112, 255, 0.45);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.flow-node:hover .node-handle {
  transform: scale(1.3);
  box-shadow: 0 0 0 2px rgba(41, 112, 255, 0.5);
}
.node-add-btn {
  position: absolute;
  right: -28px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  border: 1.5px solid var(--brand-1);
  color: var(--brand-1);
  font-size: 15px;
  font-weight: 600;
  line-height: 19px;
  text-align: center;
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  z-index: 6;
  user-select: none;
}
.flow-node:hover .node-add-btn {
  opacity: 1;
}
.node-add-btn:hover {
  background: var(--brand-1);
  color: #fff;
  transform: translateY(-50%) scale(1.15);
}
.add-btn-branch {
  top: 26%;
}
.add-btn-false {
  top: 74%;
}
.flow-node.condition .handle-branch {
  width: 9px !important;
  height: 9px !important;
  min-width: 9px;
  min-height: 9px;
  border-width: 1.5px;
}
.flow-node.condition .handle-branch-true {
  top: 26% !important;
  border-color: #67c23a !important;
  background: #67c23a !important;
}
.flow-node.condition .handle-branch-false {
  top: 74% !important;
  border-color: #f56c6c !important;
  background: #f56c6c !important;
}
.branch-tag {
  position: absolute;
  right: -22px;
  z-index: 1;
  font-size: 10px;
  line-height: 16px;
  font-weight: 600;
  padding: 0 5px;
  border-radius: 6px;
  pointer-events: none;
}
.branch-tag-true {
  top: 20%;
  color: #67c23a;
  background: rgba(103, 194, 58, 0.12);
  border: 1px solid rgba(103, 194, 58, 0.4);
}
.branch-tag-false {
  top: 68%;
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.12);
  border: 1px solid rgba(245, 108, 108, 0.4);
}
.flow-controls {
  --vue-flow-controls-border-radius: 10px;
}
.minimap {
  right: 12px;
  bottom: 12px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  border: 1px solid #e2e5ea;
}
.minimap :deep(.vue-flow__minimap-mask) {
  fill: rgba(250, 251, 252, 0.75);
  stroke: #d5dbe3;
  stroke-width: 1;
}
.minimap :deep(.vue-flow__minimap-node) {
  rx: 4px;
  ry: 4px;
}
.add-node-menu {
  position: fixed;
  z-index: 3000;
  width: 240px;
  max-height: 330px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.14);
  padding: 8px;
}
.add-node-menu-title {
  font-size: 12px;
  color: var(--text-tertiary);
  font-weight: 600;
  padding: 2px 6px 8px;
}
.add-node-group {
  margin-bottom: 6px;
}
.add-node-group-title {
  font-size: 11px;
  color: var(--text-tertiary);
  padding: 2px 6px 4px;
}
.add-node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.add-node-item:hover {
  background: var(--el-color-primary-light-9);
}
.add-node-item.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.add-node-item-icon {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  box-shadow: inset 0 -2px 4px rgba(0, 0, 0, 0.1);
}
.add-node-item-text {
  min-width: 0;
}
.add-node-item-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
}
.add-node-item-desc {
  font-size: 11px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 配置面板 */
.config-panel {
  width: 290px;
  background: #fff;
  border-left: 1px solid var(--border-color);
  padding: 16px 14px;
  overflow-y: auto;
  transition: width 0.2s ease, padding 0.2s ease;
}
.config-panel.collapsed {
  width: 42px;
  padding: 16px 5px;
  overflow: visible;
}
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
.config-panel :deep(.el-form-item) {
  margin-bottom: 14px;
}
.config-panel :deep(.el-form-item__label) {
  font-size: 12.5px;
  color: var(--text-secondary);
  font-weight: 500;
}
.config-panel :deep(.el-divider__text) {
  font-size: 12px;
  color: var(--brand-1);
}

/* ---------- 智能体工具绑定 ---------- */
.agent-config {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 28px;
  display: flex;
  justify-content: center;
  background-image: radial-gradient(rgba(139, 92, 246, 0.06) 1px, transparent 1px);
  background-size: 22px 22px;
}
.agent-card {
  width: 680px;
  height: fit-content;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}
.agent-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.agent-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--brand-gradient-soft);
  border-radius: 10px;
  padding: 10px 14px;
}
.tool-check-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tool-check {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  transition: all 0.2s ease;
  white-space: normal;
}
.tool-check:hover {
  border-color: var(--brand-3);
  background: #fafbfe;
}
.tool-check-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 4px;
}
.tool-check-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tool-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  color: #409eff;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
}
.tool-check-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.5;
}
.agent-actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ---------- 节点运行状态 ---------- */
.flow-node.run-success {
  border-color: #67c23a;
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.16), var(--shadow-card);
}
.flow-node.run-error {
  border-color: #f56c6c;
  box-shadow: 0 0 0 3px rgba(245, 108, 108, 0.16), var(--shadow-card);
  animation: node-error-pulse 1.2s ease-in-out infinite;
}
.flow-node.run-skipped {
  opacity: 0.45;
  filter: grayscale(0.6);
}
.flow-node.run-highlight {
  animation: node-highlight-pulse 0.9s ease-in-out infinite;
}
@keyframes node-error-pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(245, 108, 108, 0.18), var(--shadow-card); }
  50% { box-shadow: 0 0 0 7px rgba(245, 108, 108, 0.3), var(--shadow-card); }
}
@keyframes node-highlight-pulse {
  0%, 100% { box-shadow: 0 0 0 4px rgba(91, 108, 255, 0.22), var(--shadow-card-hover); }
  50% { box-shadow: 0 0 0 9px rgba(91, 108, 255, 0.34), var(--shadow-card-hover); }
}
.run-badge {
  position: absolute;
  top: -7px;
  right: -7px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  z-index: 2;
}
.run-badge-success {
  background: #67c23a;
}
.run-badge-error {
  background: #f56c6c;
}
.node-warn-badge {
  position: absolute;
  top: -7px;
  right: -7px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  z-index: 2;
  cursor: help;
}
.node-warn-badge.warn {
  background: #e6a23c;
}
.node-remark {
  display: inline-flex;
  align-items: center;
  color: #e6a23c;
  cursor: help;
  flex: none;
}
.edge-branch {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}
.edge-branch-tip {
  font-size: 12px;
  color: var(--text-tertiary);
}
.edge-label-form {
  margin-bottom: 4px;
}
.var-chips {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
.var-chips-title {
  font-size: 11px;
  color: var(--text-tertiary, #8f9bb3);
}
.var-chip {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: #5b6cff;
  background: #f1f2ff;
  border: 1px dashed #b9c1ff;
  border-radius: 5px;
  padding: 1px 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.var-chip:hover {
  background: #5b6cff;
  color: #fff;
  border-color: #5b6cff;
}
.toolbar-edit-group {
  display: flex;
  gap: 4px;
  padding-right: 8px;
  margin-right: 4px;
  border-right: 1px solid var(--el-border-color-lighter);
}
.toolbar-layout-group {
  display: flex;
  gap: 4px;
  padding-right: 8px;
  margin-right: 4px;
  border-right: 1px solid var(--el-border-color-lighter);
}
.shortcut-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.shortcut-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}
.shortcut-keys {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: #5b6cff;
  background: #f1f2ff;
  border-radius: 4px;
  padding: 1px 6px;
}
.shortcut-desc {
  color: var(--text-secondary, #6b7280);
}
.edge-info {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.edge-route {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f7f8fc;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}
.edge-end {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}
.edge-end .node-icon {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  color: #fff;
  box-shadow: inset 0 -2px 4px rgba(0, 0, 0, 0.1), 0 1px 3px rgba(0, 0, 0, 0.12);
  align-items: center;
  justify-content: center;
}
.edge-end-label {
  font-size: 12px;
  color: var(--text-secondary, #6b7280);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.edge-arrow {
  font-size: 16px;
  color: #c0c4cc;
  flex-shrink: 0;
}
.edge-tip {
  font-size: 12px;
  color: var(--text-tertiary, #8f9bb3);
  line-height: 1.6;
  margin: 0;
}
.run-cost {
  position: absolute;
  right: 10px;
  bottom: 6px;
  font-size: 10px;
  color: var(--text-tertiary);
  background: rgba(255, 255, 255, 0.85);
  padding: 1px 6px;
  border-radius: 8px;
}
.run-error-msg {
  margin-top: 8px;
  font-size: 10.5px;
  color: #f56c6c;
  background: #fef0f0;
  border-radius: 6px;
  padding: 3px 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 运行调试面板 ---------- */
.debug-panel {
  flex-shrink: 0;
  border-top: 1px solid var(--border-color);
  background: #fff;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.05);
  z-index: 5;
}
.debug-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid var(--border-color);
}
.debug-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-1);
}
.debug-head-actions {
  display: flex;
  gap: 6px;
}
.debug-body {
  display: flex;
  gap: 16px;
  padding: 12px 14px;
  min-height: 118px;
  max-height: 240px;
}
.debug-input-area {
  width: 300px;
  flex-shrink: 0;
}
.debug-result-area {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}
.debug-answer {
  background: var(--brand-gradient-soft);
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 10px;
}
.debug-answer-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-1);
}
.debug-answer-text {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.debug-trace-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.debug-trace-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.debug-trace-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: 12px;
}
.debug-trace-item:hover {
  border-color: var(--brand-3);
  background: #fafbfe;
}
.debug-trace-item.active {
  border-color: var(--brand-1);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.15);
}
.dt-index {
  width: 16px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 11px;
}
.dt-label {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dt-cost {
  color: var(--text-tertiary);
  font-size: 11px;
  flex-shrink: 0;
}
.dt-error {
  color: #f56c6c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 版本历史 ---------- */
.version-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  transition: all 0.15s ease;
}
.version-item:hover {
  border-color: var(--brand-3);
  background: #fafbfe;
}
.version-info {
  min-width: 0;
}
.version-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.version-no {
  font-weight: 700;
  font-size: 13px;
  color: var(--brand-1);
}
.version-time {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
