import {
  ChatLineSquare,
  Connection,
  Cpu,
  DataAnalysis,
  EditPen,
  Message,
  Promotion,
  Reading,
  SetUp
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import type { AppAgentType } from '@/api/types'

/** 应用卡片/创建向导共享元信息 */

export const typeMeta: Record<string, { label: string; desc: string; icon: Component }> = {
  chatflow: {
    label: '对话流',
    desc: '多轮对话式智能体，适合客服、助手',
    icon: ChatLineSquare
  },
  workflow: {
    label: '工作流',
    desc: '流程化编排，按节点依次执行任务',
    icon: Connection
  },
  agent: { label: '智能体', desc: '自主规划工具调用，处理复杂任务', icon: Cpu }
}

export const typeLabels: Record<string, string> = Object.fromEntries(
  Object.entries(typeMeta).map(([key, value]) => [key, value.label])
)

/** 内置场景模板：创建时预填名称/类型/描述 */
export interface AppTemplate {
  key: string
  name: string
  type: AppAgentType
  icon: Component
  desc: string
  useCase: string
}

export const appTemplates: AppTemplate[] = [
  {
    key: 'customer-service',
    name: '智能客服助手',
    type: 'chatflow',
    icon: Message,
    desc: '基于知识库回答客户问题，支持多轮追问与转人工',
    useCase: '电商 / 企业服务'
  },
  {
    key: 'translator',
    name: '多语言翻译助手',
    type: 'chatflow',
    icon: Reading,
    desc: '中英互译与术语润色，支持行业术语定制',
    useCase: '出海业务'
  },
  {
    key: 'content',
    name: '内容创作助手',
    type: 'agent',
    icon: EditPen,
    desc: '撰写文章 / 文案 / 脚本，自动调用写作工具',
    useCase: '新媒体运营'
  },
  {
    key: 'data-analysis',
    name: '数据分析工作流',
    type: 'workflow',
    icon: DataAnalysis,
    desc: '数据接入-清洗-分析-报告一键生成',
    useCase: '经营分析'
  },
  {
    key: 'marketing',
    name: '营销文案助手',
    type: 'chatflow',
    icon: Promotion,
    desc: '生成营销文案与活动创意，可切换文案风格',
    useCase: '市场运营'
  },
  {
    key: 'coding',
    name: '编程助手',
    type: 'agent',
    icon: SetUp,
    desc: '代码生成 / 解释 / 重构，调用代码工具链',
    useCase: '研发提效'
  }
]
