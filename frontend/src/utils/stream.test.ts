import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { chatDeltaStream, streamEvents } from './stream'

function sseResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder()
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const c of chunks) controller.enqueue(encoder.encode(c))
      controller.close()
    }
  })
  return new Response(stream, { status })
}

describe('utils/stream', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('streamEvents 逐帧回调原始 data', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(sseResponse(['data: {"a":1}\n\n', 'data: {"a":2}\n\n']))
    )
    const raws: string[] = []
    await streamEvents('/api/x', { body: { q: 1 } }, (raw) => {
      raws.push(raw)
    })
    expect(raws).toEqual(['{"a":1}', '{"a":2}'])
    // 注入了登录态注入点（无 token 时也不应报错）
    expect(fetch).toHaveBeenCalledOnce()
  })

  it('onEvent 返回 false 时提前中断', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(sseResponse(['data: 1\n\n', 'data: 2\n\n', 'data: 3\n\n']))
    )
    const raws: string[] = []
    await streamEvents('/api/x', {}, (raw) => {
      raws.push(raw)
      return false
    })
    expect(raws).toEqual(['1'])
  })

  it('chatDeltaStream 累加 delta 并在 [DONE] 结束', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          sseResponse([
            'data: {"delta":"你"}\n\n',
            'data: {"delta":"好"}\n\n',
            'data: [DONE]\n\n',
            'data: {"delta":"不应收到"}\n\n'
          ])
        )
    )
    const chunks: string[] = []
    const full = await chatDeltaStream<{ delta?: string }>('/api/x', {}, (c) =>
      chunks.push(c.delta ?? '')
    )
    expect(full).toBe('你好')
    expect(chunks).toEqual(['你', '好'])
  })

  it('HTTP 非 2xx 抛出带响应体的错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('服务器开小差', { status: 500 })))
    await expect(streamEvents('/api/x', {}, () => {})).rejects.toThrow('服务器开小差')
  })
})
