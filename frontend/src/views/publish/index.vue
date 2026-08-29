<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CopyDocument, Promotion, Right } from '@element-plus/icons-vue'
import { appApi } from '@/api/app'
import type { App, AppVersion, AppStats } from '@/api/types'

const router = useRouter()
const loading = ref(false)
const list = ref<App[]>([])
const versions = ref<Record<number, AppVersion>>({})
const stats = ref<Record<number, AppStats>>({})

const typeMeta: Record<string, { label: string; icon: string; color: string }> = {
  chatflow: { label: '对话流', icon: '💬', color: '#5b6cff' },
  workflow: { label: '工作流', icon: '⚙️', color: '#0ea5e9' },
  agent: { label: '智能体', icon: '🤖', color: '#8b5cf6' }
}

const coverColors: Record<string, [string, string]> = {
  chatflow: ['#5b6cff', '#8b5cf6'],
  workflow: ['#0ea5e9', '#5b6cff'],
  agent: ['#8b5cf6', '#d946ef']
}

const gradientOf = (row: App) => {
  const c = coverColors[row.type] || coverColors.chatflow
  return `linear-gradient(135deg, ${c[0]} 0%, ${c[1]} 100%)`
}

const publicUrl = (id: number) => `${location.origin}/public/${id}`

async function copyUrl(row: App) {
  try {
    await navigator.clipboard.writeText(publicUrl(row.id))
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

/** 格式化时间：ISO 字符串 → YYYY-MM-DD HH:mm */
function formatTime(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const data = await appApi.page({ page: 1, size: 100 })
    const published = data.records.filter((a) => a.status === 1)
    list.value = published
    if (published.length) {
      const ids = published.map((a) => a.id)
      const [st, verMap] = await Promise.all([
        appApi.batchStats(ids),
        appApi.publishedBatch(ids).catch(() => ({}))
      ])
      stats.value = st
      versions.value = verMap
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container publish-page">
    <div class="publish-head">
      <div>
        <h2 class="head-title">发布与集成</h2>
        <p class="head-desc">将智能体能力嵌入现有业务流：WebApp 链接、线上版本与运行数据</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="router.push('/apps')">
        <el-icon><Right /></el-icon>&nbsp;去应用列表
      </el-button>
    </div>

    <div v-loading="loading" class="pub-grid">
      <div v-for="row in list" :key="row.id" class="pub-card hover-card">
        <div class="pub-cover" :style="{ background: gradientOf(row) }">
          <span class="pub-icon">{{ typeMeta[row.type]?.icon }}</span>
          <span class="pub-type">{{ typeMeta[row.type]?.label }}</span>
        </div>
        <div class="pub-body">
          <div class="pub-title-row">
            <span class="pub-name">{{ row.name }}</span>
            <el-tag v-if="versions[row.id]?.isPublished === 1" size="small" type="success" effect="light">
              v{{ versions[row.id].version }}
            </el-tag>
          </div>
          <p class="pub-desc">{{ row.description || '暂无描述' }}</p>

          <div class="pub-link" title="点击复制对外访问链接" @click="copyUrl(row)">
            <el-icon :size="14"><Promotion /></el-icon>
            <span class="pub-link-text">{{ publicUrl(row.id) }}</span>
            <el-icon :size="14" class="pub-copy"><CopyDocument /></el-icon>
          </div>

          <div class="pub-stats">
            <div class="pub-stat">
              <span class="pub-stat-value">{{ stats[row.id]?.conversationCount ?? 0 }}</span>
              <span class="pub-stat-label">会话</span>
            </div>
            <div class="pub-stat">
              <span class="pub-stat-value">{{ stats[row.id]?.messageCount ?? 0 }}</span>
              <span class="pub-stat-label">消息</span>
            </div>
            <div class="pub-stat">
              <span class="pub-stat-value">{{ formatTime(row.updateTime) }}</span>
              <span class="pub-stat-label">更新时间</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && list.length === 0" class="empty-state">
        <div class="empty-icon">
          <el-icon :size="40"><Promotion /></el-icon>
        </div>
        <p>还没有已发布的应用，先去发布一个吧</p>
        <el-button type="primary" class="btn-gradient" @click="router.push('/apps')">去发布</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.publish-page {
  max-width: 1280px;
  margin: 0 auto;
}
.publish-head {
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

.pub-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  min-height: 200px;
}
.pub-card {
  overflow: hidden;
  cursor: default;
  display: flex;
  flex-direction: column;
}
.pub-cover {
  position: relative;
  height: 76px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pub-icon {
  font-size: 30px;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.2));
}
.pub-type {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 10px;
}
.pub-body {
  padding: 14px 16px 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.pub-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pub-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pub-desc {
  font-size: 12.5px;
  color: var(--text-tertiary);
  min-height: 34px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pub-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  background: var(--bg-page);
  color: var(--brand-1);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.pub-link:hover {
  border-color: var(--brand-1);
  background: var(--brand-gradient-soft);
}
.pub-link-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pub-copy {
  flex-shrink: 0;
}
.pub-stats {
  display: flex;
  gap: 18px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}
.pub-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.pub-stat-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}
.pub-stat:last-child .pub-stat-value {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-tertiary);
}
.pub-stat-label {
  font-size: 11px;
  color: var(--text-tertiary);
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
}
.empty-icon {
  width: 84px;
  height: 84px;
  border-radius: 24px;
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-state p {
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
