import { markRaw, type Component } from 'vue'
import {
  CircleCheck,
  Cpu,
  Document,
  Link as LinkIcon,
  MagicStick,
  Promotion,
  Reading,
  Share
} from '@element-plus/icons-vue'
import type { WorkflowNodeType } from '@/api/types'
import { NODE_TYPE_META } from '@/utils/flow'

/** 节点视觉相关共享工具：图标缓存 / 类型元信息 / 运行态样式 / 节点面板分组 */

const iconMap: Record<string, Component> = {}

export function iconOf(name: string): Component {
  if (!iconMap[name]) {
    const map: Record<string, Component> = {
      Promotion,
      CircleCheck,
      Cpu,
      MagicStick,
      Share,
      Document,
      Reading,
      Link: LinkIcon
    }
    iconMap[name] = markRaw(map[name] || Cpu)
  }
  return iconMap[name]
}

export function metaOf(type: WorkflowNodeType) {
  return NODE_TYPE_META[type]
}

/** MiniMap 节点颜色：按节点类型取主题色 */
export function miniMapNodeColor(node: any): string {
  const type = node?.data?.nodeType as WorkflowNodeType | undefined
  return type && NODE_TYPE_META[type] ? NODE_TYPE_META[type].color : '#d1d5db'
}

/** trace 状态 -> 节点着色 class（含实时 running / canceled） */
export function nodeRunClass(data: any): string {
  const s = data?.runStatus
  if (s === 'success') return 'run-success'
  if (s === 'error') return 'run-error'
  if (s === 'skipped') return 'run-skipped'
  if (s === 'canceled') return 'run-canceled'
  if (s === 'running') return 'run-running'
  return ''
}

/** 左侧节点面板 / 添加节点浮层的分组（按功能归类，不影响拖拽与数据模型） */
export const paletteGroups: Array<{ title: string; types: WorkflowNodeType[] }> = [
  { title: '流程控制', types: ['start', 'end', 'condition'] },
  { title: '处理节点', types: ['llm', 'agent', 'skill', 'template', 'code'] },
  { title: '外部数据', types: ['http', 'knowledge'] }
]

/** 调试轨迹状态 -> el-tag 颜色 */
export const nodeStatusColor: Record<string, 'success' | 'danger' | 'info' | 'warning'> = {
  success: 'success',
  error: 'danger',
  skipped: 'info',
  canceled: 'info'
}
