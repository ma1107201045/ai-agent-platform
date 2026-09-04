import type { Component } from 'vue'
import {
  Aim,
  Avatar,
  Bell,
  Box,
  ChatDotRound,
  Clock,
  Collection,
  CollectionTag,
  Connection,
  CopyDocument,
  Cpu,
  CreditCard,
  DataAnalysis,
  DataBoard,
  DataLine,
  Delete,
  Document,
  EditPen,
  Files,
  Folder,
  FolderOpened,
  Grid,
  Histogram,
  Key,
  Link,
  List,
  MagicStick,
  Management,
  Notebook,
  Notification,
  OfficeBuilding,
  Operation,
  Odometer,
  Picture,
  Platform,
  Promotion,
  QuestionFilled,
  Reading,
  SemiSelect,
  Setting,
  Share,
  Shop,
  ShoppingCart,
  Timer,
  Tools,
  TrendCharts,
  Umbrella,
  User,
  WarningFilled
} from '@element-plus/icons-vue'

/**
 * 侧边栏菜单配置
 *
 * 约定：
 * 1. group.key 即该分组的路由一级路径前缀，分组内所有页面路径都以 /{key} 开头
 *    （/workbench 工作台 · /app-agents 应用 · /data 数据 · /tools 工具 · /publish 发布
 *      /ops 观测 · /eval 评测 · /models 模型 · /system 系统管理 · /support 帮助与文档）
 * 2. item.path 必须与 router 中已注册的路由 path 完全一致（开发环境启动时自动校验）
 * 3. planned: true 的菜单项对应“建设中”占位页
 * 4. 菜单顺序与 router 中的路由顺序保持一致：已上线页面在前，规划中页面在后
 */
export interface MenuItem {
  /** 完整路由路径 */
  path: string
  title: string
  icon: Component
  planned?: boolean
}

export interface MenuGroup {
  /** 分组标识，同时作为该分组路由的一级路径前缀 */
  key: string
  title: string
  icon: Component
  items: MenuItem[]
}

/** 登录后默认落地页 */
export const DEFAULT_HOME = '/workbench/dashboard'

export const menuGroups: MenuGroup[] = [
  {
    key: 'workbench',
    title: '工作台',
    icon: Odometer,
    items: [
      { path: '/workbench/dashboard', title: '工作台概览', icon: Odometer },
      { path: '/workbench/notifications', title: '通知中心', icon: Bell }
    ]
  },
  {
    key: 'app',
    title: '应用',
    icon: MagicStick,
    items: [
      { path: '/app/agents', title: '智能体', icon: Grid },
      { path: '/app/prompts', title: '提示词库', icon: Collection },
      { path: '/app/templates', title: '应用模板', icon: CopyDocument },
      { path: '/app/marketplace', title: '应用市场', icon: Shop },
      { path: '/app/multi-agent', title: '多智能体编排', icon: Avatar },
      { path: '/app/schedules', title: '定时任务', icon: Timer },
      { path: '/app/guardrails', title: '内容安全', icon: Umbrella }
    ]
  },
  {
    key: 'data',
    title: '数据',
    icon: Files,
    items: [
      { path: '/data/knowledge', title: '知识库', icon: FolderOpened },
      { path: '/data/memory', title: '记忆管理', icon: Notebook },
      { path: '/data/storage', title: '数据存储', icon: Folder },
      { path: '/data/assets', title: '素材管理', icon: Picture }
    ]
  },
  {
    key: 'tool',
    title: '工具',
    icon: Tools,
    items: [
      { path: '/tool/infos', title: '工具管理', icon: Operation },
      { path: '/tool/marketplace', title: '插件市场', icon: ShoppingCart },
      { path: '/tool/integrations', title: '数据集成', icon: Link }
    ]
  },
  {
    key: 'publish',
    title: '发布',
    icon: Promotion,
    items: [
      { path: '/publish', title: '发布管理', icon: Share },
      { path: '/publish/api-keys', title: 'API 密钥', icon: Key },
      { path: '/publish/versions', title: '版本历史', icon: Clock },
      { path: '/publish/channels', title: '渠道管理', icon: Platform },
      { path: '/publish/docs', title: 'API 文档', icon: Reading }
    ]
  },
  {
    key: 'ops',
    title: '观测',
    icon: DataLine,
    items: [
      { path: '/ops', title: '运行监控', icon: TrendCharts },
      { path: '/ops/conversations', title: '对话记录', icon: ChatDotRound },
      { path: '/ops/runs', title: '运行记录', icon: Timer },
      { path: '/ops/usage', title: '用量统计', icon: Histogram },
      { path: '/ops/conversations/label', title: '对话标注', icon: CollectionTag },
      { path: '/ops/billing', title: '费用账单', icon: CreditCard },
      { path: '/ops/alerts', title: '告警管理', icon: WarningFilled },
    ]
  },
  {
    key: 'eval',
    title: '评测',
    icon: Aim,
    items: [
      { path: '/eval', title: '评测中心', icon: DataAnalysis },
      { path: '/eval/datasets', title: '评测数据集', icon: DataBoard },
      { path: '/eval/experiments', title: '对比实验', icon: SemiSelect },
    ]
  },
  {
    key: 'models',
    title: '模型',
    icon: Cpu,
    items: [
      { path: '/models', title: '供应商管理', icon: Management },
      { path: '/models/playground', title: '模型广场', icon: Box },
      { path: '/models/finetune', title: '模型微调', icon: EditPen },
      { path: '/models/gateway', title: '模型网关', icon: Connection }
    ]
  },
  {
    key: 'system',
    title: '系统管理',
    icon: Setting,
    items: [
      { path: '/system/users', title: '团队与权限', icon: User },
      { path: '/system/users/audit', title: '操作日志', icon: Document },
      { path: '/system/workspace', title: '工作空间', icon: OfficeBuilding },
      { path: '/system/trash', title: '回收站', icon: Delete },
      { path: '/system/announcements', title: '公告管理', icon: Notification }
    ]
  },
  {
    key: 'support',
    title: '帮助与文档',
    icon: QuestionFilled,
    items: [
      { path: '/support/help', title: '使用指南', icon: QuestionFilled },
      { path: '/support/changelog', title: '更新日志', icon: List }
    ]
  }
]

/** 扁平化的菜单项，用于激活态匹配与路由一致性校验 */
export const menuItems: MenuItem[] = menuGroups.flatMap((g) => g.items)
