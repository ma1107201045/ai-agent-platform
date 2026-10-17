import type { AppAgent, AppAgentType } from '@/api/types'

/** 应用卡片/创建向导共享元信息 */

export const typeMeta: Record<
  string,
  { label: string; desc: string; icon: string; color: string }
> = {
  chatflow: {
    label: '对话流',
    desc: '多轮对话式智能体，适合客服、助手',
    icon: '💬',
    color: '#5b6cff'
  },
  workflow: {
    label: '工作流',
    desc: '流程化编排，按节点依次执行任务',
    icon: '⚙️',
    color: '#0ea5e9'
  },
  agent: { label: '智能体', desc: '自主规划工具调用，处理复杂任务', icon: '🤖', color: '#8b5cf6' }
}

export const typeLabels: Record<string, string> = {
  chatflow: '对话流',
  workflow: '工作流',
  agent: '智能体'
}

const coverColors: Record<string, [string, string]> = {
  chatflow: ['#5b6cff', '#8b5cf6'],
  workflow: ['#0ea5e9', '#5b6cff'],
  agent: ['#8b5cf6', '#d946ef']
}

export function gradientOf(row: AppAgent): string {
  const c = coverColors[row.type] || coverColors.chatflow
  return `linear-gradient(135deg, ${c[0]} 0%, ${c[1]} 100%)`
}

/** 内置场景模板：创建时预填名称/类型/描述 */
export interface AppTemplate {
  key: string
  name: string
  type: AppAgentType
  icon: string
  color: [string, string]
  desc: string
  useCase: string
}

export const appTemplates: AppTemplate[] = [
  {
    key: 'customer-service',
    name: '智能客服助手',
    type: 'chatflow',
    icon: '💬',
    color: ['#5b6cff', '#8b5cf6'],
    desc: '基于知识库回答客户问题，支持多轮追问与转人工',
    useCase: '电商 / 企业服务'
  },
  {
    key: 'translator',
    name: '多语言翻译助手',
    type: 'chatflow',
    icon: '🌐',
    color: ['#0ea5e9', '#38bdf8'],
    desc: '中英互译与术语润色，支持行业术语定制',
    useCase: '出海业务'
  },
  {
    key: 'content',
    name: '内容创作助手',
    type: 'agent',
    icon: '✍️',
    color: ['#8b5cf6', '#a855f7'],
    desc: '撰写文章 / 文案 / 脚本，自动调用写作工具',
    useCase: '新媒体运营'
  },
  {
    key: 'data-analysis',
    name: '数据分析工作流',
    type: 'workflow',
    icon: '📊',
    color: ['#10b981', '#34d399'],
    desc: '数据接入-清洗-分析-报告一键生成',
    useCase: '经营分析'
  },
  {
    key: 'marketing',
    name: '营销文案助手',
    type: 'chatflow',
    icon: '📣',
    color: ['#f59e0b', '#fbbf24'],
    desc: '生成营销文案与活动创意，可切换文案风格',
    useCase: '市场运营'
  },
  {
    key: 'coding',
    name: '编程助手',
    type: 'agent',
    icon: '👨‍💻',
    color: ['#6366f1', '#818cf8'],
    desc: '代码生成 / 解释 / 重构，调用代码工具链',
    useCase: '研发提效'
  }
]
