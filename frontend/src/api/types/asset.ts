export type AssetCategory = 'image' | 'document' | 'audio' | 'video' | 'other'

/** 素材文件（asset_file） */
export interface AssetFile {
  id: number
  tenantId: number
  /** 素材名称（展示用） */
  name: string
  originalName?: string
  ext?: string
  contentType?: string
  /** 文件大小（字节） */
  size: number
  category: AssetCategory
  status: number
  createdBy?: number
  createTime?: string
  updateTime?: string
}
