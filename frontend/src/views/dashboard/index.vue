<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Connection, Cpu, Files, MagicStick, Right } from '@element-plus/icons-vue'
import { appApi } from '@/api/app'
import { modelApi } from '@/api/model'
import { knowledgeApi } from '@/api/knowledge'

const router = useRouter()

const loading = ref(true)
const stats = ref([
  { key: 'apps', label: '智能体应用', value: 0, icon: MagicStick, color: '#5b6cff', bg: 'rgba(91,108,255,0.12)' },
  { key: 'datasets', label: '知识库数据集', value: 0, icon: Files, color: '#8b5cf6', bg: 'rgba(139,92,246,0.12)' },
  { key: 'providers', label: '模型供应商', value: 0, icon: Cpu, color: '#0ea5e9', bg: 'rgba(14,165,233,0.12)' },
  { key: 'published', label: '已发布应用', value: 0, icon: Connection, color: '#10b981', bg: 'rgba(16,185,129,0.12)' }
])

const quickActions = [
  { title: '创建智能体应用', desc: '对话流 / 工作流 / 智能体', icon: MagicStick, path: '/apps', color: '#5b6cff' },
  { title: '导入知识库', desc: '文档切块、向量化、RAG 检索', icon: Files, path: '/data/knowledge', color: '#8b5cf6' },
  { title: '接入模型', desc: '配置 DeepSeek 等供应商', icon: Cpu, path: '/models', color: '#0ea5e9' }
]

onMounted(async () => {
  try {
    const [appRes, dsRes, pvRes] = await Promise.all([
      appApi.page({ page: 1, size: 1 }),
      knowledgeApi.datasetPage({ page: 1, size: 1 }),
      modelApi.providerPage({ page: 1, size: 1 })
    ])
    const published = appRes.records?.filter((a) => a.status === 1).length ?? 0
    stats.value = [
      { ...stats.value[0], value: appRes.total },
      { ...stats.value[1], value: dsRes.total },
      { ...stats.value[2], value: pvRes.total },
      { ...stats.value[3], value: published }
    ]
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-glow glow-1"></div>
      <div class="welcome-glow glow-2"></div>
      <div class="welcome-text">
        <h1>你好，欢迎回来 👋</h1>
        <p>开始构建你的下一个 AI 智能体，从灵感走向生产。</p>
      </div>
      <el-button class="btn-gradient welcome-btn" @click="router.push('/apps')">
        创建应用
      </el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div
        v-for="s in stats"
        :key="s.key"
        v-loading="loading"
        class="stat-card hover-card"
      >
        <div class="stat-icon" :style="{ background: s.bg, color: s.color }">
          <el-icon :size="22"><component :is="s.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ loading ? '—' : s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="section">
      <div class="section-head">
        <h3>快捷操作</h3>
      </div>
      <div class="quick-grid">
        <div
          v-for="q in quickActions"
          :key="q.title"
          class="quick-card hover-card"
          @click="router.push(q.path)"
        >
          <div class="quick-icon" :style="{ background: `${q.color}18`, color: q.color }">
            <el-icon :size="22"><component :is="q.icon" /></el-icon>
          </div>
          <div class="quick-body">
            <div class="quick-title">{{ q.title }}</div>
            <div class="quick-desc">{{ q.desc }}</div>
          </div>
          <el-icon class="quick-arrow"><Right /></el-icon>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 24px;
  max-width: 1280px;
  margin: 0 auto;
}

/* ---------- 欢迎横幅 ---------- */
.welcome-banner {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-xl);
  background: linear-gradient(120deg, #5b6cff 0%, #7c4dff 60%, #9b5cff 100%);
  padding: 36px 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
  box-shadow: 0 12px 32px rgba(91, 108, 255, 0.28);
}
.welcome-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(50px);
  opacity: 0.5;
  animation: pulse 6s ease-in-out infinite;
}
.glow-1 {
  width: 260px;
  height: 260px;
  background: #ff8f6b;
  top: -90px;
  right: 8%;
}
.glow-2 {
  width: 200px;
  height: 200px;
  background: #5be3ff;
  bottom: -80px;
  left: 20%;
  animation-delay: 3s;
}
@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.15);
  }
}
.welcome-text {
  position: relative;
  z-index: 1;
}
.welcome-text h1 {
  font-size: 26px;
  font-weight: 700;
}
.welcome-text p {
  margin-top: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}
.welcome-btn {
  position: relative;
  z-index: 1;
  background: #fff !important;
  color: #5b6cff !important;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}

/* ---------- 统计卡片 ---------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 20px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
}
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

/* ---------- 快捷操作 ---------- */
.section {
  margin-top: 28px;
}
.section-head h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 14px;
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.quick-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  cursor: pointer;
}
.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.quick-body {
  flex: 1;
  min-width: 0;
}
.quick-title {
  font-size: 14.5px;
  font-weight: 600;
}
.quick-desc {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--text-tertiary);
}
.quick-arrow {
  color: var(--text-tertiary);
  transition: transform 0.2s ease;
}
.quick-card:hover .quick-arrow {
  transform: translateX(4px);
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
