<script setup lang="ts">
import { ref } from 'vue'
import { Promotion } from '@element-plus/icons-vue'

interface Version {
  version: string
  date: string
  title: string
  type: 'feature' | 'improve' | 'fix'
  items: string[]
}

const versions = ref<Version[]>([
  {
    version: 'v1.4.0',
    date: '2026-09-04',
    title: '治理与观测增强',
    type: 'feature',
    items: [
      '新增「操作日志」：登录与关键管理操作自动审计，支持按模块 / 操作人 / 时间追溯',
      '新增「工作空间」：集中查看空间信息、成员与应用概况，支持空间设置',
      '新增「告警管理」：阈值规则配置、通知渠道与事件跟踪，支持一键测试触发链路',
      '「使用指南」与「更新日志」页面上线，帮助快速上手',
      '团队与权限增加内容安全校验，提高分享链接与对外接口的安全性'
    ]
  },
  {
    version: 'v1.3.0',
    date: '2026-08-28',
    title: '协同与自动化',
    type: 'feature',
    items: [
      '「通知中心」上线：公告与系统消息统一聚合、未读提醒',
      '「应用模板」：沉淀场景化应用模板，一键从模板创建智能体',
      '「定时任务」：按计划自动执行智能体任务并记录结果',
      '「公告管理」：平台运营公告的发布与下架管理'
    ]
  },
  {
    version: 'v1.2.0',
    date: '2026-08-20',
    title: '多智能体与商业化能力',
    type: 'feature',
    items: [
      '「多智能体编排」：子智能体协作与负责人调度，处理复杂任务',
      '「内容安全」：敏感词拦截与安全审核开关',
      '「费用账单」：月度用量费用统计、预算设置与超支提醒',
      '发布体系扩展：API 密钥、发布渠道、版本历史与 API 文档',
      '「应用市场」：将应用开放到市场供组织内使用'
    ]
  },
  {
    version: 'v1.1.0',
    date: '2026-08-12',
    title: '数据闭环完善',
    type: 'improve',
    items: [
      '新增「记忆管理」「数据存储」「素材管理」，完善数据侧能力',
      '「观测」体验升级：运行记录细化、用量统计图表化',
      '对话标注：支持对会话质量进行人工标注，支撑评测数据沉淀',
      '模型网关上线：统一路由与状态监控'
    ]
  },
  {
    version: 'v1.0.0',
    date: '2026-08-01',
    title: '平台初版发布',
    type: 'feature',
    items: [
      '智能体应用的可视化编排与对话调试',
      '知识库上传解析与 RAG 问答',
      '提示词库管理与多模型供应商接入',
      '评测中心与对比实验、团队与权限管理'
    ]
  }
])

const typeMeta: Record<Version['type'], { label: string; tag: 'primary' | 'success' | 'warning' }> = {
  feature: { label: '新功能', tag: 'primary' },
  improve: { label: '优化', tag: 'success' },
  fix: { label: '修复', tag: 'warning' }
}
</script>

<template>
  <div class="page-container changelog-page">
    <div class="chg-head">
      <div>
        <h2 class="head-title">更新日志</h2>
        <p class="head-desc">跟踪平台版本演进，第一时间了解新能力与改进</p>
      </div>
      <el-tag effect="plain" type="primary" :icon="Promotion">v1.4.0 · 当前版本</el-tag>
    </div>

    <div class="timeline">
      <div v-for="v in versions" :key="v.version" class="tl-item">
        <div class="tl-rail">
          <div class="tl-dot" :class="typeMeta[v.type].tag"></div>
          <div v-if="v !== versions[versions.length - 1]" class="tl-line"></div>
        </div>
        <div class="tl-card hover-card">
          <div class="tl-top">
            <span class="tl-version">{{ v.version }}</span>
            <el-tag size="small" :type="typeMeta[v.type].tag" effect="light">{{ typeMeta[v.type].label }}</el-tag>
            <span class="tl-date">{{ v.date }}</span>
          </div>
          <h4 class="tl-title">{{ v.title }}</h4>
          <ul class="tl-items">
            <li v-for="(item, i) in v.items" :key="i">{{ item }}</li>
          </ul>
        </div>
      </div>
    </div>

    <p class="chg-foot">以上版本说明面向平台内部版本演进整理，更多能力与规划中的功能可参考侧边栏对应模块。</p>
  </div>
</template>

<style scoped>
.changelog-page {
  max-width: 980px;
  margin: 0 auto;
}
.chg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.head-title {
  font-size: 22px;
  font-weight: 700;
}
.head-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-tertiary);
}
.timeline {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tl-item {
  display: flex;
  gap: 18px;
}
.tl-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 12px;
}
.tl-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-top: 26px;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.6);
}
.tl-dot.primary {
  background: var(--brand-1);
}
.tl-dot.success {
  background: #16a34a;
}
.tl-dot.warning {
  background: #f59e0b;
}
.tl-line {
  flex: 1;
  width: 2px;
  background: linear-gradient(180deg, #e2e8f0, #f1f5f9);
  min-height: 24px;
}
.tl-card {
  flex: 1;
  padding: 18px 22px;
  margin-bottom: 16px;
}
.tl-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.tl-version {
  font-size: 15px;
  font-weight: 800;
}
.tl-date {
  margin-left: auto;
  font-size: 12.5px;
  color: var(--text-tertiary);
}
.tl-title {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 700;
}
.tl-items {
  margin-top: 8px;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tl-items li {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.65;
}
.chg-foot {
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 8px;
}
</style>
