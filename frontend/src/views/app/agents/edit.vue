<script setup lang="ts">
import {type Component, computed, markRaw, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import {onBeforeRouteLeave, useRoute, useRouter} from 'vue-router'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  Aim,
  ArrowLeft,
  CircleCheck,
  Clock,
  Close,
  CopyDocument,
  Cpu,
  Delete,
  Document,
  Expand,
  Files,
  Fold,
  Link as LinkIcon,
  MagicStick,
  Notebook,
  Promotion,
  QuestionFilled,
  Rank,
  RefreshLeft,
  RefreshRight,
  Share,
  VideoPlay
} from '@element-plus/icons-vue'
import {Handle, Position, useVueFlow, VueFlow} from '@vue-flow/core'
import {Background} from '@vue-flow/background'
import {Controls} from '@vue-flow/controls'
import {MiniMap} from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import {appAgentApi} from '@/api/app-agent.ts'
import {knowledgeApi} from '@/api/knowledge.ts'
import {modelApi} from '@/api/model.ts'
import {appAgentToolApi} from '@/api/app-agent-tool.ts'
import type {
  AppAgentTool,
  AppAgentVersion,
  ChatModelInfo,
  KnowledgeDataset,
  RunResult,
  TraceItem,
  WorkflowNodeType
} from '@/api/types.ts'
import type {VarItem} from '@/utils/flow.ts'
import {branchHandlesOf, defaultConfig, dslToFlow, flowToDsl, genNodeId, NODE_TYPE_META} from '@/utils/flow.ts'
import NodeConfigPanel from './components/NodeConfigPanel.vue'
import {useThemeStore} from '@/stores/theme.ts'

const route = useRoute()
const router = useRouter()
const appId = Number(route.params.id)
const themeStore = useThemeStore()

/** 画布网格点颜色跟随主题 */
const gridColor = computed(() => (themeStore.isDark ? '#333a52' : '#d5dbe3'))


// ---------- 画布 ----------
const nodes = ref<Array<any>>([])
const edges = ref<Array<any>>([])
const {addNodes, removeNodes, onConnect, setCenter, fitView, getNodes, getEdges} = useVueFlow()

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
  const sel = getNodes.value.filter((n) => n.selected)
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
      position: {x: baseX + i * 40, y: baseY + i * 40},
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
        ...(e.sourceHandle ? branchStyleOf(e.source, e.sourceHandle) : {})
      })
    }
  }
  addNodes(newNodes)
  edges.value = [...edges.value, ...newEdges]
  snapshot()
  ElMessage.success(`已粘贴 ${newNodes.length} 个节点`)
}

// ---------- 左侧操作栏「编辑」组 ----------
/** 编辑组按钮配置：统一 tooltip / 禁用逻辑 / 点击动作 */
const editTools: Array<{ key: string; icon: Component; tip: string; disabled: () => boolean; action: () => void }> = [
  {key: 'undo', icon: RefreshLeft, tip: '撤销 (Ctrl+Z)', disabled: () => historyIndex.value <= 0, action: undo},
  {
    key: 'redo',
    icon: RefreshRight,
    tip: '重做 (Ctrl+Shift+Z)',
    disabled: () => historyIndex.value >= historyStack.value.length - 1,
    action: redo
  },
  {key: 'copy', icon: CopyDocument, tip: '复制选中 (Ctrl+C)', disabled: () => false, action: copySelected},
  {key: 'paste', icon: Files, tip: '粘贴 (Ctrl+V)', disabled: () => false, action: pasteClipboard}
]

// ---------- 数据 ----------
const appName = ref('')
const appType = ref('chatflow')
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const chatModels = ref<ChatModelInfo[]>([])
const rerankModels = ref<ChatModelInfo[]>([])
const datasets = ref<KnowledgeDataset[]>([])
const allTools = ref<AppAgentTool[]>([])
const boundToolIds = ref<number[]>([])
const boundDatasetIds = ref<number[]>([])
const welcomeMessage = ref('')
const openingQuestionsText = ref('')
const saving = ref(false)
const publishing = ref(false)

// ---------- 面板折叠 ----------
const configCollapsed = ref(false)

/** 左侧节点面板分组（按功能归类，不影响拖拽与数据模型） */
const paletteGroups: Array<{ title: string; types: WorkflowNodeType[] }> = [
  {title: '流程控制', types: ['start', 'end', 'condition']},
  {title: '处理节点', types: ['llm', 'agent', 'template', 'code']},
  {title: '外部数据', types: ['http', 'knowledge']}
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
      position: {x, y},
      data: {label: NODE_TYPE_META[type].label, nodeType: type, config: defaultConfig(type)}
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
    edges.value.push({id: genNodeId('edge'), source: startId, target: endId})
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
  addMenuFor.value = {x, y, sourceId, handle}
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
  // 沿分支 handle 的纵向位置错开，多分支时按分支序号偏移
  if (sourceHandle) {
    const handles = branchHandlesOf(src.data)
    const idx = handles.findIndex((h) => h.key === sourceHandle)
    if (idx >= 0) {
      const ratio = (idx + 1) / (handles.length + 1) - 0.5
      y += Math.round(ratio * (handles.length * 56))
    } else if (sourceHandle === 'true') y -= 60
    else y += 60
  }
  nodes.value.push({
    id: newId,
    type: 'flow-node',
    position: {x, y},
    data: {
      label: NODE_TYPE_META[type].label,
      nodeType: type,
      config: defaultConfig(type)
    }
  })
  const endNode = nodes.value.find((n) => n.data.nodeType === 'end')
  const srcOuts = edges.value.filter((e) => e.source === sourceId)
  const directEnd = srcOuts.filter((e) => e.target === endNode?.id)
  // source 唯一出边直连 end → 替换为 source→new→end；否则新节点平连 end
  if (srcOuts.length === 1 && directEnd.length === 1 && endNode) {
    edges.value = edges.value.filter((e) => e.id !== directEnd[0].id)
    edges.value.push({id: genNodeId('edge'), source: newId, target: endNode.id})
  } else if (endNode) {
    edges.value.push({id: genNodeId('edge'), source: newId, target: endNode.id})
  }
  edges.value.push({
    id: genNodeId('edge'),
    source: sourceId,
    target: newId,
    sourceHandle: sourceHandle ?? undefined,
    label: branchLabelOf(sourceId, sourceHandle),
    ...(sourceHandle ? branchStyleOf(sourceId, sourceHandle) : {})
  })
  snapshot()
  // 选中新节点并定位
  selectedNodeId.value = newId
  const newNode = nodes.value.find((n) => n.id === newId)
  if (newNode) {
    editBaseline = JSON.stringify(newNode.data)
    nextTick(() => {
      setCenter(newNode.position.x + NODE_W / 2, newNode.position.y + 40, {zoom: 0.9})
    })
  }
}

