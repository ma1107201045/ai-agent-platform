import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { genNodeId } from '@/utils/flow'

interface ClipboardDeps {
  edges: Ref<any[]>
  /** vue-flow 内部真实渲染节点（选中态始终准确） */
  getNodes: Ref<any[]>
  addNodes: (nodes: any[]) => void
  snapshot: () => void
  branchStyleOf: (sourceId: string, handle: string) => Record<string, unknown>
}

/** 画布节点复制 / 粘贴（含节点间内部连线关系） */
export function useFlowClipboard(deps: ClipboardDeps) {
  const clipboard = ref<Array<{ id: string; data: any }> | null>(null)

  /** 复制当前选中的节点（含节点间内部连线关系） */
  function copySelected() {
    const sel = deps.getNodes.value.filter((n) => n.selected)
    if (sel.length === 0) {
      ElMessage.warning('请先选中要复制的节点（支持框选 / Ctrl 多选）')
      return
    }
    clipboard.value = sel.map((n) => ({
      id: n.id,
      data: JSON.parse(JSON.stringify(n.data))
    }))
    ElMessage.success(`已复制 ${sel.length} 个节点，按 Ctrl+V 粘贴`)
  }

  /** 粘贴剪贴板节点到画布（自动重映射节点 id 与内部连线） */
  function pasteClipboard() {
    if (!clipboard.value || clipboard.value.length === 0) {
      ElMessage.warning('剪贴板为空，请先复制节点')
      return
    }
    const idMap: Record<string, string> = {}
    const baseX = 60 + Math.random() * 60
    const baseY = 60 + Math.random() * 60
    const newNodes = clipboard.value.map((src, i) => {
      const id = genNodeId()
      idMap[src.id] = id
      const data = JSON.parse(JSON.stringify(src.data))
      delete data.runStatus
      delete data.runCost
      delete data.runError
      delete data.selected
      return {
        id,
        type: 'flow-node',
        position: { x: baseX + i * 40, y: baseY + i * 40 },
        data
      }
    })
    const idSet = new Set(clipboard.value.map((c) => c.id))
    const newEdges: Array<any> = []
    for (const e of deps.edges.value) {
      if (idSet.has(e.source) && idSet.has(e.target)) {
        newEdges.push({
          id: `edge-${Date.now()}-${newEdges.length}`,
          source: idMap[e.source],
          target: idMap[e.target],
          sourceHandle: e.sourceHandle,
          targetHandle: e.targetHandle,
          label: e.label,
          ...(e.sourceHandle ? deps.branchStyleOf(e.source, e.sourceHandle) : {})
        })
      }
    }
    deps.addNodes(newNodes)
    deps.edges.value = [...deps.edges.value, ...newEdges]
    deps.snapshot()
    ElMessage.success(`已粘贴 ${newNodes.length} 个节点`)
  }

  return { clipboard, copySelected, pasteClipboard }
}
