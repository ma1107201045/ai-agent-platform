import request from './request'
import type { PageResult } from './types'

/** 发布渠道 */
export interface PublishChannel {
  id: number
  appId: number
  name: string
  /** wechat_mp / feishu / dingtalk / web / webhook */
  channelType: string
  configJson?: string
  description?: string
  enabled: number
  msgCount: number
  lastMsgAt?: string
  createTime?: string
  updateTime?: string
}

/** 渠道消息 */
export interface PublishChannelMsg {
  id: number
  channelId: number
  appId: number
  direction: 'inbound' | 'outbound'
  eventType?: string
  fromUser?: string
  content?: string
  reply?: string
  status: 'success' | 'failed' | 'skipped'
  errorMsg?: string
  createTime?: string
}

/** 渠道统计 */
export interface ChannelStats {
  total: number
  today: number
  failed: number
  trend: { date: string; count: number }[]
}

export const CHANNEL_TYPE_OPTIONS: { value: string; label: string; hint: string }[] = [
  { value: 'webhook', label: '通用 Webhook', hint: '无需额外配置，任意平台将消息 POST 到回调地址即可。' },
  { value: 'wechat_mp', label: '微信公众号', hint: '需在微信公众平台配置服务器地址与 Token。' },
  { value: 'feishu', label: '飞书机器人', hint: '需飞书开放平台的应用凭证 App ID / App Secret。' },
  { value: 'dingtalk', label: '钉钉机器人', hint: '需钉钉开放平台的 AppKey / AppSecret。' },
  { value: 'web', label: '网页 / H5 接入', hint: '网页中嵌入对话，配置鉴权 Token 后即可调用。' }
]

export function channelTypeLabel(type?: string) {
  return CHANNEL_TYPE_OPTIONS.find((o) => o.value === type)?.label || type || '未知'
}

/** 渠道管理 API（与后端 PublishChannelController /api/publish/channels 对应） */
export const publishChannelApi = {
  page(params: { page?: number; size?: number; appId?: number; channelType?: string; keyword?: string }) {
    return request.get<never, PageResult<PublishChannel>>('/publish/channels', { params })
  },
  get(id: number) {
    return request.get<never, PublishChannel>(`/publish/channels/${id}`)
  },
  create(data: Partial<PublishChannel>) {
    return request.post<never, PublishChannel>('/publish/channels', data)
  },
  update(id: number, data: Partial<PublishChannel>) {
    return request.put<never, void>(`/publish/channels/${id}`, data)
  },
  remove(id: number) {
    return request.delete<never, void>(`/publish/channels/${id}`)
  },
  setEnabled(id: number, enabled: 0 | 1) {
    return request.put<never, void>(`/publish/channels/${id}/enabled`, null, { params: { enabled } })
  },
  /** 仅校验配置必填键 */
  validate(data: { channelType: string; configJson?: string }) {
    return request.post<never, { valid: boolean; missing: string[] }>('/publish/channels/config/validate', data)
  },
  /** 模拟三方回调（body 消息） */
  callback(id: number, data: { content?: string; fromUser?: string }) {
    return request.post<never, PublishChannelMsg>(`/publish/channels/${id}/callback`, data)
  },
  messages(id: number, params: { page?: number; size?: number; direction?: string; keyword?: string }) {
    return request.get<never, PageResult<PublishChannelMsg>>(`/publish/channels/${id}/messages`, { params })
  },
  stats(id: number) {
    return request.get<never, ChannelStats>(`/publish/channels/${id}/stats`)
  }
}
