/**
 * 工作流校验（纯函数，可单测）：
 * - nodeWarnings：单节点配置完整性检查
 * - validateWorkflow：运行/发布前的 error 级硬校验
 * - checkStructure：流程结构健康检查（start/end、孤立节点、循环依赖）
 */

import type { BranchItem, NodeDataLike } from './flow'

/** 校验用的最小节点/边形态（兼容 Vue Flow Node/Edge 与 DSL 转换产物） */
export interface ValidatableNode {
  id: string
  data: NodeDataLike
}

export interface ValidatableEdge {
  source: string
  target: string
}

export interface NodeWarning {
  severity: 'error' | 'warn'
  text: string
}

/** 节点配置完整性检查：缺少关键配置为 error，可能影响效果但不阻断运行为 warn */
export function nodeWarnings(data: NodeDataLike | null | undefined): NodeWarning[] {
  const cfg = data?.config ?? {}
  const list: NodeWarning[] = []
  const str = (v: unknown) => (v == null ? '' : String(v))
  const num = (v: unknown) => (typeof v === 'number' ? v : Number(v ?? 0))
  switch (data?.nodeType) {
    case 'llm':
    case 'agent':
      if (!cfg.modelId) list.push({ severity: 'error', text: '未配置模型' })
      else if (!str(cfg.userPrompt).trim()) {
        list.push({
          severity: 'warn',
          text:
            data.nodeType === 'agent'
              ? '用户提示词为空，Agent 将收不到任务输入'
              : '用户提示词为空，模型将收不到用户输入'
        })
      }
      break
    case 'http':
      if (!str(cfg.url).trim()) list.push({ severity: 'error', text: '未配置请求地址' })
      else if (cfg.bodyType && cfg.bodyType !== 'none' && !str(cfg.bodyTemplate).trim()) {
        list.push({ severity: 'warn', text: `已选请求体类型 ${cfg.bodyType}，但未填写请求体模板` })
      }
      break
    case 'code':
      if (!str(cfg.code).trim()) list.push({ severity: 'error', text: '未配置代码脚本' })
      break
    case 'condition': {
      const branches = Array.isArray(cfg.branches) ? cfg.branches : []
      if (branches.length > 0) {
        const empty = branches.filter((b: Partial<BranchItem>) => !str(b?.expression).trim()).length
        if (empty === branches.length) {
          list.push({ severity: 'warn', text: '所有分支条件均为空，将命中第一条分支' })
        }
      } else if (!str(cfg.expression).trim()) {
        list.push({ severity: 'warn', text: '未配置判断条件，默认走「是」分支' })
      }
      break
    }
    case 'template':
      if (!str(cfg.template).trim()) list.push({ severity: 'error', text: '未配置模板内容' })
      break
    case 'knowledge':
      if (!cfg.datasetId) list.push({ severity: 'error', text: '未选择数据集' })
      else if (num(cfg.scoreThreshold) > 0.95) {
        list.push({ severity: 'warn', text: '相似度阈值过高，可能召回不到内容' })
      }
      break
  }
  return list
}

export function hasNodeError(data: NodeDataLike | null | undefined): boolean {
  return nodeWarnings(data).some((w) => w.severity === 'error')
}

/** 运行/发布前统一校验，返回存在 error 级缺失的节点列表 */
export function validateWorkflow<T extends ValidatableNode>(
  nodes: T[]
): Array<{ node: T; text: string }> {
  const errors: Array<{ node: T; text: string }> = []
  for (const n of nodes) {
    for (const w of nodeWarnings(n.data)) {
      if (w.severity === 'error')
        errors.push({ node: n, text: `「${n.data?.label || '未命名'}」${w.text}` })
    }
  }
  return errors
}

/** 检查流程结构问题：start/end 缺失或重复、孤立节点、循环依赖（返回警告文案列表） */
export function checkStructure(nodes: ValidatableNode[], edges: ValidatableEdge[]): string[] {
  const issues: string[] = []
  const labelOf = (id: string) => nodes.find((n) => n.id === id)?.data?.label || id

  const startCount = nodes.filter((n) => n.data.nodeType === 'start').length
  const endCount = nodes.filter((n) => n.data.nodeType === 'end').length
  if (startCount === 0) issues.push('缺少「开始」节点，将默认从第一个节点开始执行')
  if (startCount > 1) issues.push('存在多个「开始」节点，仅第一个生效')
  if (endCount === 0) issues.push('缺少「结束」节点，流程执行到无出边的节点即终止')
  if (endCount > 1) issues.push('存在多个「结束」节点')

  // 孤立节点
  const hasIn = new Set(edges.map((e) => e.target))
  const hasOut = new Set(edges.map((e) => e.source))
  for (const n of nodes) {
    if (!hasIn.has(n.id) && !hasOut.has(n.id) && nodes.length > 1) {
      issues.push(`节点「${labelOf(n.id)}」未与任何节点连接（孤立节点）`)
    }
  }

  // 循环依赖检测（DFS 三色标记）
  const adj: Record<string, string[]> = {}
  for (const e of edges) (adj[e.source] ??= []).push(e.target)
  const color: Record<string, number> = {}
  const inCycle = new Set<string>()
  const dfs = (u: string, path: string[]) => {
    color[u] = 1
    for (const v of adj[u] ?? []) {
      if (color[v] === 1) {
        const startIdx = path.indexOf(v)
        path
          .slice(startIdx)
          .concat(v)
          .forEach((c) => inCycle.add(c))
      } else if (!color[v]) {
        dfs(v, path.concat(v))
      }
    }
    color[u] = 2
  }
  for (const n of nodes) if (!color[n.id]) dfs(n.id, [n.id])
  if (inCycle.size > 0) {
    const names = [...inCycle].map((id) => `「${labelOf(id)}」`).join('、')
    issues.push(`检测到循环依赖：${names}，执行到环时将被截断`)
  }
  return issues
}
