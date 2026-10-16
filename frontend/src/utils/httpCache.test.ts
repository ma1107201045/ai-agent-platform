import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buildCacheKey, clearHttpCache, dedup, getCached, invalidate, setCached } from './httpCache'

beforeEach(() => clearHttpCache())

describe('buildCacheKey', () => {
  it('参数顺序不同生成相同键', () => {
    const a = buildCacheKey({ method: 'get', url: '/x', params: { b: 2, a: 1 } })
    const b = buildCacheKey({ method: 'GET', url: '/x', params: { a: 1, b: 2 } })
    expect(a).toBe(b)
  })

  it('不同参数生成不同键', () => {
    const a = buildCacheKey({ url: '/x', params: { a: 1 } })
    const b = buildCacheKey({ url: '/x', params: { a: 2 } })
    expect(a).not.toBe(b)
  })
})

describe('TTL 缓存', () => {
  it('未过期可命中，过期后失效', () => {
    vi.useFakeTimers()
    try {
      setCached('k', { v: 1 }, 1000)
      expect(getCached('k')).toEqual({ v: 1 })
      vi.advanceTimersByTime(1001)
      expect(getCached('k')).toBeUndefined()
    } finally {
      vi.useRealTimers()
    }
  })

  it('invalidate 定向清除', () => {
    setCached('k1', 1, 60000)
    setCached('k2', 2, 60000)
    invalidate('k1')
    expect(getCached('k1')).toBeUndefined()
    expect(getCached('k2')).toBe(2)
  })
})

describe('dedup', () => {
  it('并发调用只执行一次', async () => {
    const fn = vi.fn().mockImplementation(() => Promise.resolve('ok'))
    const [r1, r2] = await Promise.all([dedup('k', fn), dedup('k', fn)])
    expect(fn).toHaveBeenCalledTimes(1)
    expect(r1).toBe('ok')
    expect(r2).toBe('ok')
  })

  it('完成后释放键，可再次执行', async () => {
    const fn = vi.fn().mockResolvedValue('ok')
    await dedup('k', fn)
    await dedup('k', fn)
    expect(fn).toHaveBeenCalledTimes(2)
  })

  it('失败同样共享并释放键', async () => {
    const fn = vi.fn().mockRejectedValue(new Error('boom'))
    await expect(Promise.all([dedup('k', fn), dedup('k', fn)])).rejects.toThrow('boom')
    expect(fn).toHaveBeenCalledTimes(1)
    await expect(dedup('k', fn)).rejects.toThrow('boom')
    expect(fn).toHaveBeenCalledTimes(2)
  })
})
