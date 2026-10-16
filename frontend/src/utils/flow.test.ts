import { describe, expect, it } from 'vitest'
import {
  branchHandlesOf,
  branchesOf,
  defaultConfig,
  dslToFlow,
  ELSE_BRANCH_KEY,
  flowToDsl,
  initialBranches,
  isMultiBranch,
  nextBranchKey
} from './flow'

describe('utils/flow - defaultConfig', () => {
  it('llm 节点包含默认提示词与温度', () => {
    const cfg = defaultConfig('llm')
    expect(cfg.userPrompt).toBe('{{input}}')
    expect(cfg.temperature).toBe(0.7)
  })

  it('未知类型返回空对象', () => {
    // @ts-expect-error 故意传入非法类型
    expect(defaultConfig('not-exist')).toEqual({})
  })
})

describe('utils/flow - 分支工具', () => {
  it('无 branches 时不是多分支', () => {
    expect(isMultiBranch({ config: {} })).toBe(false)
    expect(branchesOf(null)).toEqual([])
  })

  it('多分支 handle 追加默认分支', () => {
    const data = { config: { branches: initialBranches() } }
    const handles = branchHandlesOf(data)
    expect(handles).toHaveLength(3)
    expect(handles[handles.length - 1]?.key).toBe(ELSE_BRANCH_KEY)
  })

  it('nextBranchKey 避开已占用的 key', () => {
    const data = { config: { branches: initialBranches() } }
    expect(nextBranchKey(data)).toBe('branch3')
  })
})

describe('utils/flow - DSL 转换', () => {
  it('dslToFlow 解析失败返回空画布', () => {
    expect(dslToFlow('not-json')).toEqual({ nodes: [], edges: [] })
    expect(dslToFlow(null)).toEqual({ nodes: [], edges: [] })
  })

  it('DSL <-> Flow 往返保持节点与边', () => {
    const dsl = JSON.stringify({
      nodes: [
        { id: 'n1', type: 'start', label: '开始', position: { x: 0, y: 0 }, config: {} },
        { id: 'n2', type: 'end', label: '结束', position: { x: 200, y: 0 }, config: {} }
      ],
      edges: [{ id: 'e1', source: 'n1', target: 'n2' }]
    })
    const { nodes, edges } = dslToFlow(dsl)
    expect(nodes).toHaveLength(2)
    expect(edges).toHaveLength(1)

    const roundTrip = JSON.parse(flowToDsl(nodes, edges))
    expect(roundTrip.nodes.map((n: { id: string }) => n.id)).toEqual(['n1', 'n2'])
    expect(roundTrip.edges[0].source).toBe('n1')
    expect(roundTrip.edges[0].target).toBe('n2')
  })
})
