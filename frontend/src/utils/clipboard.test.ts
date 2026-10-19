import { afterEach, describe, expect, it, vi } from 'vitest'
import { copyText, copyTextSilent } from './clipboard'
import { ElMessage } from 'element-plus'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() }
}))

function setClipboardApi(impl?: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: impl ? { writeText: vi.fn(impl) } : undefined
  })
}

describe('copyTextSilent', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('空文本直接返回 false', async () => {
    expect(await copyTextSilent('')).toBe(false)
  })

  it('优先走 Clipboard API', async () => {
    setClipboardApi(async () => {})
    expect(await copyTextSilent('hello')).toBe(true)
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('hello')
  })

  it('Clipboard API 不可用时回退 execCommand', async () => {
    setClipboardApi(undefined)
    document.execCommand = vi.fn(() => true)
    expect(await copyTextSilent('fallback')).toBe(true)
    expect(document.execCommand).toHaveBeenCalledWith('copy')
  })

  it('Clipboard API 抛错时回退 execCommand', async () => {
    setClipboardApi(async () => {
      throw new Error('denied')
    })
    document.execCommand = vi.fn(() => true)
    expect(await copyTextSilent('retry')).toBe(true)
  })

  it('两条路都失败时返回 false', async () => {
    setClipboardApi(undefined)
    document.execCommand = vi.fn(() => {
      throw new Error('unsupported')
    })
    expect(await copyTextSilent('x')).toBe(false)
  })
})

describe('copyText', () => {
  it('成功时弹出成功提示', async () => {
    setClipboardApi(async () => {})
    copyText('hello', '复制成功')
    await vi.waitFor(() => {
      expect(ElMessage.success).toHaveBeenCalledWith('复制成功')
    })
  })

  it('失败时弹出警告提示', async () => {
    setClipboardApi(undefined)
    document.execCommand = vi.fn(() => false)
    copyText('hello')
    await vi.waitFor(() => {
      expect(ElMessage.warning).toHaveBeenCalledWith('复制失败，请手动选择复制')
    })
  })
})