const selectedNode = computed(() =>
    nodes.value.find((n) => n.id === selectedNodeId.value) ?? null
)
const selectedData = computed(() => selectedNode.value?.data ?? null)
/**
 * 配置面板重建版本：选中节点 data 对象引用变化时自增。
 * 撤销/重做、加载 DSL、回滚等路径会用新对象整体替换 node.data，
 * 此时强制 <NodeConfigPanel> 重建，避免面板内部持有陈旧的 config 引用。
 */
const panelVersion = ref(0)
watch(selectedData, (d, old) => {
  if (d !== old) panelVersion.value++
})
const hasSelection = computed(
    () => getNodes.value.some((n) => n.selected) || getEdges.value.some((e) => e.selected)
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
    branch: branchLabelOf(e.source, e.sourceHandle)
  }
})
const selectedCount = computed(() => getNodes.value.filter((n) => n.selected).length)

interface ShortcutItem {
  keys: string
  desc: string
}

interface ShortcutGroup {
  title: string
  items: ShortcutItem[]
}

const shortcutGroups: ShortcutGroup[] = [
  {
    title: '撤销 / 重做',
    items: [
      {keys: 'Ctrl + Z', desc: '撤销上一步画布操作，最多回退 50 步'},
      {keys: 'Ctrl + Shift + Z', desc: '重做被撤销的操作'},
      {keys: 'Ctrl + Y', desc: '重做（与 Ctrl + Shift + Z 等效）'}
    ]
  },
  {
    title: '复制 / 删除',
    items: [
      {keys: 'Ctrl + C', desc: '复制选中节点（含节点配置，支持框选 / Ctrl 多选）'},
      {keys: 'Ctrl + V', desc: '粘贴到画布左上角，逐个错位 40px，并自动重建它们之间的连线'},
      {keys: 'Delete / Backspace', desc: '删除选中节点或连线；删节点会一并移除其所有连线'}
    ]
  },
  {
    title: '选中节点',
    items: [
      {keys: '单击节点', desc: '选中节点，并在右侧打开该节点的配置面板'},
      {keys: 'Ctrl / Cmd + 点击', desc: '在已选节点上追加或取消选中，实现多选'},
      {keys: 'Shift + 拖拽空白', desc: '框选多个节点，可批量移动 / 复制 / 删除'},
      {keys: '单击画布空白', desc: '取消选中，收起右侧配置面板'}
    ]
  },
  {
    title: '画布与连线',
    items: [
      {keys: '拖拽节点', desc: '移动节点位置，松手后可用 Ctrl + Z 撤销'},
      {keys: '拖拽画布空白', desc: '平移画布；按住 Shift 拖拽则框选多节点'},
      {keys: '滚轮 / 触控板双指', desc: '缩放画布，缩放范围 20% ~ 200%'},
      {keys: '双击画布空白', desc: '放大一级'},
      {keys: '拖拽节点右侧圆点', desc: '从出点拉出连线，接到目标节点左侧入点'},
      {keys: '点击节点「+」', desc: '快速插入下一个节点并自动连线（结束节点无「+」）'},
      {keys: '单击连线', desc: '选中连线，按 Delete 删除'}
    ]
  },
  {
    title: '布局工具（左侧栏）',
    items: [
      {keys: '选中 ≥ 2 个节点', desc: '左 / 水平居中 / 右 / 顶 / 垂直居中 / 底对齐'},
      {keys: '选中 ≥ 3 个节点', desc: '水平 / 垂直等距分布，保持首尾节点不动'},
      {keys: '自动布局', desc: '一键按流程层级重排全部节点'}
    ]
  }
]
const shortcutNote =
    '说明：快捷键在输入框 / 文本域聚焦时不生效；撤销重做只覆盖画布结构（节点与连线的增删改移），右侧表单配置修改不会进入历史栈。'

// ---------- 未保存修改追踪 ----------
const dirty = ref(false)
const hydrated = ref(false)
let editBaseline = '' // 当前选中节点的内容基线（区分编辑与切换选中）

