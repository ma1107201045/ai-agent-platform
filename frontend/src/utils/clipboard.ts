import { ElMessage } from 'element-plus'

/** 复制文本到剪贴板（带全局提示） */
export function copyText(text: string, tip = '已复制到剪贴板'): void {
  if (!text) return
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success(tip))
    .catch(() => ElMessage.warning('复制失败，请手动选择复制'))
}
