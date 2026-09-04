import request from './request'
import type { PageResult } from './types'

/** 标注统计 */
export interface LabelStats {
  /** 助手消息总数 */
  totalMessages: number
  /** 已标注数 */
  labeledCount: number
  /** 好评数 */
  goodCount: number
  /** 差评数 */
  badCount: number
  /** 标注覆盖率(%) */
  coverage: number
}

/** 待标注消息（助手消息 + 反馈） */
export interface LabelMessage {
  messageId: number
  conversationId: number
  conversationTitle: string
  appId: number
  appName: string
  content: string
  assistant: boolean
  createTime?: string
  /** 是否已标注 */
  labeled: boolean
  feedbackId?: number
  rating?: 'good' | 'bad'
  labelType?: string
  correctedAnswer?: string
  note?: string
  createdByName?: string
  feedbackTime?: string
}

export interface LabelSaveBody {
  messageId: number
  rating: 'good' | 'bad'
  labelType?: string
  correctedAnswer?: string
  note?: string
}

/** 对话标注 API（/api/chat/feedbacks） */
export const feedbackApi = {
  stats() {
    return request.get<never, LabelStats>('/chat/feedbacks/stats')
  },
  messages(params: {
    page?: number
    size?: number
    appId?: number
    /** 0未标注 1已标注 */
    labeled?: number
    rating?: 'good' | 'bad'
    keyword?: string
  }) {
    return request.get<never, PageResult<LabelMessage>>('/chat/feedbacks/messages', { params })
  },
  save(body: LabelSaveBody) {
    return request.post<never, LabelMessage>('/chat/feedbacks', body)
  },
  remove(feedbackId: number) {
    return request.delete<never, void>(`/chat/feedbacks/${feedbackId}`)
  }
}
