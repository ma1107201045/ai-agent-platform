import { describe, expect, it } from 'vitest'
import { formatTime } from './format'

describe('formatTime', () => {
  it('空值返回占位符', () => {
    expect(formatTime()).toBe('-')
    expect(formatTime('')).toBe('-')
  })

  it('默认格式到分钟', () => {
    expect(formatTime('2026-10-16T15:04:05')).toBe('2026-10-16 15:04')
  })

  it('withSeconds 时格式到秒', () => {
    expect(formatTime('2026-10-16T15:04:05', true)).toBe('2026-10-16 15:04:05')
  })

  it('月日时分秒不足两位补零', () => {
    expect(formatTime('2026-01-02T03:04:05', true)).toBe('2026-01-02 03:04:05')
  })

  it('非法时间原样返回', () => {
    expect(formatTime('not-a-date')).toBe('not-a-date')
  })
})
