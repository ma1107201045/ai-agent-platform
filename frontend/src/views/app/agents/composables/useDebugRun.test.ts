import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useDebugRun } from './useDebugRun'
import type { FlowGraphEdge, FlowGraphNode } from '@/utils/flow'
import type { RunResult, TraceItem, WorkflowNodeType } from '@/api/types'

const mocks = vi.hoisted(() => ({
  runStream: vi.fn(),
  cancelRun: vi.fn()
}))

vi.mock('@/api/app-agent', () => ({ appAgentApi: mocks }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() }
}))

function makeNode(id: string, nodeType: WorkflowNodeType = 'start'): FlowGraphNode {
  return {
    id,
    type: 'flow-node',
    position: { x: 0, y: 0 },
    data: { label: id, nodeType, config: {} }
  }
}

function makeTrace(nodeId: string, status: TraceItem['status'] = 'success'): TraceItem {
  return { nodeId, nodeType: 'start', label: nodeId, status, costMs: 10 }
}

function makeDeps(nodes: FlowGraphNode[], edges: FlowGraphEdge[] = []) {
  return {
    appId: 1,
    nodes: ref(nodes),
    edges: ref(edges),
    setCenter: vi.fn(),
    saveDraft: vi.fn(async () => {})
  }
}

describe('useDebugRun', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('输入为空时提示且不发起运行', async () => {
    const deps = makeDeps([makeNode('s')])
    const { debugInput, runDebug, running } = useDebugRun(deps)
    debugInput.value = '   '
    await runDebug()
    expect(ElMessage.warning).toHaveBeenCalledWith('请输入测试消息')
    expect(mocks.runStream).not.toHaveBeenCalled()
    expect(running.value).toBe(false)
  })

  it('存在未配置节点时中止并高亮问题节点', async () => {
    // llm 节点缺 modelId → error 级校验失败
    const bad = makeNode('llm1', 'llm')
    const deps = makeDeps([makeNode('s'), bad])
    const { debugInput, runDebug, highlightedNodeId } = useDebugRun(deps)
    debugInput.value = 'hi'
    await runDebug()
    expect(ElMessage.error).toHaveBeenCalled()
    expect(highlightedNodeId.value).toBe('llm1')
    expect(mocks.runStream).not.toHaveBeenCalled()
  })

  it('流式运行成功：实时点亮节点、落色执行路径、收敛结果', async () => {
    const nodes = [makeNode('s'), makeNode('e', 'end')]
    const edges: FlowGraphEdge[] = [{ id: 'e1', source: 's', target: 'e' }]
    const result: RunResult = {
      status: 'success',
      answer: 'ok',
      trace: [makeTrace('s'), makeTrace('e')]
    }
    mocks.runStream.mockImplementation(async (_id, _msgs, handlers) => {
      handlers.onRunStarted?.('run-1')
      handlers.onNodeStarted?.('s')
      handlers.onNodeFinished?.(makeTrace('s'))
      handlers.onDone?.(result)
    })
    const deps = makeDeps(nodes, edges)
    const { debugInput, runDebug, runResult, running, curRunId } = useDebugRun(deps)
    debugInput.value = 'hello'
    await runDebug()

    expect(deps.saveDraft).toHaveBeenCalled()
    expect(mocks.runStream).toHaveBeenCalledWith(
      1,
      [{ role: 'user', content: 'hello' }],
      expect.any(Object)
    )
    expect(runResult.value).toEqual(result)
    expect(nodes[0].data.runStatus).toBe('success')
    expect(nodes[1].data.runStatus).toBe('success')
    // 执行路径上的边被高亮
    expect(edges[0].class).toBe('executed-edge')
    expect(edges[0].style).toEqual({ stroke: '#67c23a', strokeWidth: 2.5 })
    expect(curRunId.value).toBeNull()
    expect(running.value).toBe(false)
  })

  it('运行失败（done.failed）：标记失败并定位错误节点', async () => {
    const nodes = [makeNode('s'), makeNode('e', 'end')]
    const errTrace = makeTrace('e', 'error')
    errTrace.error = 'boom'
    mocks.runStream.mockImplementation(async (_id, _msgs, handlers) => {
      handlers.onDone?.({
        status: 'failed',
        answer: '',
        error: 'boom',
        trace: [makeTrace('s'), errTrace]
      })
    })
    const deps = makeDeps(nodes)
    const { debugInput, runDebug, runStatusMeta, highlightedNodeId } = useDebugRun(deps)
    debugInput.value = 'hi'
    await runDebug()
    expect(runStatusMeta.value.text).toBe('运行失败')
    expect(highlightedNodeId.value).toBe('e')
    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('流错误回调：标记失败状态', async () => {
    mocks.runStream.mockImplementation(async (_id, _msgs, handlers) => {
      handlers.onError?.('网络中断')
    })
    const deps = makeDeps([makeNode('s'), makeNode('e', 'end')])
    const { debugInput, runDebug, runStatusMeta, running } = useDebugRun(deps)
    debugInput.value = 'hi'
    await runDebug()
    expect(ElMessage.error).toHaveBeenCalledWith('网络中断')
    expect(runStatusMeta.value.text).toBe('运行失败')
    expect(running.value).toBe(false)
  })

  it('停止运行：有 runId 时调用取消接口，无 runId 时仅复位状态', async () => {
    mocks.cancelRun.mockResolvedValue(true)
    const deps = makeDeps([makeNode('s')])
    const { stopRun, running, curRunId } = useDebugRun(deps)

    running.value = true
    await stopRun()
    expect(mocks.cancelRun).not.toHaveBeenCalled()
    expect(running.value).toBe(false)

    curRunId.value = 'run-9'
    running.value = true
    await stopRun()
    expect(mocks.cancelRun).toHaveBeenCalledWith('run-9')
    // 优雅取消：等待 done(canceled) 收敛，不立即复位 running
    expect(running.value).toBe(true)
  })

  it('clearRunState 清空画布运行态与调试结果', async () => {
    const nodes = [makeNode('s')]
    nodes[0].data.runStatus = 'success'
    nodes[0].data.runCost = 5
    const edges: FlowGraphEdge[] = [{ id: 'e1', source: 's', target: 'e', class: 'executed-edge' }]
    const deps = makeDeps(nodes, edges)
    const { clearRunState, runResult } = useDebugRun(deps)
    clearRunState()
    expect(runResult.value).toBeNull()
    expect(nodes[0].data.runStatus).toBeUndefined()
    expect(nodes[0].data.runCost).toBeUndefined()
    expect(edges[0].class).toBe('')
  })
})
