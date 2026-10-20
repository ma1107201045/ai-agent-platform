import { ref, shallowRef, type Ref } from 'vue'
import type { FlowGraphEdge, FlowGraphNode } from '@/utils/flow'

interface FlowState {
  nodes: FlowGraphNode[]
  edges: FlowGraphEdge[]
}

/**
 * 画布历史栈（撤销/重做）。
 * 只覆盖画布结构（节点与连线的增删改移），右侧表单配置修改不进历史栈。
 */
export function useFlowHistory(
  nodes: Ref<FlowGraphNode[]>,
  edges: Ref<FlowGraphEdge[]>,
  /** 恢复历史后的收尾（清空选中、重置编辑基线等） */
  onRestored?: () => void
) {
  const MAX_HISTORY = 50
  // shallowRef：历史栈快照是整棵画布树的深拷贝，最多 50 份，
  // 用 ref 会把每份快照都深度响应式化（大画布下拖动/打字卡顿、内存膨胀），快照只需原样存取
  const historyStack = shallowRef<FlowState[]>([])
  const historyIndex = ref(-1)
  let restoring = false

  /** 记录当前画布状态到历史栈 */
  function snapshot() {
    if (restoring) return
    historyStack.value = historyStack.value.slice(0, historyIndex.value + 1)
    historyStack.value.push({
      nodes: JSON.parse(JSON.stringify(nodes.value)),
      edges: JSON.parse(JSON.stringify(edges.value))
    })
    if (historyStack.value.length > MAX_HISTORY) historyStack.value.shift()
    historyIndex.value = historyStack.value.length - 1
  }

  function restore(state: FlowState) {
    restoring = true
    nodes.value = JSON.parse(JSON.stringify(state.nodes))
    edges.value = JSON.parse(JSON.stringify(state.edges))
    restoring = false
    onRestored?.()
  }

  function undo() {
    if (historyIndex.value <= 0) return
    historyIndex.value -= 1
    restore(historyStack.value[historyIndex.value])
  }

  function redo() {
    if (historyIndex.value >= historyStack.value.length - 1) return
    historyIndex.value += 1
    restore(historyStack.value[historyIndex.value])
  }

  return { historyStack, historyIndex, snapshot, undo, redo }
}
