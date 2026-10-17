import { beforeEach, describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useBranchEdges } from './useBranchEdges'
import type { FlowGraphEdge, FlowGraphNode } from '@/utils/flow'

function condNode(
  branches: Array<{ key: string; label: string; expression: string }>
): FlowGraphNode {
  return {
    id: 'cond1',
    type: 'flow-node',
    position: { x: 0, y: 0 },
    data: { label: '条件', nodeType: 'condition', config: { branches } }
  }
}

function edge(sourceHandle: string | null): FlowGraphEdge {
  return { id: `e-${sourceHandle}`, source: 'cond1', target: 'next', sourceHandle }
}

describe('useBranchEdges', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('branchLabelOf：多分支取分支名，缺 handle 返回 undefined', () => {
    const nodes = ref([condNode([{ key: 'branch1', label: 'VIP', expression: '1' }])])
    const edges = ref<FlowGraphEdge[]>([])
    const { branchLabelOf } = useBranchEdges(nodes, edges)
    expect(branchLabelOf('cond1', 'branch1')).toBe('VIP')
    expect(branchLabelOf('cond1', 'else')).toBe('默认')
    expect(branchLabelOf('cond1', null)).toBeUndefined()
    expect(branchLabelOf('missing', 'branch1')).toBeUndefined()
  })

  it('branchLabelOf：二分支模式返回 是/否', () => {
    const n = condNode([])
    n.data.config = { expression: '1 > 0' }
    const nodes = ref([n])
    const edges = ref<FlowGraphEdge[]>([])
    const { branchLabelOf } = useBranchEdges(nodes, edges)
    expect(branchLabelOf('cond1', 'true')).toBe('是')
    expect(branchLabelOf('cond1', 'false')).toBe('否')
  })

  it('syncBranchEdges：仅补齐 condition 出边的标签，保留已有标签', () => {
    const nodes = ref([condNode([{ key: 'branch1', label: 'VIP', expression: '1' }])])
    const edges = ref<FlowGraphEdge[]>([
      edge('branch1'),
      { ...edge('branch1'), id: 'e2', label: '已有' }
    ])
    const { syncBranchEdges } = useBranchEdges(nodes, edges)
    syncBranchEdges()
    expect(edges.value[0].label).toBe('VIP')
    expect(edges.value[1].label).toBe('已有')
  })

  it('syncBranchEdges：非 condition 节点的出边不处理', () => {
    const n = condNode([])
    n.data.nodeType = 'llm'
    const nodes = ref([n])
    const edges = ref<FlowGraphEdge[]>([edge('branch1')])
    const { syncBranchEdges } = useBranchEdges(nodes, edges)
    syncBranchEdges()
    expect(edges.value[0].label).toBeUndefined()
  })
})
