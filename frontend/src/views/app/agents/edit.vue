<script setup lang="ts">
import { type Component, computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Aim,
  ArrowLeft,
  CircleCheck,
  Clock,
  CopyDocument,
  Delete,
  Expand,
  Files,
  Fold,
  MagicStick,
  Promotion,
  QuestionFilled,
  Rank,
  RefreshLeft,
  RefreshRight,
  VideoPlay
} from '@element-plus/icons-vue'
import { useVueFlow, VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { appAgentApi } from '@/api/app-agent.ts'
import { appSkillApi } from '@/api/app-skill.ts'
import { knowledgeApi } from '@/api/knowledge.ts'
import { modelApi } from '@/api/model.ts'
import { appAgentToolApi } from '@/api/app-tool-info.ts'
import type {
  AppAgentTool,
  ChatModelInfo,
  KnowledgeDataset,
  Skill,
  WorkflowNodeType
} from '@/api/types'
import type { VarItem } from '@/utils/flow.ts'
import { branchHandlesOf, dslToFlow, flowToDsl, genNodeId, NODE_TYPE_META } from '@/utils/flow.ts'
import { checkStructure, validateWorkflow } from '@/utils/flowValidation.ts'
import { buildInitialConfig, loadNodeSchemas } from '@/utils/nodeSchema.ts'
import NodeConfigPanel from './components/NodeConfigPanel.vue'
import FlowNode from './components/FlowNode.vue'
import AddNodeMenu from './components/AddNodeMenu.vue'
import DebugPanel from './components/DebugPanel.vue'
import VersionsDialog from './components/VersionsDialog.vue'
import AgentConfigPanel from './components/AgentConfigPanel.vue'
import { iconOf, metaOf, miniMapNodeColor } from './nodeVisual.ts'
import { useFlowHistory } from './composables/useFlowHistory.ts'
import { useBranchEdges } from './composables/useBranchEdges.ts'
import { useFlowClipboard } from './composables/useFlowClipboard.ts'
import { useNodeLayout } from './composables/useNodeLayout.ts'
import { useDebugRun } from './composables/useDebugRun.ts'
import { useThemeStore } from '@/stores/theme.ts'

const route = useRoute()
const router = useRouter()
const appId = Number(route.params.id)
const themeStore = useThemeStore()

/** 画布网格点颜色跟随主题 */
const gridColor = computed(() => (themeStore.isDark ? '#333a52' : '#d5dbe3'))

// ---------- 画布 ----------
const nodes = ref<Array<any>>([])
const edges = ref<Array<any>>([])
const { addNodes, removeNodes, onConnect, setCenter, fitView, getNodes, getEdges } = useVueFlow()

// ---------- 选中与编辑基线 ----------
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
/** 当前选中节点的内容基线（区分编辑与切换选中） */
let editBaseline = ''

// ---------- 组合逻辑 ----------
const { historyStack, historyIndex, snapshot, undo, redo } = useFlowHistory(nodes, edges, () => {
  selectedNodeId.value = null
  selectedEdgeId.value = null
  editBaseline = ''
})
const { branchLabelOf, branchStyleOf, syncBranchEdges } = useBranchEdges(nodes, edges)
const { copySelected, pasteClipboard } = useFlowClipboard({
  edges,
  getNodes,
  addNodes,
  snapshot,
  branchStyleOf
})
const { alignNodes, distributeNodes, autoLayout } = useNodeLayout({
  nodes,
  edges,
  getNodes,
  snapshot
})

// ---------- 左侧操作栏「编辑」组 ----------
/** 编辑组按钮配置：统一 tooltip / 禁用逻辑 / 点击动作 */
const editTools: Array<{
  key: string
  icon: Component
  tip: string
  disabled: () => boolean
  action: () => void
}> = [
  {
    key: 'undo',
    icon: RefreshLeft,
    tip: '撤销 (Ctrl+Z)',
    disabled: () => historyIndex.value <= 0,
    action: undo
  },
  {
    key: 'redo',
    icon: RefreshRight,
    tip: '重做 (Ctrl+Shift+Z)',
    disabled: () => historyIndex.value >= historyStack.value.length - 1,
    action: redo
  },
  {
    key: 'copy',
    icon: CopyDocument,
    tip: '复制选中 (Ctrl+C)',
    disabled: () => false,
    action: copySelected
  },
  { key: 'paste', icon: Files, tip: '粘贴 (Ctrl+V)', disabled: () => false, action: pasteClipboard }
]

// ---------- 数据 ----------
const appName = ref('')
const appType = ref('chatflow')
const chatModels = ref<ChatModelInfo[]>([])
const rerankModels = ref<ChatModelInfo[]>([])
const datasets = ref<KnowledgeDataset[]>([])
const allTools = ref<AppAgentTool[]>([])
const boundToolIds = ref<number[]>([])
const boundDatasetIds = ref<number[]>([])
const boundSkillIds = ref<number[]>([])
const allSkills = ref<Skill[]>([])
const welcomeMessage = ref('')
const openingQuestionsText = ref('')
const saving = ref(false)
const publishing = ref(false)

// ---------- 面板折叠 ----------
const configCollapsed = ref(false)

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
      data: { label: NODE_TYPE_META[type].label, nodeType: type, config: buildInitialConfig(type) }
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
const addMenuFor = ref<{ x: number; y: number; sourceId: string; handle: string | null } | null>(
  null
)

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
    position: { x, y },
    data: {
      label: NODE_TYPE_META[type].label,
      nodeType: type,
      config: buildInitialConfig(type)
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
      setCenter(newNode.position.x + NODE_W / 2, newNode.position.y + 40, { zoom: 0.9 })
    })
  }
}

