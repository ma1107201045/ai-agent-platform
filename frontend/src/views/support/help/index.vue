<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, Search } from '@element-plus/icons-vue'

const steps = [
  { no: '01', title: '注册并登录', desc: '进入登录页，使用管理员账号登录平台，进入团队与权限可管理成员。' },
  { no: '02', title: '创建第一个智能体', desc: '在「智能体」新建应用，配置模型供应商与角色提示词，点击保存。' },
  { no: '03', title: '接入知识并调试', desc: '在「知识库」上传资料并关联应用，使用对话调试进行效果验证。' },
  { no: '04', title: '发布与上线', desc: '在「发布管理」一键发布，获取 API 或分享链接对外提供服务。' }
]

const modules = [
  {
    title: '智能体',
    desc: '可视化编排应用的角色、模型与工作流',
    points: ['新建与配置智能体应用', '对话调试与效果调优', '发布 / 下架应用', '多智能体团队协作']
  },
  {
    title: '知识库',
    desc: '为应用接入私有知识，回答有据可依',
    points: ['文档上传与分段解析', '关联智能体使用', '记忆管理沉淀长期上下文', '数据存储与素材统一管理']
  },
  {
    title: '提示词库',
    desc: '沉淀高质量提示词模板',
    points: ['常用提示词模板维护', '按场景分类管理', '一键套用到新应用']
  },
  {
    title: '定时任务',
    desc: '让智能体按计划自动执行',
    points: ['设置 cron 触发计划', '绑定目标智能体', '查看每次执行结果']
  },
  {
    title: '发布与 API',
    desc: '将能力开放给外部系统',
    points: ['一键发布到市场或对外渠道', '生成 API 密钥并管理', '渠道与版本管理', '查看 API 文档']
  },
  {
    title: '观测与评测',
    desc: '让运行状态与质量可量化',
    points: ['对话与运行记录全量留存', '用量与费用统计、预算告警', '评测中心与对比实验', '告警规则与事件跟踪']
  },
  {
    title: '模型管理',
    desc: '统一接入各供应商模型能力',
    points: ['供应商与密钥配置', '模型网关路由与限流', '网关状态监控']
  },
  {
    title: '系统管理',
    desc: '平台的组织与治理能力',
    points: ['团队与权限（角色/成员）', '操作日志审计追溯', '工作空间信息管理', '公告发布与通知触达']
  }
]

const keyword = ref('')
const filteredModules = computed(() =>
  keyword.value.trim()
    ? modules.filter((m) => (m.title + m.desc + m.points.join('')).includes(keyword.value.trim()))
    : modules
)

const faqs = [
  {
    q: '如何邀请成员加入团队？',
    a: '进入「系统管理 · 团队与权限」，点击新增成员，填写登录名/昵称/密码并分配角色即可；成员也可在注册后由管理员在列表中找到并启用。'
  },
  {
    q: '模型对话报错（认证失败 / 配额不足）怎么办？',
    a: '请到「模型 · 供应商管理」核对供应商 API Key 是否有效、额度是否充足，并确认网关路由状态正常。测试对话可在「模型广场」中进行。'
  },
  {
    q: '知识库上传后如何让智能体使用？',
    a: '在知识库完成文档解析后，进入智能体编辑页的知识配置，选择对应的知识库并保存；对话调试时可验证引用效果。'
  },
  {
    q: '发布的应用如何被外部调用？',
    a: '在「发布管理」完成发布后，到「API 密钥」创建密钥，通过「API 文档」中的接口规范携带密钥发起请求；也可通过分享链接在免登录页面直接对话。'
  },
  {
    q: '误操作删除了数据能否找回？',
    a: '当前资源删除为彻底删除，请在操作时确认。建议重要配置定期在「API 密钥 / 应用模板」保留副本；我们正在规划回收站能力。'
  },
  {
    q: '如何查看是谁在什么时间做了哪些操作？',
    a: '所有登录与关键写操作会自动记录到「系统管理 · 操作日志」，支持按模块、操作人、结果与时间范围筛选追溯。'
  },
  {
    q: '告警规则什么时候会触发？',
    a: '规则配置完成后可点击「测试触发」验证通知链路；指标自动触发将在接入运行监控流后开启，届时候选指标（错误率、失败数、延迟、成本）会按窗口自动计算。'
  }
]
</script>

