import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useFlowHistory } from './useFlowHistory'
import type { FlowGraphEdge, FlowGraphNode } from '@/utils/flow'

function makeNode(id: string): FlowGraphNode {
  return {
    id,
    type: 'flow-node',
    position: { x: 0, y: 0 },
    data: { label: id, nodeType: 'llm', config: {} }
  }
}

describe('useFlowHistory', () => {
  it('快照后可撤销、重做', () => {
    const nodes = ref<FlowGraphNode[]>([])
    const edges = ref<FlowGraphEdge[]>([])
    const { snapshot, undo, redo } = useFlowHistory(nodes, edges)

    snapshot() // 空画布基线
    nodes.value = [makeNode('a')]
    snapshot()
    nodes.value = [makeNode('a'), makeNode('b')]
    snapshot()

    undo()
    expect(nodes.value.map((n) => n.id)).toEqual(['a'])
    undo()
    expect(nodes.value).toEqual([])
    redo()
    expect(nodes.value.map((n) => n.id)).toEqual(['a'])
    redo()
    expect(nodes.value.map((n) => n.id)).toEqual(['a', 'b'])
  })

  it('边界：无法继续撤销/重做时状态不变', () => {
    const nodes = ref<FlowGraphNode[]>([makeNode('x')])
    const edges = ref<FlowGraphEdge[]>([])
    const { snapshot, undo, redo } = useFlowHistory(nodes, edges)
    snapshot()
    undo() // 已是最早
    expect(nodes.value.map((n) => n.id)).toEqual(['x'])
    redo() // 已是最新
    expect(nodes.value.map((n) => n.id)).toEqual(['x'])
  })

  it('撤销后再快照会截断重做分支', () => {
    const nodes = ref<FlowGraphNode[]>([])
    const edges = ref<FlowGraphEdge[]>([])
    const { snapshot, undo, redo, historyStack } = useFlowHistory(nodes, edges)
    snapshot()
    nodes.value = [makeNode('a')]
    snapshot()
    undo()
    nodes.value = [makeNode('c')]
    snapshot()
    redo() // 重做分支已被截断
    expect(nodes.value.map((n) => n.id)).toEqual(['c'])
    expect(historyStack.value).toHaveLength(2)
  })

  it('快照是深拷贝：后续修改不影响历史', () => {
    const nodes = ref<FlowGraphNode[]>([makeNode('a')])
    const edges = ref<FlowGraphEdge[]>([])
    const { snapshot, undo } = useFlowHistory(nodes, edges)
    snapshot()
    nodes.value[0].data.label = 'changed'
    nodes.value = [makeNode('b')]
    snapshot()
    undo()
    expect(nodes.value[0].data.label).toBe('a')
  })

  it('超过 50 条历史时淘汰最早的', () => {
    const nodes = ref<FlowGraphNode[]>([])
    const edges = ref<FlowGraphEdge[]>([])
    const { snapshot, historyStack } = useFlowHistory(nodes, edges)
    for (let i = 0; i < 60; i++) {
      nodes.value = [makeNode(`n${i}`)]
      snapshot()
    }
    expect(historyStack.value.length).toBe(50)
  })
})
