import type { AppWorkflow, WorkflowEdge, WorkflowNode, WorkflowNodeType } from '@/api/types'

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
    const parsed = JSON.parse(dsl) as AppWorkflow
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
  const workflow: AppWorkflow = {
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