watch(
    [nodes, edges, boundToolIds, boundDatasetIds, welcomeMessage, openingQuestionsText],
    () => {
      if (hydrated.value) dirty.value = true
    },
    {deep: true}
)
watch(selectedData, (val) => {
  if (!hydrated.value) return
  if (JSON.stringify(val ?? null) !== editBaseline) dirty.value = true
}, {deep: true})

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
  const app = await appAgentApi.get(appId)
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
  const {nodes: ns, edges: es} = dslToFlow(app.workflowJson)
  nodes.value = ns
  edges.value = es
  syncBranchEdges()
  if (ns.length === 0) {
    // Dify 式：空画布预置 开始/结束 节点并直连
    ensureStartEnd()
  }
  nextTick(() => {
    hydrated.value = true
    dirty.value = false
    if (ns.length === 0) {
      setCenter(290, 300, {zoom: 0.85})
    } else {
      fitView({padding: 0.2, duration: 300})
    }
  })
}

async function loadModels() {
  const [chat, rerank] = await Promise.all([
    modelApi.chatModels(),
    modelApi.rerankModels().catch(() => [] as ChatModelInfo[])
  ])
  chatModels.value = chat
  rerankModels.value = rerank
}

async function loadTools() {
  try {
    allTools.value = await appAgentToolApi.enabled()
  } catch {
    allTools.value = []
  }
}

async function loadDatasets() {
  try {
    datasets.value = (await knowledgeApi.datasetPage({size: 100})).records
  } catch {
    datasets.value = []
  }
}

// ---------- 节点操作 ----------
function onNodeClick({node}: any) {
  selectedNodeId.value = node.id
  selectedEdgeId.value = null
  // 同步更新内容基线：避免 watch(selectedData) 把"切换选中"误判为"编辑"
  editBaseline = JSON.stringify(node.data ?? null)
}

function onPaneClick() {
  selectedNodeId.value = null
  selectedEdgeId.value = null
}

function onEdgeClick({edge}: any) {
  selectedEdgeId.value = edge.id
  selectedNodeId.value = null
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
  edges.value.push({
    id: genNodeId('edge'),
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    targetHandle: params.targetHandle,
    label: branchLabelOf(params.source, params.sourceHandle),
    ...(params.sourceHandle ? branchStyleOf(params.source, params.sourceHandle) : {})
  })
  snapshot()
})

// ---------- 条件分支出边 ----------
/** 分支 handle 对应的展示名（多分支取分支名，二分支取 是/否，非分支返回 undefined） */
function branchLabelOf(sourceId: string, handle?: string | null): string | undefined {
  if (!handle) return undefined
  const node = nodes.value.find((n) => n.id === sourceId)
  if (!node) return undefined
  const hit = branchHandlesOf(node.data).find((h) => h.key === handle)
  return hit ? hit.label : undefined
}

/** 分支 handle 对应的主题色 */
function branchColorOf(sourceId: string, handle: string): string {
  const node = nodes.value.find((n) => n.id === sourceId)
  const hit = node ? branchHandlesOf(node.data).find((h) => h.key === handle) : null
  return hit?.color ?? '#909399'
}

/** 分支出边样式（按分支取色），Vue Flow 内置 label 渲染 */
function branchStyleOf(sourceId: string, handle: string) {
  const color = branchColorOf(sourceId, handle)
  return {
    labelStyle: {
      color,
      fontWeight: '600',
      fontSize: '11px',
      background: themeStore.isDark ? '#1a1d2c' : '#ffffff',
      border: `1px solid ${color}`,
      borderRadius: '8px',
      padding: '1px 6px'
    },
    labelShowBg: false,
    labelBgStyle: {fill: 'transparent'}
  }
}

/** 为分支边补齐标签与样式（历史数据 / 回滚 / 分支改名后调用） */
function syncBranchEdges() {
  for (const e of edges.value) {
    if (!e.sourceHandle) continue
    const src = nodes.value.find((n) => n.id === e.source)
    // 仅处理排他分支节点（condition）的出边
    if (src?.data?.nodeType !== 'condition') continue
    e.label = e.label || branchLabelOf(e.source, e.sourceHandle)
    Object.assign(e, branchStyleOf(e.source, e.sourceHandle))
  }
}

// 主题切换后刷新分支标签的底色
watch(
    () => themeStore.isDark,
    () => syncBranchEdges()
)

