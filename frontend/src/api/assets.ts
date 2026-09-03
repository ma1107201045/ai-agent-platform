import request from './request'
import { getToken } from '@/utils/token'
import type { AssetCategory, AssetFile, PageResult } from './types'

/** 素材管理 API（/api/assets），与后端 AssetController 一一对应 */
export const assetsApi = {
  page(params: { page?: number; size?: number; category?: string; keyword?: string }) {
    return request.get<never, PageResult<AssetFile>>('/assets', { params })
  },
  get(id: number) {
    return request.get<never, AssetFile>(`/assets/${id}`)
  },
  /** 上传素材（file + 可选 name/category） */
  upload(file: File, name?: string, category?: string) {
    const form = new FormData()
    form.append('file', file)
    if (name) form.append('name', name)
    if (category) form.append('category', category)
    return request.post<never, AssetFile>('/assets', form)
  },
  update(id: number, data: { name?: string; category?: string; status?: number }) {
    return request.put<never, AssetFile>(`/assets/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/assets/${id}`)
  },
  /** 携带登录态拉取素材二进制并转 objectURL（供预览 / 下载） */
  async fetchBlob(id: number): Promise<{ url: string; blob: Blob }> {
    const token = getToken()
    const resp = await fetch(`/api/assets/${id}/content`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined
    })
    if (!resp.ok) {
      const err = await resp.text().catch(() => '')
      throw new Error(err || `素材读取失败(${resp.status})`)
    }
    const blob = await resp.blob()
    return { url: URL.createObjectURL(blob), blob }
  }
}

/** 触发浏览器下载素材文件 */
export function downloadAsset(asset: AssetFile, objectUrl: string) {
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = asset.originalName || asset.name
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

/** 素材分类显示名 */
export const assetCategoryText: Record<AssetCategory, string> = {
  image: '图片',
  document: '文档',
  audio: '音频',
  video: '视频',
  other: '其他'
}

/** 素材可访问地址（已登录会话内可直接打开；用于「复制引用地址」） */
export function assetContentUrl(id: number): string {
  return `${location.origin}/api/assets/${id}/content`
}
