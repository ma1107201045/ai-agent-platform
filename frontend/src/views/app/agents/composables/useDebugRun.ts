import { computed, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { appAgentApi } from '@/api/app-agent'
import type { RunResult, TraceItem } from '@/api/types'
import { checkStructure, validateWorkflow } from '@/utils/flowValidation'
import type { FlowGraphEdge, FlowGraphNode } from '@/utils/flow'

interface DebugRunDeps {
  appId: number
  nodes: Ref<FlowGraphNode[]>
  edges: Ref<FlowGraphEdge[]>
  setCenter: (x: number, y: number, options?: { zoom?: number }) => void
  /** 运行前先保存草稿，确保后端运行的是当前画布内容 */
  saveDraft: () => Promise<void>
}

/** 运行调试（SSE 流式）：实时点亮画布 / 轨迹 / 停止 / 高亮定位 */
export function useDebugRun(deps: DebugRunDeps) {
  const { appId, nodes, edges } = deps

  const debugVisible = ref(false)
  const debugInput = ref('')
  const running = ref(false)
  const runResult = ref<RunResult | null>(null)
  const highlightedNodeId = ref<string | null>(null)
  const lastRunFailed = ref(false)
  /** 当前流式运行 runId（供停止接口使用；无流式运行时为 null） */
  const curRunId = ref<string | null>(null)
  /** 流式运行过程中已结束节点的轨迹（结束前实时展示，结束后以 runResult.trace 为准） */
  const streamTrace = ref<TraceItem[]>([])

  const displayTrace = computed(() => runResult.value?.trace ?? streamTrace.value)

  /** 运行终态展示（仅 runResult 就绪后使用） */
  const runStatusMeta = computed(() => {
    const s = runResult.value?.status
    if (s === 'failed' || lastRunFailed.value) return { tag: 'danger' as const, text: '运行失败' }
    if (s === 'canceled') return { tag: 'info' as const, text: '已停止' }
    if (s === 'timeout') return { tag: 'warning' as const, text: '运行超时' }
    return { tag: 'success' as const, text: '运行成功' }
  })

  function toggleDebug() {
    debugVisible.value = !debugVisible.value
  }

  /** 清空画布运行态与调试结果 */
  function clearRunState() {
    runResult.value = null
    highlightedNodeId.value = null
    lastRunFailed.value = false
    curRunId.value = null
    streamTrace.value = []
    // 直接删除运行态属性而非整体替换 data：
    // 整体替换 node.data 会让 vue-flow 的 Handle 组件在更新期访问到失效实例（emitsOptions/subTree 空指针），
    // 且会使右侧配置面板持有的 config 引用失效。
    for (const n of nodes.value) {
      if (!n.data) continue
      delete n.data.runStatus
      delete n.data.runCost
      delete n.data.runError
    }
    for (const e of edges.value) {
      e.class = ''
      e.style = undefined
    }
  }

  /** 将单个 trace 项实时应用到画布节点（节点着色 / 耗时 / 错误气泡） */
  function applyItemToCanvas(item: TraceItem) {
    const n = nodes.value.find((x) => x.id === item.nodeId)
    if (!n?.data) return
    n.data.runStatus = item.status
    n.data.runCost = item.costMs
    n.data.runError = item.error
  }

  /** 将运行 trace 映射到画布节点 / 边 */
  function applyTraceToCanvas(trace: TraceItem[]) {
    const statusMap: Record<string, string> = {}
    const costMap: Record<string, number> = {}
    const errMap: Record<string, string> = {}
    for (const t of trace) {
      statusMap[t.nodeId] = t.status
      costMap[t.nodeId] = t.costMs
      if (t.error) errMap[t.nodeId] = t.error
    }
    for (const n of nodes.value) {
      if (!n.data) continue
      // 保持 node.data 对象引用不变，仅更新运行态属性（避免 vue-flow 更新期崩溃与面板配置引用失效）
      n.data.runStatus = statusMap[n.id] || 'idle'
      n.data.runCost = costMap[n.id]
      n.data.runError = errMap[n.id]
    }
    // 高亮执行路径上的边（trace 相邻节点间的连线）
    const executed = new Set<string>()
    for (let i = 0; i < trace.length - 1; i++) {
      const src = trace[i].nodeId
      const tgt = trace[i + 1].nodeId
      const edge = edges.value.find((e) => e.source === src && e.target === tgt)
      if (edge) executed.add(edge.id)
    }
    for (const e of edges.value) {
      const on = executed.has(e.id)
      e.class = on ? 'executed-edge' : ''
      e.style = on ? { stroke: '#67c23a', strokeWidth: 2.5 } : undefined
    }
  }

  /** 高亮并定位节点 */
  function highlightNode(nodeId: string) {
    const node = nodes.value.find((n) => n.id === nodeId)
    if (!node) return
    highlightedNodeId.value = nodeId
    deps.setCenter(node.position.x, node.position.y, { zoom: 0.9 })
  }

  /**
   * 运行调试（SSE 流式）：节点逐个执行时实时点亮画布 / 追加轨迹，
   * 结束后整体落色并高亮执行路径。运行中可点击「停止」中断（优雅取消）。
   */
  async function runDebug() {
    const text = debugInput.value.trim()
    if (!text) {
      ElMessage.warning('请输入测试消息')
      return
    }
    if (nodes.value.length === 0) {
      ElMessage.warning('画布为空，请先拖入节点编排工作流')
      return
    }
    const errors = validateWorkflow(nodes.value)
    if (errors.length > 0) {
      ElMessage.error(`存在未配置的节点，请先完善：\n${errors.map((e) => e.text).join('\n')}`)
      highlightNode(errors[0].node.id)
      return
    }
    const structure = checkStructure(nodes.value, edges.value)
    if (structure.length > 0) {
      ElMessage.warning(`流程结构提示：\n${structure.join('\n')}`)
    }
    running.value = true
    lastRunFailed.value = false
    runResult.value = null
    streamTrace.value = []
    curRunId.value = null
    try {
      await deps.saveDraft()
      clearRunState()
      await appAgentApi.runStream(appId, [{ role: 'user', content: text }], {
        onRunStarted(runId) {
          curRunId.value = runId
        },
        onNodeStarted(nodeId) {
          const n = nodes.value.find((x) => x.id === nodeId)
          if (n?.data) n.data.runStatus = 'running'
        },
        onNodeFinished(item) {
          applyItemToCanvas(item)
          streamTrace.value.push(item)
        },
        onDone(result) {
          curRunId.value = null
          highlightedNodeId.value = null
          runResult.value = result
          applyTraceToCanvas(result.trace)
          const canceled = result.status === 'canceled'
          if (canceled) {
            ElMessage.info('运行已停止')
          } else if (result.status === 'failed' || result.status === 'timeout') {
            lastRunFailed.value = true
            const errItem = result.trace.find((t) => t.status === 'error')
            if (errItem) {
              highlightNode(errItem.nodeId)
              ElMessage.error(`节点「${errItem.label}」执行失败：${errItem.error || '未知错误'}`)
            } else {
              ElMessage.error(result.error || '运行失败')
            }
          } else {
            ElMessage.success(`运行完成，共执行 ${result.trace.length} 个节点`)
          }
        },
        onError(message) {
          curRunId.value = null
          lastRunFailed.value = true
          ElMessage.error(message || '运行失败')
        }
      })
    } catch (e) {
      ElMessage.error((e as Error)?.message || '运行失败')
    } finally {
      running.value = false
    }
  }

  /** 停止当前流式运行：优雅取消，等待引擎收尾后由 done(canceled) 事件收敛界面 */
  async function stopRun() {
    const rid = curRunId.value
    if (!rid) {
      running.value = false
      return
    }
    try {
      await appAgentApi.cancelRun(rid)
      ElMessage.info('已请求停止，等待当前节点返回…')
    } catch (e) {
      ElMessage.error((e as Error)?.message || '停止失败')
      running.value = false
    }
  }

  return {
    debugVisible,
    debugInput,
    running,
    runResult,
    highlightedNodeId,
    curRunId,
    displayTrace,
    runStatusMeta,
    toggleDebug,
    clearRunState,
    highlightNode,
    runDebug,
    stopRun
  }
}
