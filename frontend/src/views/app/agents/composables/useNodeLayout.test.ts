import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useNodeLayout } from './useNodeLayout'
import type { FlowGraphEdge, FlowGraphNode } from '@/utils/flow'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() }
}))

function makeNode(
  id: string,
  x: number,
  y: number,
  opts: { selected?: boolean; nodeType?: string; w?: number; h?: number } = {}
): FlowGraphNode {
  return {
    id,
    type: 'flow-node',
    position: { x, y },
    selected: opts.selected ?? false,
    dimensions: { width: opts.w ?? 210, height: opts.h ?? 64 },
    data: { label: id, nodeType: opts.nodeType ?? 'llm', config: {} }
  } as FlowGraphNode
}

function makeDeps(nodes: FlowGraphNode[], edges: FlowGraphEdge[] = []) {
  return {
    nodes: ref(nodes),
    edges: ref(edges),
    getNodes: ref(nodes),
    snapshot: vi.fn()
  }
}

describe('useNodeLayout', () => {
  it('左对齐：所有选中节点 x 对齐到最小 x', () => {
    const nodes = [
      makeNode('a', 100, 0, { selected: true }),
      makeNode('b', 300, 50, { selected: true })
    ]
    const deps = makeDeps(nodes)
    const { alignNodes } = useNodeLayout(deps)
    alignNodes('left')
    expect(nodes[0].position.x).toBe(100)
    expect(nodes[1].position.x).toBe(100)
    expect(deps.snapshot).toHaveBeenCalled()
  })

  it('选中不足 2 个节点时提示且不移动', () => {
    const nodes = [makeNode('a', 100, 0, { selected: true }), makeNode('b', 300, 50)]
    const deps = makeDeps(nodes)
    const { alignNodes } = useNodeLayout(deps)
    alignNodes('left')
    expect(ElMessage.warning).toHaveBeenCalled()
    expect(nodes[0].position.x).toBe(100)
    expect(deps.snapshot).not.toHaveBeenCalled()
  })

  it('水平均匀分布：首尾不动、间隙相等', () => {
    // 三个 210 宽节点，x 分别为 0 / 100 / 620（首尾跨度 620+210=830，总宽 630，间隙 (830-630)/2=100）
    const nodes = [
      makeNode('a', 0, 0, { selected: true }),
      makeNode('b', 100, 0, { selected: true }),
      makeNode('c', 620, 0, { selected: true })
    ]
    const deps = makeDeps(nodes)
    const { distributeNodes } = useNodeLayout(deps)
    distributeNodes('horizontal')
    expect(nodes[0].position.x).toBe(0)
    expect(nodes[1].position.x).toBe(310)
    expect(nodes[2].position.x).toBe(620)
  })

  it('自动布局：从开始节点 BFS 分层，逐列排开', () => {
    const nodes = [
      makeNode('s', 0, 0, { nodeType: 'start' }),
      makeNode('a', 5, 5),
      makeNode('b', 9, 9)
    ]
    const edges: FlowGraphEdge[] = [
      { id: 'e1', source: 's', target: 'a' },
      { id: 'e2', source: 'a', target: 'b' }
    ]
    const deps = makeDeps(nodes, edges)
    const { autoLayout } = useNodeLayout(deps)
    autoLayout()
    // 层间距 W + GAP_X = 330，起始 MARGIN = 40
    expect(nodes[0].position).toEqual({ x: 40, y: 40 })
    expect(nodes[1].position).toEqual({ x: 370, y: 40 })
    expect(nodes[2].position).toEqual({ x: 700, y: 40 })
  })

  it('自动布局：孤立节点追加到末尾层', () => {
    const nodes = [makeNode('s', 0, 0, { nodeType: 'start' }), makeNode('lonely', 1, 1)]
    const deps = makeDeps(nodes, [])
    const { autoLayout } = useNodeLayout(deps)
    autoLayout()
    // 孤立节点 depth = 0 + 1 = 1 → x = 40 + 330
    expect(nodes[1].position.x).toBe(370)
  })

  it('空画布自动布局提示并跳过', () => {
    const deps = makeDeps([])
    const { autoLayout } = useNodeLayout(deps)
    autoLayout()
    expect(ElMessage.warning).toHaveBeenCalled()
    expect(deps.snapshot).not.toHaveBeenCalled()
  })
})
