import { beforeEach, describe, expect, it } from 'vitest'
import { clearToken, getToken, setToken } from './token'

describe('utils/token', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('未设置时返回空字符串', () => {
    expect(getToken()).toBe('')
  })

  it('setToken 后可读取', () => {
    setToken('abc123')
    expect(getToken()).toBe('abc123')
  })

  it('clearToken 后返回空字符串', () => {
    setToken('abc123')
    clearToken()
    expect(getToken()).toBe('')
  })
})
