import { reactive } from 'vue'
import type { NodeFieldSchema, NodeTypeSchema, WorkflowNodeType } from '@/api/types'
import { orchestratorApi } from '@/api/orchestrator'
import { defaultConfig, NODE_TYPE_META } from '@/utils/flow'

/**
 * 节点 Schema 前端单例（数据源：GET /api/orchestrator/node-types）
 *
 * 职责：
 *  - 拉取后端节点 Schema 后统一缓存，并融合到 NODE_TYPE_META（后端 label 优先）；
 *  - 「默认配置」按后端 handler 声明补齐（与引擎 applyDefaults 行为一致）；
 *  - 「必填校验」按后端声明渲染（与引擎 NodeExecutionPolicy 行为一致）。
 *
 * 离线 / 接口失败时自动回退本地静态元数据，画布功能不受影响。
 */

const byType = reactive<Partial<Record<WorkflowNodeType, NodeTypeSchema>>>({})

/** Schema 是否已成功加载（供面板 watch 补默认值） */
export const schemaReady = reactive({ value: false })

let loadPromise: Promise<void> | null = null

export function schemaOf(type: WorkflowNodeType): NodeTypeSchema | undefined {
  return byType[type]
}

export function fieldsOf(type: WorkflowNodeType): NodeFieldSchema[] {
  return schemaOf(type)?.fields ?? []
}

export async function loadNodeSchemas(): Promise<void> {
  if (loadPromise) return loadPromise
  loadPromise = (async () => {
    try {
      const list = await orchestratorApi.nodeTypes()
      for (const s of list) {
        byType[s.code] = s
        const m = NODE_TYPE_META[s.code]
        if (m) {
          // 后端 label 优先，视觉信息（icon/渐变）仍保留前端静态定义
          m.label = s.label || m.label
        }
      }
    } catch {
      // 离线/后端不可用时回退本地静态元数据
    } finally {
      schemaReady.value = true
    }
  })()
  return loadPromise
}

function isBlank(v: unknown): boolean {
  if (v === undefined || v === null) return true
  if (typeof v === 'string') return v.trim() === '' || v === 'null'
  return false
}

function cloneDefault(v: unknown): unknown {
  return Array.isArray(v) ? [...v] : v
}

/**
 * 按后端 Schema 补齐配置默认值。
 * @param config  目标配置对象（原地修改）
 * @param type    节点类型
 * @param newNode 新建节点：空白值也覆盖为 schema 默认；已有节点（默认）仅补齐缺失键，
 *                与引擎 applyDefaults 语义保持一致
 */
export function fillSchemaDefaults(
  config: Record<string, unknown>,
  type: WorkflowNodeType,
  newNode = false
): Record<string, unknown> {
  if (!config) return config
  for (const f of fieldsOf(type)) {
    if (f.defaultValue === undefined || f.defaultValue === null) continue
    const cur = config[f.key]
    const missing = newNode ? isBlank(cur) : cur === undefined
    if (missing) config[f.key] = cloneDefault(f.defaultValue)
  }
  return config
}

/** 新建节点的初始配置：本地字段骨架 + schema 默认值（schema 优先） */
export function buildInitialConfig(type: WorkflowNodeType): Record<string, unknown> {
  const cfg: Record<string, unknown> = { ...defaultConfig(type) }
  return fillSchemaDefaults(cfg, type, true)
}

/** Schema 必填校验（与后端 NodeExecutionPolicy 相同规则） */
export function schemaIssues(config: Record<string, unknown> | undefined, type: WorkflowNodeType): string[] {
  if (!config) return []
  const issues: string[] = []
  for (const f of fieldsOf(type)) {
    if (f.required !== true) continue
    if (isBlank(config[f.key])) issues.push(f.label)
  }
  return issues
}
