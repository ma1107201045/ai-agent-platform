/** 编排节点类型 */
export type WorkflowNodeType =
  | 'start'
  | 'end'
  | 'llm'
  | 'agent'
  | 'skill'
  | 'condition'
  | 'code'
  | 'http'
  | 'template'
  | 'knowledge'

/** 节点配置字段控件类型（与后端 orchestrator NodeField.type 一致） */
export type NodeFieldType =
  | 'text'
  | 'textarea'
  | 'code'
  | 'password'
  | 'number'
  | 'boolean'
  | 'select'
  | 'model'
  | 'knowledge'
  | 'tools'
  | 'datasets'
  | 'skill'
  | 'json'
  | 'branches'

/** select 下拉选项 */
export interface NodeFieldOption {
  label: string
  value: string
}

/** 节点配置字段描述（后端 orchestrator NodeField） */
export interface NodeFieldSchema {
  key: string
  label: string
  type: NodeFieldType
  description?: string
  defaultValue?: unknown
  required?: boolean
  placeholder?: string
  options?: NodeFieldOption[]
}

/** 节点类型 Schema（后端 /api/orchestrator/node-types 返回） */
export interface NodeTypeSchema {
  code: WorkflowNodeType
  label: string
  branch: boolean
  /** 该类型节点是否允许出边 */
  source: boolean
  /** 该类型节点是否允许入边 */
  target: boolean
  fields: NodeFieldSchema[]
}

/** DSL 节点定义 */
export interface WorkflowNode {
  id: string
  type: WorkflowNodeType
  label?: string
  position?: { x: number; y: number }
  config?: Record<string, unknown>
}

/** DSL 连线定义 */
export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  /** 连线文字标注 */
  label?: string
}

/** 工作流 DSL */
export interface AppAgentWorkflow {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

/** 工作流节点执行轨迹 */
export interface TraceItem {
  nodeId: string
  nodeType: string
  label: string
  status: 'success' | 'skipped' | 'error' | 'canceled'
  input?: string
  output?: string
  costMs: number
  error?: string
}

/** 工作流运行结果 */
export interface RunResult {
  runId?: string
  appId?: number
  /** running / success / failed / canceled / timeout */
  status?: string
  startedAt?: string
  finishedAt?: string
  costMs?: number
  error?: string
  answer: string
  trace: TraceItem[]
}
