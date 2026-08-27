import type { AppWorkflow, WorkflowEdge, WorkflowNode, WorkflowNodeType } from '@/api/types'

/** 节点类型元信息（图标名对应 @element-plus/icons-vue） */
export const NODE_TYPE_META: Record<
  WorkflowNodeType,
  { label: string; icon: string; color: string; desc: string }
> = {
  start: { label: '开始', icon: 'Promotion', color: '#67c23a', desc: '对话入口' },
  end: { label: '结束', icon: 'CircleCheck', color: '#f56c6c', desc: '流程出口' },
  llm: { label: 'LLM', icon: 'Cpu', color: '#409eff', desc: '大模型对话' },
  condition: { label: '条件分支', icon: 'Share', color: '#e6a23c', desc: 'IF / ELSE 判断' },
  code: { label: '代码', icon: 'Document', color: '#909399', desc: 'Python / JS 片段' },
  http: { label: 'HTTP 请求', icon: 'Link', color: '#8e44ad', desc: '调用外部 API' }
}

export const NODE_TYPE_LIST = Object.keys(NODE_TYPE_META) as WorkflowNodeType[]

/** 画布节点（Vue Flow 形态） */
export interface FlowNodeData {
  label: string
  nodeType: WorkflowNodeType
  config: Record<string, unknown>
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
          config: n.config ?? {}
        }
      })
    }
    for (const e of parsed.edges ?? []) {
      edges.push({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle
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
  }>
): string {
  const workflow: AppWorkflow = {
    nodes: nodes.map(
      (n): WorkflowNode => ({
        id: n.id,
        type: n.data.nodeType,
        label: n.data.label,
        position: { x: Math.round(n.position.x), y: Math.round(n.position.y) },
        config: n.data.config ?? {}
      })
    ),
    edges: edges.map(
      (e): WorkflowEdge => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle
      })
    )
  }
  return JSON.stringify(workflow)
}