function removeSelected() {
  const selNodes = getNodes.value.filter((n) => n.selected).map((n) => n.id)
  const selEdges = getEdges.value.filter((e) => e.selected).map((e) => e.id)
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

/** 节点配置完整性检查：缺少关键配置为 error，可能影响效果但不阻断运行为 warn */
function nodeWarnings(data: any): NodeWarning[] {
  const cfg = data?.config ?? {}
  const list: NodeWarning[] = []
  const str = (v: unknown) => (v == null ? '' : String(v))
  const num = (v: unknown) => (typeof v === 'number' ? v : Number(v ?? 0))
  switch (data?.nodeType) {
    case 'llm':
    case 'agent':
      if (!cfg.modelId) list.push({severity: 'error', text: '未配置模型'})
      else if (!str(cfg.userPrompt).trim()) {
        list.push({
          severity: 'warn',
          text: data.nodeType === 'agent' ? '用户提示词为空，Agent 将收不到任务输入' : '用户提示词为空，模型将收不到用户输入'
        })
      }
      break
    case 'http':
      if (!str(cfg.url).trim()) list.push({severity: 'error', text: '未配置请求地址'})
      else if (cfg.bodyType && cfg.bodyType !== 'none' && !str(cfg.bodyTemplate).trim()) {
        list.push({severity: 'warn', text: `已选请求体类型 ${cfg.bodyType}，但未填写请求体模板`})
      }
      break
    case 'code':
      if (!str(cfg.code).trim()) list.push({severity: 'error', text: '未配置代码脚本'})
      break
    case 'condition': {
      const branches = Array.isArray(cfg.branches) ? cfg.branches : []
      if (branches.length > 0) {
        const empty = branches.filter((b: any) => !str(b?.expression).trim()).length
        if (empty === branches.length) {
          list.push({severity: 'warn', text: '所有分支条件均为空，将命中第一条分支'})
        }
      } else if (!str(cfg.expression).trim()) {
        list.push({severity: 'warn', text: '未配置判断条件，默认走「是」分支'})
      }
      break
    }
    case 'template':
      if (!str(cfg.template).trim()) list.push({severity: 'error', text: '未配置模板内容'})
      break
    case 'knowledge':
      if (!cfg.datasetId) list.push({severity: 'error', text: '未选择数据集'})
      else if (num(cfg.scoreThreshold) > 0.95) {
        list.push({severity: 'warn', text: '相似度阈值过高，可能召回不到内容'})
      }
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
      if (w.severity === 'error') errors.push({node: n, text: `「${n.data?.label || '未命名'}」${w.text}`})
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

/** 多选手势引导文案（对齐 / 分布未选中足够节点时的提示） */
const SELECT_HINT = '请先选中至少 2 个节点：按住 Shift 拖拽框选，或按住 Ctrl 点击追加选中'

/**
 * 读取 vue-flow 内部 store 的选中节点（包围盒，尺寸未就绪时用默认值）。
 * 注意：不读 v-model 父数组的 n.selected——vue-flow 框选后 selected 回写不可靠，
 * getNodes（ComputedRef）返回内部真实渲染节点（选中态/尺寸始终准确）。
 */
function selectedBoxes() {
  return getNodes.value
      .filter((n) => n.selected)
      .map((n) => ({
        n,
        x: n.position.x,
        y: n.position.y,
        w: n.dimensions?.width || 210,
        h: n.dimensions?.height || 64
      }))
}

/**
 * 批量应用节点新位置：
 * 1) 原地修改内部 store 节点对象的 position（与 vue-flow 拖动的更新机制一致，
 *    拖动能生效、这里就必然生效），画布立即重排；
 * 2) 再用全新浅拷贝数组替换 v-model 的 nodes，保证受控同步与保存/撤销快照一致；
 * 3) 记录历史并反馈实际移动的节点数。
 */
function applyNodePositions(next: Record<string, Partial<{ x: number; y: number }>>, msg: string) {
  const ids = Object.keys(next)
  if (!ids.length) {
    ElMessage.warning('未选中任何节点：请先按住 Shift 拖拽框选，或按住 Ctrl 点击追加选中')
    return
  }
  const current = getNodes.value
  const moved: string[] = []
  current.forEach((n) => {
    const p = next[n.id]
    if (p) {
      if (typeof p.x === 'number') n.position.x = p.x
      if (typeof p.y === 'number') n.position.y = p.y
      moved.push(n.id)
    }
  })
  nodes.value = current.map((n) => ({...n}))
  snapshot()
  ElMessage.success(`${msg}（已移动 ${moved.length} 个节点）`)
}

/** 对齐选中节点：以包围盒为基准，left/hcenter/right 对齐 X 轴，top/vcenter/bottom 对齐 Y 轴 */
function alignNodes(mode: AlignMode) {
  const boxes = selectedBoxes()
  if (boxes.length < 2) {
    ElMessage.warning(SELECT_HINT)
    return
  }
  const minX = Math.min(...boxes.map((b) => b.x))
  const maxX = Math.max(...boxes.map((b) => b.x + b.w))
  const minY = Math.min(...boxes.map((b) => b.y))
  const maxY = Math.max(...boxes.map((b) => b.y + b.h))
  const avgCX = boxes.reduce((s, b) => s + b.x + b.w / 2, 0) / boxes.length
  const avgCY = boxes.reduce((s, b) => s + b.y + b.h / 2, 0) / boxes.length
  // 先计算目标位置
  const next: Record<string, Partial<{ x: number; y: number }>> = {}
  for (const b of boxes) {
    switch (mode) {
      case 'left':
        next[b.n.id] = {x: minX};
        break
      case 'hcenter':
        next[b.n.id] = {x: avgCX - b.w / 2};
        break
      case 'right':
        next[b.n.id] = {x: maxX - b.w};
        break
      case 'top':
        next[b.n.id] = {y: minY};
        break
      case 'vcenter':
        next[b.n.id] = {y: avgCY - b.h / 2};
        break
      case 'bottom':
        next[b.n.id] = {y: maxY - b.h};
        break
    }
  }
  applyNodePositions(next, '对齐完成')
}

/** 均匀分布选中节点：沿 X 轴（horizontal）或 Y 轴（vertical）保持首尾不动、间隙相等 */
function distributeNodes(mode: DistributeMode) {
  const boxes = selectedBoxes()
  if (boxes.length < 2) {
    ElMessage.warning(SELECT_HINT)
    return
  }
  const isX = mode === 'horizontal'
  const axis = isX ? 'x' : 'y'
  const size = isX ? 'w' : 'h'
  const sorted = [...boxes].sort((a, b) => a[axis] - b[axis])
  const total = sorted.reduce((s, b) => s + b[size], 0)
  const span = sorted[sorted.length - 1][axis] + sorted[sorted.length - 1][size] - sorted[0][axis]
  const gap = (span - total) / (sorted.length - 1)
  const next: Record<string, Partial<{ x: number; y: number }>> = {}
  let pos = sorted[0][axis]
  for (const b of sorted) {
    next[b.n.id] = isX ? {x: pos} : {y: pos}
    pos += b[size] + gap
  }
  applyNodePositions(next, '分布完成')
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
  const next: Record<string, { x: number; y: number }> = {}
  for (const d of Object.keys(layers).map(Number).sort((a, b) => a - b)) {
    const ids = layers[d]
    const x = MARGIN + d * (W + GAP_X)
    ids.forEach((id, i) => {
      next[id] = {x: Math.round(x), y: Math.round(MARGIN + i * (H + GAP_Y))}
    })
  }
  applyNodePositions(next, '已按流程层级完成自动布局')
}

// ---------- 保存 / 发布 ----------
async function saveDraft() {
  saving.value = true
  try {
    if (appType.value === 'agent') {
      await appAgentApi.update(appId, {
        toolIds: JSON.stringify(boundToolIds.value),
        datasetIds: JSON.stringify(boundDatasetIds.value),
        welcomeMessage: welcomeMessage.value,
        openingQuestions: JSON.stringify(
            openingQuestionsText.value.split('\n').map((q) => q.trim()).filter(Boolean)
        )
      })
    } else {
      await appAgentApi.update(appId, {workflowJson: flowToDsl(nodes.value, edges.value)})
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
          {confirmButtonText: '仍要发布', cancelButtonText: '去完善', type: 'warning'}
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
        appType.value === 'agent' ? JSON.stringify({nodes: [], edges: []}) : flowToDsl(nodes.value, edges.value)
    await appAgentApi.publish(appId, {workflowJson, promptConfig: ''})
    ElMessage.success('发布成功')
  } finally {
    publishing.value = false
  }
}

function goChat() {
  router.push(`/app/agents/${appId}/chat`)
}

// ---------- 版本历史 ----------
const versionsVisible = ref(false)
const versions = ref<AppAgentVersion[]>([])
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
    versions.value = await appAgentApi.versions(appId)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载版本列表失败')
  } finally {
    loadingVersions.value = false
  }
}

async function rollbackTo(version: AppAgentVersion) {
  await ElMessageBox.confirm(
      `将把 v${version.version} 的工作流恢复到当前画布（草稿），不会自动发布，确认回滚？`,
      '回滚确认',
      {confirmButtonText: '回滚', cancelButtonText: '取消', type: 'warning'}
  )
  rollingBack.value = true
  try {
    await appAgentApi.rollback(appId, version.id)
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
  // 直接删除运行态属性而非整体替换 data：
  // 整体替换 node.data 会让 vue-flow 的 Handle 组件在更新期访问到失效实例（emitsOptions/subTree 空指针），
  // 且会使右侧配置面板持有的 config 引用失效。
  for (const n of nodes.value) {
    if (!n.data) continue
    delete n.data.runStatus
    delete n.data.runCost
    delete n.data.runError
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
    if (!n.data) continue
    // 保持 node.data 对象引用不变，仅更新运行态属性（避免 vue-flow 更新期崩溃与面板配置引用失效）
    n.data.runStatus = statusMap[n.id] || 'idle'
    n.data.runCost = costMap[n.id]
    n.data.runError = errMap[n.id]
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
    e.style = on ? {stroke: '#67c23a', strokeWidth: 2.5} : undefined
  }
}

// ---------- {{}} 变量自动补全 ----------

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

/** 有输出的节点类型（start/end/condition 无输出，不作为变量来源） */
const OUTPUT_NODE_TYPES = new Set(['llm', 'agent', 'http', 'code', 'template', 'knowledge'])

/**
 * 当前节点可插入的变量项：{{input}} + 开始节点定义的流程变量 + 上游节点输出
 * （code 节点脚本内使用裸变量名，不加 {{}}）
 */
function varItemsFor(nodeId: string): VarItem[] {
  const isCode = selectedData.value?.nodeType === 'code'
  const wrap = (k: string) => (isCode ? k : `{{${k}}}`)
  const items: VarItem[] = [{text: wrap('input'), desc: '用户输入'}]
  if (isCode) items.push({text: 'outputs', desc: '全部节点输出集合'})
  // 开始节点定义的流程变量（全局可见）
  for (const n of nodes.value) {
    if (n.data?.nodeType !== 'start') continue
    const vars = n.data?.config?.variables
    if (!Array.isArray(vars)) continue
    for (const v of vars) {
      if (v?.name) items.push({text: wrap(String(v.name)), desc: '开始节点流程变量'})
    }
  }
  for (const id of upstreamNodeIds(nodeId)) {
    const n = nodes.value.find((x) => x.id === id)
    if (!OUTPUT_NODE_TYPES.has(n?.data?.nodeType)) continue
    const alias = n?.data?.config?.outputVar
    const label = n?.data?.label || id
    items.push({text: wrap(id), desc: `节点「${label}」的输出`})
    if (alias && !isCode) {
      items.push({text: `{{${alias}}}`, desc: `节点「${label}」的输出变量别名`})
    }
  }
  return items
}

/** 高亮并定位节点 */
function highlightNode(nodeId: string) {
  const node = nodes.value.find((n) => n.id === nodeId)
  if (!node) return
  highlightedNodeId.value = nodeId
  setCenter(node.position.x, node.position.y, {zoom: 0.9})
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
    const result = await appAgentApi.run(appId, [{role: 'user', content: text}])
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
      <el-button link @click="router.push('/app-agents')">
        <el-icon>
          <ArrowLeft/>
        </el-icon>
        返回
      </el-button>
      <span class="app-name">
        {{ appName }}
        <span v-if="dirty" class="dirty-dot" title="有未保存的修改"/>
        <el-tag size="small" :type="appType === 'agent' ? 'success' : 'info'">
          {{ appType === 'agent' ? '智能体' : '编排' }}
        </el-tag>
      </span>

      <div class="toolbar-actions">
        <el-button v-if="appType !== 'agent'" :icon="VideoPlay" plain @click="toggleDebug">
          {{ debugVisible ? '收起调试' : '运行调试' }}
        </el-button>
        <el-button :icon="Promotion" plain @click="goChat">对话调试</el-button>
        <el-tooltip content="历史版本" placement="bottom">
          <el-button
              v-if="appType !== 'agent'"
              :icon="Clock"
              text
              circle
              class="toolbar-icon-btn"
              @click="openVersions"
          />
        </el-tooltip>
        <el-button :icon="CopyDocument" :loading="saving" plain @click="saveDraft">保存草稿</el-button>
        <el-button :icon="CircleCheck" class="btn-gradient" :loading="publishing" @click="publish">发布</el-button>
      </div>
    </div>

    <div class="editor-main">
      <!-- 左侧竖向操作栏 -->
      <aside class="op-rail">
        <!-- 编辑 -->
        <div class="op-group">
          <el-tooltip v-for="t in editTools" :key="t.key" :content="t.tip" placement="right">
            <span class="op-wrap">
              <el-button class="op-btn" text circle :disabled="t.disabled()" @click="t.action">
                <el-icon :size="16"><component :is="t.icon"/></el-icon>
              </el-button>
            </span>
          </el-tooltip>
        </div>

        <div class="op-divider"/>

        <!-- 删除 -->
        <div class="op-group">
          <el-tooltip content="删除选中 (Delete)" placement="right">
            <span class="op-wrap">
              <el-button
                  class="op-btn op-btn-danger"
                  text
                  circle
                  type="danger"
                  :disabled="!hasSelection"
                  @click="removeSelected"
              >
                <el-icon :size="16"><Delete/></el-icon>
              </el-button>
            </span>
          </el-tooltip>
        </div>

        <div class="op-divider"/>

        <!-- 布局 -->
        <div class="op-group">
          <el-popover placement="right-start" :width="176" trigger="click" :show-arrow="false"
                      popper-class="layout-popover">
            <template #reference>
              <el-tooltip :content="`对齐选中节点（已选 ${selectedCount} 个，至少 2 个）`" placement="right">
                <span class="op-wrap">
                  <el-button class="op-btn" text circle :disabled="selectedCount < 2">
                    <el-icon :size="16"><Aim/></el-icon>
                  </el-button>
                </span>
              </el-tooltip>
            </template>
            <div class="layout-menu">
              <div class="layout-menu-tip">已选中 {{ selectedCount }} 个节点</div>
              <div class="layout-menu-item" @click="alignNodes('left')">左对齐</div>
              <div class="layout-menu-item" @click="alignNodes('hcenter')">水平居中</div>
              <div class="layout-menu-item" @click="alignNodes('right')">右对齐</div>
              <div class="layout-menu-item" @click="alignNodes('top')">顶对齐</div>
              <div class="layout-menu-item" @click="alignNodes('vcenter')">垂直居中</div>
              <div class="layout-menu-item" @click="alignNodes('bottom')">底对齐</div>
            </div>
          </el-popover>
          <el-popover placement="right-start" :width="176" trigger="click" :show-arrow="false"
                      popper-class="layout-popover">
            <template #reference>
              <el-tooltip :content="`均匀分布选中节点（已选 ${selectedCount} 个，至少 2 个）`" placement="right">
                <span class="op-wrap">
                  <el-button class="op-btn" text circle :disabled="selectedCount < 2">
                    <el-icon :size="16"><Rank/></el-icon>
                  </el-button>
                </span>
              </el-tooltip>
            </template>
            <div class="layout-menu">
              <div class="layout-menu-tip">已选中 {{ selectedCount }} 个节点</div>
              <div class="layout-menu-item" @click="distributeNodes('horizontal')">水平等距分布</div>
              <div class="layout-menu-item" @click="distributeNodes('vertical')">垂直等距分布</div>
            </div>
          </el-popover>
          <el-tooltip content="自动布局全部节点" placement="right">
            <span class="op-wrap">
              <el-button class="op-btn" text circle @click="autoLayout">
                <el-icon :size="16"><MagicStick/></el-icon>
              </el-button>
            </span>
          </el-tooltip>
        </div>

        <div class="op-divider"/>

        <el-popover placement="right-end" :width="420" trigger="hover" popper-class="shortcut-popover">
          <template #reference>
            <el-tooltip content="快捷键" placement="right">
              <span class="op-wrap">
                <el-button class="op-btn" text circle>
                  <el-icon :size="16"><QuestionFilled/></el-icon>
                </el-button>
              </span>
            </el-tooltip>
          </template>
          <div class="shortcut-list">
            <div v-for="g in shortcutGroups" :key="g.title" class="shortcut-group">
              <div class="shortcut-group-title">{{ g.title }}</div>
              <div v-for="s in g.items" :key="g.title + s.keys" class="shortcut-item">
                <span class="shortcut-keys">{{ s.keys }}</span>
                <span class="shortcut-desc">{{ s.desc }}</span>
              </div>
            </div>
            <div class="shortcut-note">{{ shortcutNote }}</div>
          </div>
        </el-popover>
      </aside>

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
                <span>· 拖拽空白平移画布，按住 Shift 拖拽可框选多节点，按住 Ctrl 点击可追加选中</span>
              </div>
            </div>
            <!-- 多选键注意：vue-flow 用 event.key 匹配按键，Ctrl 键的 key 是 'Control'、Cmd 键的 key 是 'Meta'；传 'Ctrl' 永远匹配不上 -->
            <VueFlow
                v-model:nodes="nodes"
                v-model:edges="edges"
                :default-viewport="{ zoom: 0.85 }"
                :min-zoom="0.2"
                :max-zoom="2"
                :selection-key-code="'Shift'"
                :multi-selection-key-code="['Meta', 'Control']"
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
                    :style="data.nodeType === 'condition' ? { minHeight: `${52 + branchHandlesOf(data).length * 26}px` } : undefined"
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
                    <div class="node-icon" :style="{ background: metaOf(data.nodeType).color }">
                      <el-icon :size="16" color="#fff">
                        <component :is="iconOf(metaOf(data.nodeType).icon)"/>
                      </el-icon>
                    </div>
                    <div class="node-head-text">
                      <span class="node-title">{{ data.label }}</span>
                      <span class="node-desc">{{ metaOf(data.nodeType).desc }}</span>
                    </div>
                    <el-tooltip v-if="data.remark" :content="data.remark" placement="top" :show-after="200">
                      <span class="node-remark"><el-icon :size="12"><Notebook/></el-icon></span>
                    </el-tooltip>
                  </div>
                  <span v-if="data.runCost !== undefined" class="run-cost">{{ data.runCost }}ms</span>
                  <div v-if="data.runError" class="run-error-msg" :title="data.runError">{{ data.runError }}</div>
                  <Handle type="target" :position="Position.Left" class="node-handle"></Handle>
                  <!-- 条件分支：多分支模式下按分支配置动态渲染出点 -->
                  <template v-if="data.nodeType === 'condition'">
                    <template v-for="(h, i) in branchHandlesOf(data)" :key="h.key">
                  <span
                      class="branch-tag"
                      :style="{
                      top: `calc(${(i + 1) / (branchHandlesOf(data).length + 1) * 100}% - 8px)`,
                      color: h.color,
                      borderColor: h.color,
                      background: `${h.color}1f`
                    }"
                  >{{ h.label }}</span
                  >
                      <Handle
                          type="source"
                          :position="Position.Right"
                          :id="h.key"
                          class="node-handle handle-branch"
                          :style="{
                      top: `${(i + 1) / (branchHandlesOf(data).length + 1) * 100}%`,
                      borderColor: h.color,
                      background: h.color
                    }"
                      ></Handle>
                      <span
                          class="node-add-btn add-btn-branch"
                          :style="{ top: `${(i + 1) / (branchHandlesOf(data).length + 1) * 100}%` }"
                          :title="`在「${h.label}」分支后添加节点`"
                          @click.stop="openAddMenu($event, id, h.key)"
                          @mousedown.stop
                      >+</span>
                    </template>
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
              <Background :gap="24" :pattern-color="gridColor" :line-width="1"/>
              <Controls position="bottom-left" class="flow-controls"/>
              <MiniMap
                  pannable
                  zoomable
                  position="bottom-right"
                  class="minimap"
                  :node-color="miniMapNodeColor"
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
                  <div class="add-node-item-icon" :style="{ background: metaOf(t).color }">
                    <el-icon :size="12" color="#fff">
                      <component :is="iconOf(metaOf(t).icon)"/>
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
                <NodeConfigPanel
                    :key="selectedNodeId + ':' + panelVersion"
                    :node="selectedNode"
                    :edges="edges"
                    :chat-models="chatModels"
                    :rerank-models="rerankModels"
                    :datasets="datasets"
                    :tools="allTools"
                    :vars="varItemsFor(selectedNodeId || '')"
                />
              </template>
              <template v-else-if="selectedEdgeEnds">
                <div class="edge-info">
                  <div class="edge-route">
                    <div class="edge-end">
                      <div
                          class="node-icon"
                          :style="{ background: metaOf(selectedEdgeEnds.srcType).color }"
                      >
                        <el-icon :size="14" color="#fff">
                          <component :is="iconOf(metaOf(selectedEdgeEnds.srcType).icon)"/>
                        </el-icon>
                      </div>
                      <span class="edge-end-label">{{ selectedEdgeEnds.srcLabel }}</span>
                    </div>
                    <span class="edge-arrow">→</span>
                    <div class="edge-end">
                      <div
                          class="node-icon"
                          :style="{ background: metaOf(selectedEdgeEnds.tgtType).color }"
                      >
                        <el-icon :size="14" color="#fff">
                          <component :is="iconOf(metaOf(selectedEdgeEnds.tgtType).icon)"/>
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
                <el-empty description="选中节点后在此配置" :image-size="80"/>
              </template>
            </template>
            <div v-else class="config-collapsed-tip" title="展开配置面板">配置</div>
          </aside>
        </div>
      </template>
    </div>

    <template v-if="appType !== 'agent'">
      <!-- 运行调试面板 -->
      <div v-if="debugVisible" class="debug-panel">
        <div class="debug-head">
          <div class="debug-title">
            <el-icon>
              <VideoPlay/>
            </el-icon>
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
            <el-empty v-else description="输入测试消息后点击「运行」，结果将在此展示" :image-size="52"/>
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
  background: var(--bg-card);
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
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}

.toolbar-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-icon-btn {
  color: var(--text-secondary);
}
.toolbar-icon-btn:hover {
  color: var(--brand-1);
  background: var(--fill-light);
}

.editor-main {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* ---------- 左侧竖向操作栏 ---------- */
.op-rail {
  width: 48px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
  background: var(--bg-card);
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
  overflow-x: hidden;
}

.op-group {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* el-tooltip / el-dropdown / el-popover 各自插入的包裹层 display 不同
   （inline-flex / inline-block），收缩宽度与默认对齐方式不一致，
   会把按钮挤到不同水平位置。统一撑满并居中，保证整列对齐。 */
.op-group > *,
.op-rail > :not(.op-group):not(.op-divider),
.op-wrap,
.op-group :deep(.el-dropdown),
.op-group :deep(.el-popover),
.op-group :deep(.el-tooltip__trigger) {
  width: 100%;
  display: flex;
  justify-content: center;
  flex-shrink: 0;
}

/* 对齐/均匀分布的自定义弹出菜单 */
.layout-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.layout-menu-tip {
  padding: 4px 10px;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  border-bottom: 1px dashed var(--border-color);
}

.layout-menu-item {
  padding: 7px 12px;
  font-size: 13px;
  color: var(--text-primary, #1f2329);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s ease;
  white-space: nowrap;
}

.layout-menu-item:hover {
  background: var(--el-color-primary-light-9);
  color: var(--brand-1);
}

.op-divider {
  width: 60%;
  height: 1px;
  flex-shrink: 0;
  background: var(--el-border-color-lighter);
}

/* disabled 按钮不触发鼠标事件，tooltip 需外层 span 承接 hover */
.op-wrap {
  cursor: inherit;
}

/* 纯图标方形按钮，竖向排列 */
.op-btn {
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  flex-shrink: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  transition: all 0.15s ease;
}

.op-btn:hover:not(:disabled) {
  background: var(--el-color-primary-light-9);
  color: var(--brand-1);
}

.op-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
  background: transparent;
  color: var(--text-secondary);
}

.op-btn-danger:hover:not(:disabled) {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.op-btn .el-icon {
  margin: 0;
}

.editor-body {
  flex: 1;
  display: flex;
  min-width: 0;
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
  background-color: var(--canvas-bg);
  background-image: radial-gradient(var(--canvas-dot) 1px, transparent 1px);
  background-size: 22px 22px;
}

.flow {
  width: 100%;
  height: 100%;
}

.flow :deep(.vue-flow__edge-path) {
  stroke: var(--flow-edge);
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

/* 节点卡片（简约：纯色圆角图标 + 标题/描述，选中蓝框） */
.flow-node {
  position: relative;
  width: 210px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(31, 36, 55, 0.05);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.flow-node:hover {
  border-color: #2970ff;
}

.flow-node.selected {
  border-color: #2970ff;
  box-shadow: 0 0 0 2px rgba(41, 112, 255, 0.28);
}

.node-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
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
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-desc {
  margin-top: 1px;
  font-size: 11px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-handle {
  width: 9px !important;
  height: 9px !important;
  background: #2970ff !important;
  border: 2px solid var(--bg-card) !important;
  transition: transform 0.15s ease;
}

.flow-node:hover .node-handle {
  transform: scale(1.25);
}

.node-add-btn {
  position: absolute;
  right: -28px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--bg-card);
  border: 1px solid var(--brand-1);
  color: var(--brand-1);
  font-size: 14px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;
  z-index: 6;
  user-select: none;
}

.flow-node:hover .node-add-btn {
  opacity: 1;
}

.node-add-btn:hover {
  background: var(--brand-1);
  color: #fff;
  transform: translateY(-50%) scale(1.1);
}

/* 分支手柄的纵向位置与配色由节点内联样式按分支数量动态计算 */
.flow-node.condition .handle-branch {
  width: 9px !important;
  height: 9px !important;
  min-width: 9px;
  min-height: 9px;
  border-width: 1.5px;
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
  white-space: nowrap;
  max-width: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
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
  border: 1px solid var(--border-color);
}

.minimap :deep(.vue-flow__minimap-mask) {
  fill: var(--fill-lighter);
  stroke: var(--border-color);
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
  background: var(--bg-elevated);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: var(--shadow-pop);
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
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
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
  background: var(--bg-card);
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
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: 28px;
  display: flex;
  justify-content: center;
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
  background: var(--fill-light);
  border: 1px solid var(--border-color);
  border-radius: 8px;
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
  background: var(--fill-lighter);
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
  color: var(--brand-1);
  background: var(--fill-light);
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

/* ---------- 节点运行状态（静态描边，克制动效） ---------- */
.flow-node.run-success {
  border-color: #67c23a;
  box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.18);
}

.flow-node.run-error {
  border-color: #f56c6c;
  box-shadow: 0 0 0 2px rgba(245, 108, 108, 0.22);
}

.flow-node.run-skipped {
  opacity: 0.5;
  filter: grayscale(0.5);
}

.flow-node.run-highlight {
  border-color: var(--brand-1);
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.25);
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

.shortcut-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 60vh;
  overflow-y: auto;
}

.shortcut-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.shortcut-group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary, #1f2937);
  padding-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.shortcut-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 12px;
}

.shortcut-keys {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: #5b6cff;
  background: #f1f2ff;
  border-radius: 4px;
  padding: 1px 6px;
  flex: none;
  max-width: 45%;
  white-space: nowrap;
}

.shortcut-desc {
  color: var(--text-secondary, #6b7280);
  line-height: 1.5;
}

.shortcut-note {
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-tertiary, #909399);
  padding-top: 6px;
  border-top: 1px dashed var(--el-border-color-lighter);
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
  background: var(--fill-light, #f7f8fc);
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
  border-radius: 8px;
  display: flex;
  color: #fff;
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
  color: var(--text-tertiary);
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
  background: var(--bg-card);
  padding: 1px 6px;
  border-radius: 8px;
}

.run-error-msg {
  margin-top: 8px;
  font-size: 10.5px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
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
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
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
  background: var(--fill-light);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 10px;
}

.debug-answer-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
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
  background: var(--fill-lighter);
}

.debug-trace-item.active {
  border-color: var(--brand-1);
  background: var(--el-color-primary-light-9);
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
  background: var(--fill-lighter);
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
