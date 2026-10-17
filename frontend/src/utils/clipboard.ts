import { ElMessage } from 'element-plus'

/**
 * 静默复制文本到剪贴板，返回是否成功。
 * 优先使用 Clipboard API；不可用（非安全上下文等）时回退到 textarea + execCommand。
 */
export async function copyTextSilent(text: string): Promise<boolean> {
  if (!text) return false
  try {
    if (!navigator.clipboard) throw new Error('clipboard api unavailable')
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    try {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      const ok = document.execCommand('copy')
      document.body.removeChild(ta)
      return ok
    } catch {
      return false
    }
  }
}

/** 复制文本到剪贴板（带全局提示） */
export function copyText(
  text: string,
  tip = '已复制到剪贴板',
  failTip = '复制失败，请手动选择复制'
): void {
  copyTextSilent(text).then((ok) => {
    if (ok) ElMessage.success(tip)
    else ElMessage.warning(failTip)
  })
}
