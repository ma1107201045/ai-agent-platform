import type { AppAgentWorkflow, WorkflowEdge, WorkflowNode, WorkflowNodeType } from '@/api/types'

/** 节点类型元信息（图标名对应 @element-plus/icons-vue，gradient 用于圆形渐变图标，参考 Dify 视觉） */
export const NODE_TYPE_META: Record<
  WorkflowNodeType,
  { label: string; icon: string; color: string; gradient: string; desc: string }
> = {
  start: { label: '开始', icon: 'Promotion', color: '#10b981', gradient: 'linear-gradient(135deg, #34d399 0%, #059669 100%)', desc: '对话入口' },
  end: { label: '结束', icon: 'CircleCheck', color: '#ef4444', gradient: 'linear-gradient(135deg, #f87171 0%, #dc2626 100%)', desc: '流程出口' },
  llm: { label: 'LLM', icon: 'Cpu', color: '#2970ff', gradient: 'linear-gradient(135deg, #60a5fa 0%, #2970ff 100%)', desc: '大模型对话' },
  agent: { label: 'Agent', icon: 'MagicStick', color: '#8b5cf6', gradient: 'linear-gradient(135deg, #a78bfa 0%, #7c3aed 100%)', desc: '自主规划+工具调用' },
  condition: { label: '条件分支', icon: 'Share', color: '#f59e0b', gradient: 'linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%)', desc: 'IF / ELSE 判断' },
  code: { label: '表达式计算', icon: 'Document', color: '#7a5af8', gradient: 'linear-gradient(135deg, #a78bfa 0%, #7a5af8 100%)', desc: 'MVEL 表达式求值' },
  http: { label: 'HTTP 请求', icon: 'Link', color: '#2ea9b0', gradient: 'linear-gradient(135deg, #4fd1c5 0%, #2ea9b0 100%)', desc: '调用外部 API' },
  template: { label: '模板', icon: 'Tickets', color: '#7048e8', gradient: 'linear-gradient(135deg, #9775fa 0%, #7048e8 100%)', desc: '变量插值渲染' },
  knowledge: { label: '知识库检索', icon: 'Collection', color: '#1c64f2', gradient: 'linear-gradient(135deg, #3b82f6 0%, #1c64f2 100%)', desc: 'RAG 语义检索' }
}

export const NODE_TYPE_LIST = Object.keys(NODE_TYPE_META) as WorkflowNodeType[]

/** 变量项（配置面板中可点击插入的 {{}} 变量） */
export interface VarItem {
  text: string
  desc: string
}

/** 条件分支项（多分支模式） */
export interface BranchItem {
  /** 出边 handle，与 edge.sourceHandle 对应 */
  key: string
  /** 分支展示名 */
  label: string
  /** 分支条件表达式 */
  expression: string
}

/** 默认分支（所有条件都不成立时）的 handle */
export const ELSE_BRANCH_KEY = 'else'

/** 分支配色（多分支时按序循环） */
export const BRANCH_COLORS = ['#67c23a', '#f56c6c', '#409eff', '#e6a23c', '#8b5cf6', '#2ea9b0']

/**
 * 各节点类型的默认配置。
 * 新建节点时以此为初始值，未配置的字段由后端按缺省值处理。
 */
export function defaultConfig(type: WorkflowNodeType): Record<string, unknown> {
  switch (type) {
    case 'start':
      return { variables: [], welcome: '' }
    case 'end':
      return { answerTemplate: '' }
    case 'llm':
      return {
        modelId: undefined,
        systemPrompt: '',
        userPrompt: '{{input}}',
        temperature: 0.7,
        topP: undefined,
        maxTokens: 0,
        outputFormat: 'text',
        datasetId: undefined,
        topK: 3,
        rerankModelId: undefined,
        scoreThreshold: 0,
        queryTemplate: '{{input}}',
        knowledgeTemplate: ''
      }
    case 'agent':
      return {
        modelId: undefined,
        systemPrompt: '',
        userPrompt: '{{input}}',
        toolIds: [],
        datasetIds: [],
        maxIterations: 6,
        includeSteps: false
      }
    case 'condition':
      return { expression: '' }
    case 'code':
      return { code: '' }
    case 'http':
      return {
        url: '',
        method: 'GET',
        authType: 'none',
        bodyType: 'none',
        bodyTemplate: '',
        responseType: 'text',
        jsonPath: '',
        ignoreStatus: false,
        timeoutSeconds: 30
      }
    case 'template':
      return { template: '{{input}}' }
    case 'knowledge':
      return {
        datasetId: undefined,
        topK: 3,
        rerankModelId: undefined,
        scoreThreshold: 0,
        queryTemplate: '{{input}}',
        outputFormat: 'text',
        itemTemplate: '【片段 {{index}}】{{content}}',
        separator: '\n\n'
      }
    default:
      return {}
  }
}

