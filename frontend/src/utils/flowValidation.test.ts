import { describe, expect, it } from 'vitest'
import { checkStructure, hasNodeError, nodeWarnings, validateWorkflow } from './flowValidation'

describe('flowValidation - nodeWarnings', () => {
  it('llm 节点缺模型为 error', () => {
    const w = nodeWarnings({ nodeType: 'llm', config: {} })
    expect(w.some((x) => x.severity === 'error' && x.text === '未配置模型')).toBe(true)
    expect(hasNodeError({ nodeType: 'llm', config: {} })).toBe(true)
  })

  it('llm 有模型但提示词为空为 warn', () => {
    const w = nodeWarnings({ nodeType: 'llm', config: { modelId: 1, userPrompt: ' ' } })
    expect(w).toHaveLength(1)
    expect(w[0].severity).toBe('warn')
    expect(hasNodeError({ nodeType: 'llm', config: { modelId: 1, userPrompt: 'x' } })).toBe(false)
  })

  it('http 缺 url 为 error', () => {
    expect(hasNodeError({ nodeType: 'http', config: { url: '' } })).toBe(true)
  })

  it('condition 条件为空为 warn', () => {
    const w = nodeWarnings({ nodeType: 'condition', config: { expression: '' } })
    expect(w[0].severity).toBe('warn')
  })

  it('start/end 无告警', () => {
    expect(nodeWarnings({ nodeType: 'start', config: {} })).toEqual([])
    expect(nodeWarnings({ nodeType: 'end', config: {} })).toEqual([])
  })
})

describe('flowValidation - validateWorkflow', () => {
  it('汇总 error 级缺失节点', () => {
    const nodes = [
      { id: 'a', data: { nodeType: 'llm', label: '问答', config: {} } },
      { id: 'b', data: { nodeType: 'end', label: '结束', config: {} } }
    ]
    const errors = validateWorkflow(nodes)
    expect(errors).toHaveLength(1)
    expect(errors[0].node.id).toBe('a')
    expect(errors[0].text).toContain('问答')
  })
})

describe('flowValidation - checkStructure', () => {
  it('检测 start/end 缺失与孤立节点', () => {
    const nodes = [
      { id: 'a', data: { nodeType: 'llm', label: 'A' } },
      { id: 'b', data: { nodeType: 'llm', label: 'B' } }
    ]
    const issues = checkStructure(nodes, [])
    expect(issues.some((s) => s.includes('缺少「开始」'))).toBe(true)
    expect(issues.some((s) => s.includes('缺少「结束」'))).toBe(true)
    expect(issues.filter((s) => s.includes('孤立节点'))).toHaveLength(2)
  })

  it('检测循环依赖', () => {
    const nodes = [
      { id: 's', data: { nodeType: 'start', label: '开始' } },
      { id: 'a', data: { nodeType: 'llm', label: 'A' } },
      { id: 'b', data: { nodeType: 'llm', label: 'B' } },
      { id: 'e', data: { nodeType: 'end', label: '结束' } }
    ]
    const edges = [
      { source: 's', target: 'a' },
      { source: 'a', target: 'b' },
      { source: 'b', target: 'a' },
      { source: 'b', target: 'e' }
    ]
    const issues = checkStructure(nodes, edges)
    expect(issues.some((s) => s.includes('循环依赖'))).toBe(true)
  })

  it('健康流程无告警', () => {
    const nodes = [
      { id: 's', data: { nodeType: 'start', label: '开始' } },
      { id: 'e', data: { nodeType: 'end', label: '结束' } }
    ]
    expect(checkStructure(nodes, [{ source: 's', target: 'e' }])).toEqual([])
  })
})