<template>
  <div class="page-container help-page">
    <div class="hero">
      <div class="hero-info">
        <div class="hero-badge">帮助中心</div>
        <h2 class="hero-title">从 0 到 1 上手智能体平台</h2>
        <p class="hero-desc">创建你的第一个智能体只需 4 步；遇到问题时可在下方常见问题中快速排查。</p>
      </div>
    </div>

    <!-- 快速上手 -->
    <div class="section-title">快速上手</div>
    <div class="steps">
      <div v-for="(s, i) in steps" :key="s.no" class="step hover-card">
        <div class="step-no">{{ s.no }}</div>
        <div class="step-body">
          <h4>{{ s.title }}</h4>
          <p>{{ s.desc }}</p>
        </div>
        <el-icon v-if="i < steps.length - 1" class="step-arrow"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 模块指引 -->
    <div class="section-head">
      <div class="section-title">功能指引</div>
      <el-input v-model="keyword" placeholder="搜索模块 / 能力点" clearable style="width: 220px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>
    <div class="mod-grid">
      <div v-for="m in filteredModules" :key="m.title" class="mod-card hover-card">
        <h4>{{ m.title }}</h4>
        <p class="mod-desc">{{ m.desc }}</p>
        <ul>
          <li v-for="p in m.points" :key="p">{{ p }}</li>
        </ul>
      </div>
      <el-empty v-if="filteredModules.length === 0" description="未找到相关内容" :image-size="80" />
    </div>

    <!-- FAQ -->
    <div class="section-title">常见问题</div>
    <div class="faq-card hover-card">
      <el-collapse>
        <el-collapse-item v-for="f in faqs" :key="f.q" :title="f.q">
          <p class="faq-answer">{{ f.a }}</p>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<style scoped>
.help-page {
  max-width: 1280px;
  margin: 0 auto;
}
.hero {
  padding: 34px 36px;
  border-radius: var(--radius-lg);
  background: linear-gradient(120deg, var(--brand-1), var(--brand-2) 120%);
  color: #fff;
  margin-bottom: 22px;
}
.hero-badge {
  display: inline-block;
  font-size: 12px;
  letter-spacing: 1px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  margin-bottom: 10px;
}
.hero-title {
  font-size: 26px;
  font-weight: 800;
}
.hero-desc {
  margin-top: 8px;
  font-size: 13.5px;
  opacity: 0.9;
  max-width: 620px;
  line-height: 1.7;
}
.section-title {
  font-size: 16px;
  font-weight: 800;
  margin: 22px 0 12px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 22px 0 12px;
}
.section-head .section-title {
  margin: 0;
}
.steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.step {
  position: relative;
  padding: 18px;
}
.step-no {
  font-size: 30px;
  font-weight: 900;
  opacity: 0.22;
  line-height: 1;
  margin-bottom: 10px;
}
.step h4 {
  font-size: 15px;
  margin-bottom: 6px;
}
.step p {
  font-size: 12.5px;
  color: var(--text-tertiary);
  line-height: 1.7;
}
.step-arrow {
  position: absolute;
  right: -14px;
  top: 50%;
  transform: translateY(-50%);
  color: #cbd5e1;
  font-size: 18px;
  z-index: 2;
}
.mod-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.mod-card {
  padding: 16px 18px;
}
.mod-card h4 {
  font-size: 15px;
  margin-bottom: 4px;
}
.mod-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 10px;
}
.mod-card ul {
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.mod-card li {
  font-size: 12.5px;
  color: var(--text-secondary);
}
.faq-card {
  padding: 6px 16px;
}
.faq-answer {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
  margin: 0;
}
</style>
