import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useFlowClipboard } from './useFlowClipboard'
import type { FlowGraphEdge, FlowGraphNode, FlowNodeData } from '@/utils/flow'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() }
}))

function makeNode(id: string, selected = false, data: Partial<FlowNodeData> = {}): FlowGraphNode {
  return {
    id,
    type: 'flow-node',
    position: { x: 0, y: 0 },
    selected,
    data: { label: id, nodeType: 'llm', config: {}, ...data }
  }
}

function makeDeps(nodes: FlowGraphNode[], edges: FlowGraphEdge[]) {
  return {
    edges: ref(edges),
    getNodes: ref(nodes),
    addNodes: vi.fn(),
    snapshot: vi.fn(),
    branchStyleOf: vi.fn(() => ({ style: { stroke: '#999' } }))
  }
}

describe('useFlowClipboard', () => {
  it('未选中节点时复制提示并跳过', () => {
    const deps = makeDeps([makeNode('a')], [])
    const { clipboard, copySelected } = useFlowClipboard(deps)
    copySelected()
    expect(clipboard.value).toBeNull()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('复制选中节点（深拷贝，与原节点隔离）', () => {
    const sel = makeNode('a', true)
    const deps = makeDeps([sel, makeNode('b')], [])
    const { clipboard, copySelected } = useFlowClipboard(deps)
    copySelected()
    expect(clipboard.value).toHaveLength(1)
    expect(clipboard.value?.[0].id).toBe('a')
    sel.data.label = 'changed'
    expect(clipboard.value?.[0].data.label).toBe('a')
  })

  it('粘贴空剪贴板提示并跳过', () => {
    const deps = makeDeps([], [])
    const { pasteClipboard } = useFlowClipboard(deps)
    pasteClipboard()
    expect(deps.addNodes).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('粘贴时重新生成 id、清除运行态、重映射内部连线', () => {
    const a = makeNode('a', true, { runStatus: 'success', runCost: 12 })
    const b = makeNode('b', true)
    const inner: FlowGraphEdge = { id: 'e1', source: 'a', target: 'b', sourceHandle: 'h1' }
    const outer: FlowGraphEdge = { id: 'e2', source: 'a', target: 'outside' }
    const deps = makeDeps([a, b], [inner, outer])
    const { copySelected, pasteClipboard } = useFlowClipboard(deps)

    copySelected()
    pasteClipboard()

    const added = deps.addNodes.mock.calls[0][0] as FlowGraphNode[]
    expect(added).toHaveLength(2)
    // id 重新生成且互不相同
    const ids = added.map((n) => n.id)
    expect(new Set(ids).size).toBe(2)
    expect(ids).not.toContain('a')
    // 运行态被剥离
    expect(added[0].data.runStatus).toBeUndefined()
    expect(added[0].data.runCost).toBeUndefined()
    // 内部连线重映射到新 id，外部连线不复制
    const edges = deps.edges.value
    expect(edges).toHaveLength(3)
    const newEdge = edges[2]
    expect(newEdge.source).toBe(ids[0])
    expect(newEdge.target).toBe(ids[1])
    expect(newEdge.sourceHandle).toBe('h1')
    expect(deps.branchStyleOf).toHaveBeenCalledWith('a', 'h1')
    expect(deps.snapshot).toHaveBeenCalled()
  })
})