const selectedNode = computed(() => nodes.value.find((n) => n.id === selectedNodeId.value) ?? null)
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
      { keys: 'Ctrl + Z', desc: '撤销上一步画布操作，最多回退 50 步' },
      { keys: 'Ctrl + Shift + Z', desc: '重做被撤销的操作' },
      { keys: 'Ctrl + Y', desc: '重做（与 Ctrl + Shift + Z 等效）' }
    ]
  },
  {
    title: '复制 / 删除',
    items: [
      { keys: 'Ctrl + C', desc: '复制选中节点（含节点配置，支持框选 / Ctrl 多选）' },
      { keys: 'Ctrl + V', desc: '粘贴到画布左上角，逐个错位 40px，并自动重建它们之间的连线' },
      { keys: 'Delete / Backspace', desc: '删除选中节点或连线；删节点会一并移除其所有连线' }
    ]
  },
  {
    title: '选中节点',
    items: [
      { keys: '单击节点', desc: '选中节点，并在右侧打开该节点的配置面板' },
      { keys: 'Ctrl / Cmd + 点击', desc: '在已选节点上追加或取消选中，实现多选' },
      { keys: 'Shift + 拖拽空白', desc: '框选多个节点，可批量移动 / 复制 / 删除' },
      { keys: '单击画布空白', desc: '取消选中，收起右侧配置面板' }
    ]
  },
  {
    title: '画布与连线',
    items: [
      { keys: '拖拽节点', desc: '移动节点位置，松手后可用 Ctrl + Z 撤销' },
      { keys: '拖拽画布空白', desc: '平移画布；按住 Shift 拖拽则框选多节点' },
      { keys: '滚轮 / 触控板双指', desc: '缩放画布，缩放范围 20% ~ 200%' },
      { keys: '双击画布空白', desc: '放大一级' },
      { keys: '拖拽节点右侧圆点', desc: '从出点拉出连线，接到目标节点左侧入点' },
      { keys: '点击节点「+」', desc: '快速插入下一个节点并自动连线（结束节点无「+」）' },
      { keys: '单击连线', desc: '选中连线，按 Delete 删除' }
    ]
  },
  {
    title: '布局工具（左侧栏）',
    items: [
      { keys: '选中 ≥ 2 个节点', desc: '左 / 水平居中 / 右 / 顶 / 垂直居中 / 底对齐' },
      { keys: '选中 ≥ 3 个节点', desc: '水平 / 垂直等距分布，保持首尾节点不动' },
      { keys: '自动布局', desc: '一键按流程层级重排全部节点' }
    ]
  }
]
const shortcutNote =
  '说明：快捷键在输入框 / 文本域聚焦时不生效；撤销重做只覆盖画布结构（节点与连线的增删改移），右侧表单配置修改不会进入历史栈。'