/** 通用执行策略默认值（重试 / 超时 / 错误处理 / 输出变量） */
export const DEFAULT_ADVANCED = {
  retries: 0,
  timeoutSeconds: 0,
  onError: 'fail',
  errorFallback: '',
  outputVar: ''
}

/** 读取条件节点的分支列表（多分支模式），无则为空数组 */
export function branchesOf(data: any): BranchItem[] {
  const list = data?.config?.branches
  if (!Array.isArray(list)) return []
  return list.map((b: any) => ({
    key: String(b?.key ?? ''),
    label: String(b?.label ?? b?.key ?? ''),
    expression: String(b?.expression ?? '')
  }))
}

/** 是否为多分支模式 */
export function isMultiBranch(data: any): boolean {
  return branchesOf(data).length > 0
}

/** 生成下一个分支 key，避免与现有重复 */
export function nextBranchKey(data: any): string {
  const used = new Set(branchesOf(data).map((b) => b.key))
  let i = used.size + 1
  while (used.has(`branch${i}`)) i += 1
  return `branch${i}`
}

/** 生成默认分支列表（切换到多分支时使用：两条分支 + 默认分支不入库） */
export function initialBranches(): BranchItem[] {
  return [
    { key: 'branch1', label: '分支1', expression: '' },
    { key: 'branch2', label: '分支2', expression: '' }
  ]
}

/** 节点在画布上渲染的分支 handle 列表（含默认分支） */
export function branchHandlesOf(data: any): Array<{ key: string; label: string; color: string }> {
  if (isMultiBranch(data)) {
    const list = branchesOf(data).map((b, i) => ({
      key: b.key,
      label: b.label || b.key,
      color: BRANCH_COLORS[i % BRANCH_COLORS.length]
    }))
    list.push({ key: ELSE_BRANCH_KEY, label: '默认', color: '#909399' })
    return list
  }
  return [
    { key: 'true', label: '是', color: '#67c23a' },
    { key: 'false', label: '否', color: '#f56c6c' }
  ]
}

/** 画布节点（Vue Flow 形态） */
export interface FlowNodeData {
  label: string
  nodeType: WorkflowNodeType
  config: Record<string, unknown>
  /** 节点备注（持久化到 config.remark） */
  remark?: string
}

let seed = 0

export function genNodeId(prefix = 'node'): string {
  seed += 1
  return `${prefix}-${Date.now()}-${seed}`
}

/** DSL -> Vue Flow 节点/边 */
export function dslToFlow(dsl?: string | null) {
  const nodes: Array<{
    id: string
    type: string
    position: { x: number; y: number }
    data: FlowNodeData
  }> = []
  const edges: Array<{
    id: string
    source: string
    target: string
    sourceHandle?: string
    targetHandle?: string
    label?: string
  }> = []
  if (!dsl) return { nodes, edges }
  try {
    const parsed = JSON.parse(dsl) as AppAgentWorkflow
    for (const n of parsed.nodes ?? []) {
      nodes.push({
        id: n.id,
        type: 'flow-node',
        position: n.position ?? { x: 120, y: 120 },
        data: {
          label: n.label || NODE_TYPE_META[n.type]?.label || n.type,
          nodeType: n.type,
          config: n.config ?? {},
          remark: n.config?.remark as string | undefined
        }
      })
    }
    for (const e of parsed.edges ?? []) {
      edges.push({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle,
        label: e.label
      })
    }
  } catch {
    // DSL 解析失败时返回空画布
  }
  return { nodes, edges }
}

/** Vue Flow 节点/边 -> DSL */
export function flowToDsl(
  nodes: Array<{ id: string; position: { x: number; y: number }; data: FlowNodeData }>,
  edges: Array<{
    id: string
    source: string
    target: string
    sourceHandle?: string
    targetHandle?: string
    label?: string
  }>
): string {
  const workflow: AppAgentWorkflow = {
    nodes: nodes.map(
      (n): WorkflowNode => ({
        id: n.id,
        type: n.data.nodeType,
        label: n.data.label,
        position: { x: Math.round(n.position.x), y: Math.round(n.position.y) },
        config: { ...(n.data.config ?? {}), remark: n.data.remark || undefined }
      })
    ),
    edges: edges.map(
      (e): WorkflowEdge => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle,
        label: e.label || undefined
      })
    )
  }
  return JSON.stringify(workflow)
}
