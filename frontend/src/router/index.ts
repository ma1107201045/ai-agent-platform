import {createRouter, createWebHistory} from 'vue-router'
import type {RouteMeta, RouteRecordRaw} from 'vue-router'
import {useUserStore} from '@/stores/user'
import {DEFAULT_HOME} from '@/config/menu'

/**
 * 路由规范：
 * 1. 除 /login、/public/:id 外，所有页面都挂在 DefaultLayout 下作为 children
 * 2. 一级路径前缀与侧边栏菜单分组一一对应（见 @/config/menu.ts）：
 *    /workbench 工作台 · /app-agents 应用 · /data 数据 · /tools 工具 · /publish 发布
 *    /ops 观测 · /eval 评测 · /models 模型 · /system 系统管理 · /support 帮助与文档
 * 3. 每个分组内：已上线页面在前，规划中（planned）页面居中，详情页（带动态参数、meta.hidden）在末尾
 */

/** 规划中的模块：统一指向“建设中”占位页 */
function planned(path: string, name: string, meta: RouteMeta): RouteRecordRaw {
    return {
        path,
        name,
        component: () => import('@/views/placeholder/index.vue'),
        meta: {planned: true, ...meta}
    }
}

const routes: RouteRecordRaw[] = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/login/index.vue'),
        meta: {title: '登录'}
    },
    {
        path: '/public/:id',
        name: 'PublicChat',
        component: () => import('@/views/public/chat.vue'),
        meta: {title: '智能体'}
    },
    {
        path: '/',
        component: () => import('@/layouts/DefaultLayout.vue'),
        redirect: DEFAULT_HOME,
        children: [
            /* ---------------- 工作台 /workbench ---------------- */
            {
                path: 'workbench/dashboard',
                name: 'WorkbenchDashboard',
                component: () => import('@/views/dashboard/index.vue'),
                meta: {title: '工作台概览'}
            },
            {
                path: 'workbench/notifications',
                name: 'WorkbenchNotifications',
                component: () => import('@/views/workbench/notifications/index.vue'),
                meta: {title: '通知中心'}
            },

            /* ---------------- 应用 /app-agents ---------------- */
            {
                path: 'app/agents',
                name: 'Apps',
                component: () => import('@/views/app/agents/index.vue'),
                meta: {title: '智能体'}
            },
            {
                path: 'app/prompts',
                name: 'AppPrompts',
                component: () => import('@/views/app/prompts/index.vue'),
                meta: {title: '提示词库'}
            },
            {
                path: 'app/templates',
                name: 'AppTemplates',
                component: () => import('@/views/app/templates/index.vue'),
                meta: {title: '应用模板'}
            },
            {
                path: 'app/marketplace',
                name: 'AppMarketplace',
                component: () => import('@/views/app/marketplace/index.vue'),
                meta: {title: '应用市场'}
            },
            {
                path: 'app/multi-agent',
                name: 'AppMultiAgent',
                component: () => import('@/views/app/multi-agent/index.vue'),
                meta: {title: '多智能体编排'}
            },
            {
                path: 'app/schedules',
                name: 'AppSchedules',
                component: () => import('@/views/app/schedules/index.vue'),
                meta: {title: '定时任务'}
            },
            {
                path: 'app/guardrails',
                name: 'AppGuardrails',
                component: () => import('@/views/app/guardrails/index.vue'),
                meta: {title: '内容安全'}
            },
            {
                path: 'app/agents/:id/edit',
                name: 'AppEdit',
                component: () => import('@/views/app/agents/edit.vue'),
                meta: {title: '智能体编排', hidden: true}
            },
            {
                path: 'app/agents/:id/chat',
                name: 'AppChat',
                component: () => import('@/views/app/agents/chat.vue'),
                meta: {title: '对话调试', hidden: true}
            },

            /* ---------------- 数据 /data ---------------- */
            {
                path: 'data/knowledge',
                name: 'DataKnowledge',
                component: () => import('@/views/knowledge/index.vue'),
                meta: {title: '知识库'}
            },
            {
                path: 'data/memory',
                name: 'DataMemory',
                component: () => import('@/views/memory/index.vue'),
                meta: {title: '记忆管理'}
            },
            {
                path: 'data/storage',
                name: 'DataStorage',
                component: () => import('@/views/storage/index.vue'),
                meta: {title: '数据存储'}
            },
            {
                path: 'data/assets',
                name: 'DataAssets',
                component: () => import('@/views/assets/index.vue'),
                meta: {title: '素材管理'}
            },

            /* ---------------- 工具 /tools ---------------- */
            {
                path: 'tool/infos',
                name: 'ToolInfos',
                component: () => import('@/views/tools/info/index.vue'),
                meta: {title: '工具管理'}
            },
            {
                path: 'tool/marketplace',
                name: 'ToolMarketplace',
                component: () => import('@/views/tools/marketplace/index.vue'),
                meta: {title: '插件市场'}
            },
            {
                path: 'tool/integrations',
                name: 'ToolIntegrations',
                component: () => import('@/views/tools/integrations/index.vue'),
                meta: {title: '数据集成'}
            },

            /* ---------------- 发布 /publish ---------------- */
            {
                path: 'publish',
                name: 'Publish',
                component: () => import('@/views/publish/index.vue'),
                meta: {title: '发布管理'}
            },
            {
                path: 'publish/api-keys',
                name: 'PublishApiKeys',
                component: () => import('@/views/publish/api-keys/index.vue'),
                meta: {title: 'API 密钥'}
            },
            {
                path: 'publish/versions',
                name: 'PublishVersions',
                component: () => import('@/views/publish/versions/index.vue'),
                meta: {title: '版本历史'}
            },
            {
                path: 'publish/channels',
                name: 'PublishChannels',
                component: () => import('@/views/publish/channels/index.vue'),
                meta: {title: '渠道管理'}
            },
            {
                path: 'publish/docs',
                name: 'PublishDocs',
                component: () => import('@/views/publish/docs/index.vue'),
                meta: {title: 'API 文档'}
            },

            /* ---------------- 观测 /ops ---------------- */
            {
                path: 'ops',
                name: 'Ops',
                component: () => import('@/views/ops/index.vue'),
                meta: {title: '运行监控'}
            },
            {
                path: 'ops/conversations',
                name: 'OpsConversations',
                component: () => import('@/views/chat/index.vue'),
                meta: {title: '对话记录'}
            },
            {
                path: 'ops/runs',
                name: 'OpsRuns',
                component: () => import('@/views/ops/runs/index.vue'),
                meta: {title: '运行记录'}
            },
            {
                path: 'ops/usage',
                name: 'OpsUsage',
                component: () => import('@/views/ops/usage/index.vue'),
                meta: {title: '用量统计'}
            },
            {
                path: 'ops/conversations/label',
                name: 'OpsConversationLabel',
                component: () => import('@/views/conversations/label/index.vue'),
                meta: {title: '对话标注'}
            },
            {
                path: 'ops/billing',
                name: 'OpsBilling',
                component: () => import('@/views/ops/billing/index.vue'),
                meta: {title: '费用账单'}
            },
            planned('ops/alerts', 'OpsAlerts', {
                title: '告警管理',
                phase: 'P2',
                desc: '监控错误率与用量指标，超过阈值自动通知',
                features: [
                    {name: '告警规则', detail: '配置错误率、用量、延迟等阈值规则'},
                    {name: '通知渠道', detail: '通过邮件、Webhook 推送告警消息'}
                ],
                dependency: '需后端新增指标监控与通知投递能力'
            }),

            /* ---------------- 评测 /eval ---------------- */
            {
                path: 'eval',
                name: 'Eval',
                component: () => import('@/views/eval/index.vue'),
                meta: {title: '评测中心'}
            },
            {
                path: 'eval/datasets',
                name: 'EvalDatasets',
                component: () => import('@/views/eval/datasets/index.vue'),
                meta: {title: '评测数据集'}
            },
            {
                path: 'eval/experiments',
                name: 'EvalExperiments',
                component: () => import('@/views/eval/experiments/index.vue'),
                meta: {title: '对比实验'}
            },

            /* ---------------- 模型 /models ---------------- */
            {
                path: 'models',
                name: 'Models',
                component: () => import('@/views/models/index.vue'),
                meta: {title: '供应商管理'}
            },
            planned('models/playground', 'ModelPlayground', {
                title: '模型广场',
                phase: 'P2',
                desc: '浏览各供应商模型能力，在线试运行后再接入',
                features: [
                    {name: '模型浏览', detail: '按能力/价格/上下文分类浏览模型'},
                    {name: '在线试玩', detail: '免配置快速体验模型效果'},
                    {name: '一键接入', detail: '从广场直接创建模型配置'}
                ],
                dependency: '需后端维护模型目录与试用代理'
            }),
            planned('models/finetune', 'ModelFinetune', {
                title: '模型微调',
                phase: 'P2',
                desc: '基于自有数据对模型进行微调训练，打造专属模型',
                features: [
                    {name: '训练任务', detail: '创建微调任务并跟踪训练状态'},
                    {name: '数据校验', detail: '训练数据格式校验与统计'},
                    {name: '效果评估', detail: '微调前后效果对比与发布'}
                ],
                dependency: '需对接支持微调的供应商接口'
            }),
            {
                path: 'models/gateway',
                name: 'ModelGateway',
                component: () => import('@/views/models/gateway/index.vue'),
                meta: {title: '模型网关'}
            },

            /* ---------------- 系统管理 /system ---------------- */
            {
                path: 'system/users',
                name: 'SystemUsers',
                component: () => import('@/views/users/index.vue'),
                meta: {title: '团队与权限'}
            },
            planned('system/users/audit', 'SystemUserAudit', {
                title: '操作日志',
                phase: 'P2',
                desc: '记录关键操作行为，支持审计追溯与安全合规',
                features: [
                    {name: '操作审计', detail: '记录登录、应用变更、密钥操作等关键行为'},
                    {name: '日志检索', detail: '按用户、时间、操作类型检索追溯'}
                ],
                dependency: '需后端新增审计日志记录与查询接口'
            }),
            planned('system/workspace', 'SystemWorkspace', {
                title: '工作空间',
                phase: 'P2',
                desc: '多工作空间隔离与管理，成员按空间协作',
                features: [
                    {name: '空间管理', detail: '创建、切换与归档工作空间'},
                    {name: '成员协作', detail: '按空间分配成员与角色'}
                ],
                dependency: '后端已有 SysTenant 实体，需补充空间级管理接口'
            }),
            planned('system/trash', 'SystemTrash', {
                title: '回收站',
                phase: 'P2',
                desc: '找回误删的应用、知识库与工具，支持恢复或彻底删除',
                features: [
                    {name: '删除列表', detail: '集中查看已删除资源'},
                    {name: '恢复/清除', detail: '一键恢复或彻底删除'},
                    {name: '自动清理', detail: '过期数据自动清除策略'}
                ],
                dependency: '需后端为删除操作增加软删除与回收站表'
            }),
            {
                path: 'system/announcements',
                name: 'SystemAnnouncements',
                component: () => import('@/views/system/announcements/index.vue'),
                meta: {title: '公告管理'}
            },
            {
                path: 'system/security',
                name: 'SystemSecurity',
                component: () => import('@/views/system/security/index.vue'),
                meta: {title: '账号与安全', hidden: true}
            },

            /* ---------------- 帮助与文档 /support ---------------- */
            planned('support/help', 'SupportHelp', {
                title: '使用指南',
                phase: 'P2',
                desc: '内置使用文档与常见问题，帮助快速上手',
                features: [
                    {name: '使用文档', detail: '分模块的操作指南与最佳实践'},
                    {name: '常见问题', detail: 'FAQ 与常见排障指引'}
                ],
                dependency: '纯前端内容页，无需后端改动'
            }),
            planned('support/changelog', 'SupportChangelog', {
                title: '更新日志',
                phase: 'P2',
                desc: '跟踪平台版本更新与功能演进，及时了解新能力',
                features: [
                    {name: '版本动态', detail: '按版本发布记录功能变更'},
                    {name: '订阅关注', detail: '关注重点功能上线通知'}
                ],
                dependency: '纯前端内容页，无需后端改动'
            })
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

const WHITE_LIST = ['/login']

router.beforeEach((to) => {
    document.title = `${to.meta.title ? to.meta.title + ' - ' : ''}智能体平台`

    const userStore = useUserStore()
    // 已发布应用对外访问无需登录
    const isPublic = to.path.startsWith('/public')
    // 未登录且非白名单 → 登录页
    if (!userStore.token && !WHITE_LIST.includes(to.path) && !isPublic) {
        return {path: '/login', query: {redirect: to.fullPath}}
    }
    // 已登录访问登录页 → 首页
    if (userStore.token && to.path === '/login') {
        return DEFAULT_HOME
    }
    return true
})

export default router