// ---------- 未保存修改追踪 ----------
const dirty = ref(false)
const hydrated = ref(false)

watch(
  [
    nodes,
    edges,
    boundToolIds,
    boundDatasetIds,
    boundSkillIds,
    welcomeMessage,
    openingQuestionsText
  ],
  () => {
    if (hydrated.value) dirty.value = true
  },
  { deep: true }
)
watch(
  selectedData,
  (val) => {
    if (!hydrated.value) return
    if (JSON.stringify(val ?? null) !== editBaseline) dirty.value = true
  },
  { deep: true }
)

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
  if (app.skillIds) {
    try {
      boundSkillIds.value = JSON.parse(app.skillIds) as number[]
    } catch {
      boundSkillIds.value = []
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
    ensureStartEnd()
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

async function loadSkills() {
  try {
    allSkills.value = await appSkillApi.enabled()
  } catch {
    allSkills.value = []
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
}

function onPaneClick() {
  selectedNodeId.value = null
  selectedEdgeId.value = null
}

function onEdgeClick({ edge }: any) {
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
    if (e.shiftKey) {
      redo()
    } else {
      undo()
    }
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

// ---------- 保存 / 发布 ----------
async function saveDraft() {
  saving.value = true
  try {
    if (appType.value === 'agent') {
      await appAgentApi.update(appId, {
        toolIds: JSON.stringify(boundToolIds.value),
        datasetIds: JSON.stringify(boundDatasetIds.value),
        skillIds: JSON.stringify(boundSkillIds.value),
        welcomeMessage: welcomeMessage.value,
        openingQuestions: JSON.stringify(
          openingQuestionsText.value
            .split('\n')
            .map((q) => q.trim())
            .filter(Boolean)
        )
      })
    } else {
      await appAgentApi.update(appId, { workflowJson: flowToDsl(nodes.value, edges.value) })
    }
    ElMessage.success('草稿已保存')
    dirty.value = false
  } finally {
    saving.value = false
  }
}

// ---------- 运行调试 ----------
const {
  debugVisible,
  debugInput,
  running,
  runResult,
  highlightedNodeId,
  curRunId,
  displayTrace,
  runStatusMeta,
  toggleDebug,
  clearRunState,
  highlightNode,
  runDebug,
  stopRun
} = useDebugRun({ appId, nodes, edges, setCenter, saveDraft })

async function publish() {
  if (appType.value !== 'agent' && nodes.value.length === 0) {
    ElMessage.warning('画布为空，请先拖入节点编排工作流再发布')
    return
  }
  if (appType.value !== 'agent') {
    // 全流程 schema 校验：必填缺失（error 级）为硬阻断并定位到首个问题节点；
    // 仅结构/效果类提示（warn）允许确认后继续
    const errors = validateWorkflow(nodes.value)
    if (errors.length > 0) {
      await ElMessageBox.alert(
        `以下节点存在必填配置缺失，请完善后再发布：\n${errors.map((e) => e.text).join('\n')}`,
        '无法发布',
        { confirmButtonText: '去完善', type: 'error' }
      ).catch(() => false)
      highlightNode(errors[0].node.id)
      return
    }
    const structure = checkStructure(nodes.value, edges.value)
    if (structure.length > 0) {
      const proceed = await ElMessageBox.confirm(
        `发布前检查到以下提示：\n${structure.join('\n')}\n\n仍要继续发布吗？`,
        '发布前检查',
        { confirmButtonText: '仍要发布', cancelButtonText: '返回检查', type: 'warning' }
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
      appType.value === 'agent'
        ? JSON.stringify({ nodes: [], edges: [] })
        : flowToDsl(nodes.value, edges.value)
    await appAgentApi.publish(appId, { workflowJson, promptConfig: '' })
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

/** 版本回滚成功：重新加载画布并清空运行态 */
async function onRolledBack() {
  await loadApp()
  clearRunState()
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
  const items: VarItem[] = [{ text: wrap('input'), desc: '用户输入' }]
  if (isCode) items.push({ text: 'outputs', desc: '全部节点输出集合' })
  // 开始节点定义的流程变量（全局可见）
  for (const n of nodes.value) {
    if (n.data?.nodeType !== 'start') continue
    const vars = n.data?.config?.variables
    if (!Array.isArray(vars)) continue
    for (const v of vars) {
      if (v?.name) items.push({ text: wrap(String(v.name)), desc: '开始节点流程变量' })
    }
  }
  for (const id of upstreamNodeIds(nodeId)) {
    const n = nodes.value.find((x) => x.id === id)
    if (!OUTPUT_NODE_TYPES.has(n?.data?.nodeType)) continue
    const alias = n?.data?.config?.outputVar
    const label = n?.data?.label || id
    items.push({ text: wrap(id), desc: `节点「${label}」的输出` })
    if (alias && !isCode) {
      items.push({ text: `{{${alias}}}`, desc: `节点「${label}」的输出变量别名` })
    }
  }
  return items
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
  loadSkills()
  // 加载后端节点 Schema（默认配置/必填校验/元数据融合；失败自动回退静态定义）
  loadNodeSchemas()
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
      <el-button link @click="router.push('/app/agents')">
        <el-icon>
          <ArrowLeft />
        </el-icon>
        返回
      </el-button>
      <span class="app-name">
        {{ appName }}
        <span v-if="dirty" class="dirty-dot" title="有未保存的修改" />
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
            @click="versionsVisible = true"
          />
        </el-tooltip>
        <el-button :icon="CopyDocument" :loading="saving" plain @click="saveDraft"
          >保存草稿</el-button
        >
        <el-button :icon="CircleCheck" class="btn-gradient" :loading="publishing" @click="publish"
          >发布</el-button
        >
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
                <el-icon :size="16"><component :is="t.icon" /></el-icon>
              </el-button>
            </span>
          </el-tooltip>
        </div>

        <div class="op-divider" />

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
                <el-icon :size="16"><Delete /></el-icon>
              </el-button>
            </span>
          </el-tooltip>
        </div>

        <div class="op-divider" />

        <!-- 布局 -->
        <div class="op-group">
          <el-popover
            placement="right-start"
            :width="176"
            trigger="click"
            :show-arrow="false"
            popper-class="layout-popover"
          >
            <template #reference>
              <el-tooltip
                :content="`对齐选中节点（已选 ${selectedCount} 个，至少 2 个）`"
                placement="right"
              >
                <span class="op-wrap">
                  <el-button class="op-btn" text circle :disabled="selectedCount < 2">
                    <el-icon :size="16"><Aim /></el-icon>
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
          <el-popover
            placement="right-start"
            :width="176"
            trigger="click"
            :show-arrow="false"
            popper-class="layout-popover"
          >
            <template #reference>
              <el-tooltip
                :content="`均匀分布选中节点（已选 ${selectedCount} 个，至少 2 个）`"
                placement="right"
              >
                <span class="op-wrap">
                  <el-button class="op-btn" text circle :disabled="selectedCount < 2">
                    <el-icon :size="16"><Rank /></el-icon>
                  </el-button>
                </span>
              </el-tooltip>
            </template>
            <div class="layout-menu">
              <div class="layout-menu-tip">已选中 {{ selectedCount }} 个节点</div>
              <div class="layout-menu-item" @click="distributeNodes('horizontal')">
                水平等距分布
              </div>
              <div class="layout-menu-item" @click="distributeNodes('vertical')">垂直等距分布</div>
            </div>
          </el-popover>
          <el-tooltip content="自动布局全部节点" placement="right">
            <span class="op-wrap">
              <el-button class="op-btn" text circle @click="autoLayout">
                <el-icon :size="16"><MagicStick /></el-icon>
              </el-button>
            </span>
          </el-tooltip>
        </div>

        <div class="op-divider" />

        <el-popover
          placement="right-end"
          :width="420"
          trigger="hover"
          popper-class="shortcut-popover"
        >
          <template #reference>
            <el-tooltip content="快捷键" placement="right">
              <span class="op-wrap">
                <el-button class="op-btn" text circle>
                  <el-icon :size="16"><QuestionFilled /></el-icon>
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
      <AgentConfigPanel
        v-if="appType === 'agent'"
        v-model:tool-ids="boundToolIds"
        v-model:dataset-ids="boundDatasetIds"
        v-model:skill-ids="boundSkillIds"
        v-model:welcome="welcomeMessage"
        v-model:opening-questions="openingQuestionsText"
        :all-tools="allTools"
        :datasets="datasets"
        :all-skills="allSkills"
        :saving="saving"
        :publishing="publishing"
        @save="saveDraft"
        @publish="publish"
      />

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
                <span
                  >· 拖拽空白平移画布，按住 Shift 拖拽可框选多节点，按住 Ctrl 点击可追加选中</span
                >
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
                <FlowNode
                  :id="id"
                  :data="data"
                  :selected="selected"
                  :highlighted="id === highlightedNodeId"
                  @open-add-menu="openAddMenu"
                />
              </template>
              <Background :gap="24" :pattern-color="gridColor" :line-width="1" />
              <Controls position="bottom-left" class="flow-controls" />
              <MiniMap
                pannable
                zoomable
                position="bottom-right"
                class="minimap"
                :node-color="miniMapNodeColor"
              />
            </VueFlow>

            <!-- 节点右侧「+」弹出的节点选择浮层（Dify 式） -->
            <AddNodeMenu :menu="addMenuFor" @select="onAddNodeClick" />
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
                  :skills="allSkills"
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
                          <component :is="iconOf(metaOf(selectedEdgeEnds.srcType).icon)" />
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
                          <component :is="iconOf(metaOf(selectedEdgeEnds.tgtType).icon)" />
                        </el-icon>
                      </div>
                      <span class="edge-end-label">{{ selectedEdgeEnds.tgtLabel }}</span>
                    </div>
                  </div>
                  <div v-if="selectedEdgeEnds.branch" class="edge-branch">
                    <el-tag
                      :type="selectedEdgeEnds.branch === '是' ? 'success' : 'danger'"
                      size="small"
                      effect="light"
                    >
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
                  <el-button type="danger" plain :icon="Delete" @click="removeSelected"
                    >删除该连线</el-button
                  >
                </div>
              </template>
              <template v-else>
                <el-empty description="选中节点后在此配置" :image-size="80" />
              </template>
            </template>
            <div v-else class="config-collapsed-tip" title="展开配置面板">配置</div>
          </aside>
        </div>
      </template>
    </div>

    <template v-if="appType !== 'agent'">
      <!-- 运行调试面板（SSE 实时节点状态） -->
      <DebugPanel
        v-model:visible="debugVisible"
        v-model:input="debugInput"
        :running="running"
        :cur-run-id="curRunId"
        :run-result="runResult"
        :display-trace="displayTrace"
        :highlighted-node-id="highlightedNodeId"
        :run-status-meta="runStatusMeta"
        @run="runDebug"
        @stop="stopRun"
        @highlight="highlightNode"
      />

      <!-- 版本历史弹窗 -->
      <VersionsDialog
        v-model:visible="versionsVisible"
        :app-id="appId"
        @rolled-back="onRolledBack"
      />
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

/* 配置面板 */
.config-panel {
  width: 290px;
  background: var(--bg-card);
  border-left: 1px solid var(--border-color);
  padding: 16px 14px;
  overflow-y: auto;
  transition:
    width 0.2s ease,
    padding 0.2s ease;
}

.config-panel.collapsed {
  width: 42px;
  padding: 16px 5px;
  overflow: visible;
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

/* ---------- 连线信息面板 ---------- */
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

/* ---------- 快捷键说明 ---------- */
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
</style>
