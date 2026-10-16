import { watch, type Ref } from 'vue'
import { branchHandlesOf } from '@/utils/flow'
import { useThemeStore } from '@/stores/theme'

/** 条件分支出边：标签 / 配色 / 样式同步 */
export function useBranchEdges(nodes: Ref<any[]>, edges: Ref<any[]>) {
  const themeStore = useThemeStore()

  /** 分支 handle 对应的展示名（多分支取分支名，二分支取 是/否，非分支返回 undefined） */
  function branchLabelOf(sourceId: string, handle?: string | null): string | undefined {
    if (!handle) return undefined
    const node = nodes.value.find((n) => n.id === sourceId)
    if (!node) return undefined
    const hit = branchHandlesOf(node.data).find((h) => h.key === handle)
    return hit ? hit.label : undefined
  }

  /** 分支 handle 对应的主题色 */
  function branchColorOf(sourceId: string, handle: string): string {
    const node = nodes.value.find((n) => n.id === sourceId)
    const hit = node ? branchHandlesOf(node.data).find((h) => h.key === handle) : null
    return hit?.color ?? '#909399'
  }

  /** 分支出边样式（按分支取色），Vue Flow 内置 label 渲染 */
  function branchStyleOf(sourceId: string, handle: string) {
    const color = branchColorOf(sourceId, handle)
    return {
      labelStyle: {
        color,
        fontWeight: '600',
        fontSize: '11px',
        background: themeStore.isDark ? '#1a1d2c' : '#ffffff',
        border: `1px solid ${color}`,
        borderRadius: '8px',
        padding: '1px 6px'
      },
      labelShowBg: false,
      labelBgStyle: { fill: 'transparent' }
    }
  }

  /** 为分支边补齐标签与样式（历史数据 / 回滚 / 分支改名后调用） */
  function syncBranchEdges() {
    for (const e of edges.value) {
      if (!e.sourceHandle) continue
      const src = nodes.value.find((n) => n.id === e.source)
      // 仅处理排他分支节点（condition）的出边
      if (src?.data?.nodeType !== 'condition') continue
      e.label = e.label || branchLabelOf(e.source, e.sourceHandle)
      Object.assign(e, branchStyleOf(e.source, e.sourceHandle))
    }
  }

  // 主题切换后刷新分支标签的底色
  watch(
    () => themeStore.isDark,
    () => syncBranchEdges()
  )

  return { branchLabelOf, branchColorOf, branchStyleOf, syncBranchEdges }
}
