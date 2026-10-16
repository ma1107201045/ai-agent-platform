import { ElMessage } from 'element-plus'
import type { Ref } from 'vue'

export type AlignMode = 'left' | 'hcenter' | 'right' | 'top' | 'vcenter' | 'bottom'
export type DistributeMode = 'horizontal' | 'vertical'

/** 多选手势引导文案（对齐 / 分布未选中足够节点时的提示） */
const SELECT_HINT = '请先选中至少 2 个节点：按住 Shift 拖拽框选，或按住 Ctrl 点击追加选中'

interface LayoutDeps {
  nodes: Ref<any[]>
  edges: Ref<any[]>
  /** vue-flow 内部真实渲染节点（框选后 v-model 回写不可靠，必须从这里取） */
  getNodes: Ref<any[]>
  snapshot: () => void
}

/** 节点对齐 / 均匀分布 / 自动布局 */
export function useNodeLayout(deps: LayoutDeps) {
  /**
   * 读取选中节点包围盒（尺寸未就绪时用默认值）。
   */
  function selectedBoxes() {
    return deps.getNodes.value
      .filter((n) => n.selected)
      .map((n) => ({
        n,
        x: n.position.x,
        y: n.position.y,
        w: n.dimensions?.width || 210,
        h: n.dimensions?.height || 64
      }))
  }

  /**
   * 批量应用节点新位置：
   * 1) 原地修改内部 store 节点对象的 position（与 vue-flow 拖动的更新机制一致），画布立即重排；
   * 2) 再用全新浅拷贝数组替换 v-model 的 nodes，保证受控同步与保存/撤销快照一致；
   * 3) 记录历史并反馈实际移动的节点数。
   */
  function applyNodePositions(
    next: Record<string, Partial<{ x: number; y: number }>>,
    msg: string
  ) {
    const ids = Object.keys(next)
    if (!ids.length) {
      ElMessage.warning('未选中任何节点：请先按住 Shift 拖拽框选，或按住 Ctrl 点击追加选中')
      return
    }
    const current = deps.getNodes.value
    const moved: string[] = []
    current.forEach((n) => {
      const p = next[n.id]
      if (p) {
        if (typeof p.x === 'number') n.position.x = p.x
        if (typeof p.y === 'number') n.position.y = p.y
        moved.push(n.id)
      }
    })
    deps.nodes.value = current.map((n) => ({ ...n }))
    deps.snapshot()
    ElMessage.success(`${msg}（已移动 ${moved.length} 个节点）`)
  }

  /** 对齐选中节点：以包围盒为基准，left/hcenter/right 对齐 X 轴，top/vcenter/bottom 对齐 Y 轴 */
  function alignNodes(mode: AlignMode) {
    const boxes = selectedBoxes()
    if (boxes.length < 2) {
      ElMessage.warning(SELECT_HINT)
      return
    }
    const minX = Math.min(...boxes.map((b) => b.x))
    const maxX = Math.max(...boxes.map((b) => b.x + b.w))
    const minY = Math.min(...boxes.map((b) => b.y))
    const maxY = Math.max(...boxes.map((b) => b.y + b.h))
    const avgCX = boxes.reduce((s, b) => s + b.x + b.w / 2, 0) / boxes.length
    const avgCY = boxes.reduce((s, b) => s + b.y + b.h / 2, 0) / boxes.length
    const next: Record<string, Partial<{ x: number; y: number }>> = {}
    for (const b of boxes) {
      switch (mode) {
        case 'left':
          next[b.n.id] = { x: minX }
          break
        case 'hcenter':
          next[b.n.id] = { x: avgCX - b.w / 2 }
          break
        case 'right':
          next[b.n.id] = { x: maxX - b.w }
          break
        case 'top':
          next[b.n.id] = { y: minY }
          break
        case 'vcenter':
          next[b.n.id] = { y: avgCY - b.h / 2 }
          break
        case 'bottom':
          next[b.n.id] = { y: maxY - b.h }
          break
      }
    }
    applyNodePositions(next, '对齐完成')
  }

  /** 均匀分布选中节点：沿 X 轴（horizontal）或 Y 轴（vertical）保持首尾不动、间隙相等 */
  function distributeNodes(mode: DistributeMode) {
    const boxes = selectedBoxes()
    if (boxes.length < 2) {
      ElMessage.warning(SELECT_HINT)
      return
    }
    const isX = mode === 'horizontal'
    const axis = isX ? 'x' : 'y'
    const size = isX ? 'w' : 'h'
    const sorted = [...boxes].sort((a, b) => a[axis] - b[axis])
    const total = sorted.reduce((s, b) => s + b[size], 0)
    const span = sorted[sorted.length - 1][axis] + sorted[sorted.length - 1][size] - sorted[0][axis]
    const gap = (span - total) / (sorted.length - 1)
    const next: Record<string, Partial<{ x: number; y: number }>> = {}
    let pos = sorted[0][axis]
    for (const b of sorted) {
      next[b.n.id] = isX ? { x: pos } : { y: pos }
      pos += b[size] + gap
    }
    applyNodePositions(next, '分布完成')
  }

  /** 自动布局：从「开始」（无则首个节点）BFS 分层，按层逐列排列、层内纵向错开 */
  function autoLayout() {
    if (deps.nodes.value.length === 0) {
      ElMessage.warning('画布为空')
      return
    }
    const startId =
      deps.nodes.value.find((n) => n.data.nodeType === 'start')?.id ?? deps.nodes.value[0].id
    const depthMap: Record<string, number> = {}
    const queue: string[] = [startId]
    depthMap[startId] = 0
    const visited = new Set([startId])
    const adj: Record<string, string[]> = {}
    for (const e of deps.edges.value) (adj[e.source] ??= []).push(e.target)
    while (queue.length) {
      const id = queue.shift()!
      for (const t of adj[id] ?? []) {
        if (!visited.has(t)) {
          visited.add(t)
          depthMap[t] = depthMap[id] + 1
          queue.push(t)
        }
      }
    }
    // 未可达节点（环外孤立 / 回边）追加到末尾层，保证全部落位
    let maxDepth = Math.max(0, ...Object.values(depthMap))
    for (const n of deps.nodes.value) {
      if (!visited.has(n.id)) {
        visited.add(n.id)
        depthMap[n.id] = ++maxDepth
      }
    }
    const layers: Record<number, string[]> = {}
    for (const [id, d] of Object.entries(depthMap)) (layers[d] ??= []).push(id)
    const W = 210
    const H = 70
    const GAP_X = 120
    const GAP_Y = 40
    const MARGIN = 40
    const next: Record<string, { x: number; y: number }> = {}
    for (const d of Object.keys(layers)
      .map(Number)
      .sort((a, b) => a - b)) {
      const ids = layers[d]
      const x = MARGIN + d * (W + GAP_X)
      ids.forEach((id, i) => {
        next[id] = { x: Math.round(x), y: Math.round(MARGIN + i * (H + GAP_Y)) }
      })
    }
    applyNodePositions(next, '已按流程层级完成自动布局')
  }

  return { alignNodes, distributeNodes, autoLayout }
}
