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
            planned('workbench/notifications', 'WorkbenchNotifications', {
                title: '通知中心',
                phase: 'P2',
                desc: '汇聚系统消息、任务结果与告警通知，一处查看',
                features: [
                    {name: '消息列表', detail: '系统、任务、告警消息统一聚合'},
                    {name: '偏好设置', detail: '通知类型与渠道偏好配置'},
                    {name: '已读管理', detail: '消息已读与批量操作'}
                ],
                dependency: '需后端新增消息中心服务'
            }),

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
            planned('app/templates', 'AppTemplates', {
                title: '应用模板',
                phase: 'P2',
                desc: '从模板一键创建应用，覆盖常见业务场景，降低上手成本',
                features: [
                    {name: '场景模板库', detail: '内置对话助手、翻译、客服等常用场景模板'},
                    {name: '一键创建', detail: '选择模板生成可编排应用，快速起步'}
                ],
                dependency: '需后端维护模板市场数据与模板实例化接口'
            }),
            planned('app/marketplace', 'AppMarketplace', {
                title: '应用市场',
                phase: 'P1',
                desc: '发现、分享并一键安装社区智能体，像应用商店一样获取场景能力',
                features: [
                    {name: '应用发现', detail: '按场景/分类浏览与搜索已发布应用'},
                    {name: '一键安装', detail: '从市场安装应用到当前工作空间'},
                    {name: '应用上架', detail: '将自有应用发布为市场可安装模板'}
                ],
                dependency: '需后端新增应用市场数据与安装接口'
            }),
            planned('app/multi-agent', 'MultiAgent', {
                title: '多智能体编排',
                phase: 'P1',
                desc: '编排多个角色智能体协作分工，胜任复杂业务流程',
                features: [
                    {name: '智能体团队', detail: '定义多个角色智能体及其协作关系'},
                    {name: '任务路由', detail: '将任务分配给最合适的智能体执行'},
                    {name: '协作对话', detail: '可视化查看智能体间传递与决策过程'}
                ],
                dependency: '需扩展 WorkflowEngine 支持多智能体协作'
            }),
            planned('app/schedules', 'AppSchedules', {
                title: '定时任务',
                phase: 'P2',
                desc: '按时间或事件触发智能体/工作流自动执行',
                features: [
                    {name: '定时触发', detail: 'Cron 表达式配置周期执行'},
                    {name: '事件触发', detail: 'Webhook 或业务事件触发运行'},
                    {name: '执行记录', detail: '查看每次触发的运行结果'}
                ],
                dependency: '需后端新增任务调度与触发服务'
            }),
            planned('app/guardrails', 'AppGuardrails', {
                title: '内容安全',
                phase: 'P1',
                desc: '输入输出内容校验与敏感词过滤，保障应用合规安全',
                features: [
                    {name: '敏感词库', detail: '自定义敏感词与拦截策略'},
                    {name: '输入校验', detail: 'Prompt 注入与越权输入检测'},
                    {name: '输出过滤', detail: '生成内容合规过滤与降级回复'}
                ],
                dependency: '需在模型调用链路上增加护栏拦截'
            }),
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
            planned('publish/docs', 'PublishDocs', {
                title: 'API 文档',
                phase: 'P1',
                desc: '查看应用调用的接口说明与代码示例，快速完成接入',
                features: [
                    {name: '接口文档', detail: '在线查看请求/响应定义与鉴权方式'},
                    {name: '代码示例', detail: '多语言 SDK 与调用示例'},
                    {name: '接口调试', detail: '在文档页直接调试调用'}
                ],
                dependency: '需基于应用 API 生成在线文档'
            }),

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
                component: () => import('@/views/conversations/index.vue'),
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
            planned('ops/conversations/label', 'OpsConversationLabel', {
                title: '对话标注',
                phase: 'P1',
                desc: '对对话质量进行标注与反馈回流，持续提升智能体效果',
                features: [
                    {name: '反馈标注', detail: '对消息进行好评/差评、人工标注与补充答案'},
                    {name: '标注数据集', detail: '按应用汇总标注样本，导出为评测/微调数据集'}
                ],
                dependency: '需在 ChatMessage 上补充反馈与标注字段'
            }),
            planned('ops/billing', 'OpsBilling', {
                title: '费用账单',
                phase: 'P1',
                desc: '查看调用消费明细与成本构成，掌控平台使用费用',
                features: [
                    {name: '消费明细', detail: '按应用/模型/时间维度查看费用'},
                    {name: '账单报表', detail: '月度账单与成本趋势导出'},
                    {name: '额度管理', detail: '余额与预算提醒设置'}
                ],
                dependency: '需后端在用量统计基础上核算费用'
            }),
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
            planned('eval', 'Eval', {
                title: '评测中心',
                phase: 'P0',
                desc: '批量运行评测数据集评估应用效果，沉淀可量化的质量报告',
                features: [
                    {name: '批量评测', detail: '批量运行并输出正确率、得分与报告'},
                    {name: '评测报告', detail: '查看历史评测结果与趋势对比'}
                ],
                dependency: '需后端新增评测实体及运行接口'
            }),
            planned('eval/datasets', 'EvalDatasets', {
                title: '评测数据集',
                phase: 'P1',
                desc: '管理用于评测与微调的问答数据集，独立于知识库',
                features: [
                    {name: '数据集维护', detail: '导入/标注问答对，按项目组织'},
                    {name: '数据回流', detail: '从对话标注一键回流为数据集'},
                    {name: '导出应用', detail: '导出为评测或微调所需格式'}
                ],
                dependency: '需后端新增数据集实体与导入导出接口'
            }),
            planned('eval/experiments', 'EvalExperiments', {
                title: '对比实验',
                phase: 'P1',
                desc: '同一测试集对比不同版本、模型与 Prompt 的效果差异',
                features: [
                    {name: '对比运行', detail: '多配置并行评测并横向对比'},
                    {name: '差异分析', detail: '逐用例查看答案差异与评分'},
                    {name: '最佳实践', detail: '沉淀胜出配置为推荐基线'}
                ],
                dependency: '需复用测试集评测能力并扩展对比存储'
            }),

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
            planned('models/gateway', 'ModelGateway', {
                title: '模型网关',
                phase: 'P1',
                desc: '统一模型路由、限流与故障回退，保障服务稳定与成本可控',
                features: [
                    {name: '路由策略', detail: '按模型/供应商配置路由与权重'},
                    {name: '限流配额', detail: '模型级限流与配额控制'},
                    {name: '故障回退', detail: '主供应商异常时自动切换备用'}
                ],
                dependency: '需抽象模型调用层为可路由网关'
            }),

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
            planned('system/announcements', 'SystemAnnouncements', {
                title: '公告管理',
                phase: 'P2',
                desc: '向平台用户发布系统公告与运营通知',
                features: [
                    {name: '公告发布', detail: '创建面向全部或指定用户群的公告'},
                    {name: '有效期管理', detail: '公告上线/下线时间控制'},
                    {name: '送达统计', detail: '查看公告阅读与送达情况'}
                ],
                dependency: '需后端新增公告实体与发布接口'
            }),
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
