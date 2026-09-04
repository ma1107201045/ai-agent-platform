<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marketApi } from '@/api/marketplace'
import type { MarketItem } from '@/api/marketplace'

const router = useRouter()

const CATEGORIES: { key: string; label: string; color: string; icon: string }[] = [
  { key: '', label: '全部', color: '#5b7cfa', icon: '🧩' },
  { key: 'customer_service', label: '客服服务', color: '#2ecc8f', icon: '💬' },
  { key: 'translate', label: '翻译助手', color: '#8b5cf6', icon: '🌐' },
  { key: 'writing', label: '写作创作', color: '#f59e0b', icon: '✍️' },
  { key: 'office', label: '办公效率', color: '#0ea5e9', icon: '📊' },
  { key: 'analysis', label: '数据分析', color: '#ef4444', icon: '📈' },
  { key: 'general', label: '通用助手', color: '#10b981', icon: '🤖' },
  { key: 'other', label: '其他', color: '#64748b', icon: '📦' }
]
function catOf(key: string) {
  return CATEGORIES.find((c) => c.key === key) || CATEGORIES[CATEGORIES.length - 1]
}

const APP_TYPES: Record<string, { label: string; color: string }> = {
  chatflow: { label: '对话流', color: '#5b7cfa' },
  workflow: { label: '工作流', color: '#8b5cf6' },
  agent: { label: '自主智能体', color: '#2ecc8f' }
}
function typeOf(t?: string) {
  return (t && APP_TYPES[t]) || APP_TYPES.chatflow
}

const loading = ref(false)
const rows = ref<MarketItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const category = ref('')
const stats = reactive({ total: 0, totalInstall: 0, byCategory: {} as Record<string, number> })

async function load() {
  loading.value = true
  try {
    const data = await marketApi.page({
      page: page.value,
      size: size.value,
      category: category.value || undefined,
      keyword: keyword.value.trim() || undefined
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
async function loadStats() {
  const s = await marketApi.stats()
  stats.total = s.total
  stats.totalInstall = s.totalInstall
  stats.byCategory = s.byCategory
}
function pickCategory(key: string) {
  category.value = key
  page.value = 1
  load()
}
function onSearch() {
  page.value = 1
  load()
}

// ---------------- 安装 ----------------
const installing = ref(false)
const current = ref<MarketItem | null>(null)
async function install(item: MarketItem) {
  ElMessageBox.confirm(
    `将「${item.name}」安装到当前工作空间并自动发布，可立即对话与调试。是否继续？`,
    '一键安装',
    { type: 'info', confirmButtonText: '立即安装' }
  )
    .then(async () => {
      installing.value = true
      current.value = item
      try {
        const app = await marketApi.install(item.id)
        ElMessage.success(`安装成功，已生成应用「${app.name}」`)
        router.push('/app/agents')
      } catch (e) {
        ElMessage.error(e instanceof Error ? e.message : '安装失败')
      } finally {
        installing.value = false
        current.value = null
      }
    })
    .catch(() => {})
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<template>
  <div class="page-container market-page">
    <div class="market-head">
      <div>
        <h2 class="head-title">应用市场</h2>
        <p class="head-desc">发现、分享并一键安装智能体，像应用商店一样快速获取场景能力</p>
      </div>
      <div class="head-stats">
        <div class="stat-chip"><b>{{ stats.total }}</b><span>在架应用</span></div>
        <div class="stat-chip"><b>{{ stats.totalInstall }}</b><span>累计安装</span></div>
      </div>
    </div>

    <div class="market-toolbar">
      <div class="cat-list">
        <button
          v-for="c in CATEGORIES"
          :key="c.key"
          class="cat-item"
          :class="{ active: category === c.key }"
          @click="pickCategory(c.key)"
        >
          <span>{{ c.icon }}</span>
          <span>{{ c.label }}</span>
          <i v-if="stats.byCategory[c.key]" class="cat-count">{{ stats.byCategory[c.key] }}</i>
        </button>
      </div>
      <el-input
        v-model="keyword"
        class="market-search"
        placeholder="搜索应用"
        clearable
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <div v-loading="loading">
      <div v-if="rows.length" class="market-grid">
        <div v-for="item in rows" :key="item.id" class="m-card">
          <div class="m-cover">
            <div class="m-icon" :style="{ background: `linear-gradient(135deg, ${catOf(item.category).color}33, ${catOf(item.category).color}66)` }">
              {{ item.icon || catOf(item.category).icon }}
            </div>
            <span class="m-type" :style="{ color: typeOf(item.type).color, borderColor: typeOf(item.type).color }">
              {{ typeOf(item.type).label }}
            </span>
          </div>
          <div class="m-body">
            <div class="m-name-row">
              <b class="m-name">{{ item.name }}</b>
              <span class="m-cat">{{ catOf(item.category).label }}</span>
            </div>
            <p class="m-desc">{{ item.description }}</p>
            <div class="m-foot">
              <div class="m-meta">
                <span class="m-author">作者 · {{ item.author || '平台官方' }}</span>
                <span class="m-installs">{{ item.installCount ?? 0 }} 次安装</span>
              </div>
              <el-button
                type="primary"
                class="m-install"
                :loading="installing && current?.id === item.id"
                @click="install(item)"
              >
                安装
              </el-button>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else :description="loading ? '加载中…' : '暂无可安装的应用'" />
    </div>

    <el-pagination
      v-if="total > size"
      class="market-pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      v-model:current-page="page"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.market-page {
  max-width: 1280px;
  margin: 0 auto;
}
.market-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
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
.head-stats {
  display: flex;
  gap: 10px;
}
.stat-chip {
  display: flex;
  align-items: baseline;
  gap: 6px;
  background: var(--fill-lighter);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 6px 12px;
}
.stat-chip b {
  font-size: 16px;
  color: var(--brand);
}
.stat-chip span {
  font-size: 12px;
  color: var(--text-tertiary);
}
.market-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.cat-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cat-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--fill-lighter);
  padding: 5px 12px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}
.cat-item:hover {
  border-color: var(--brand);
  color: var(--brand);
}
.cat-item.active {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
  font-weight: 600;
}
.cat-count {
  font-style: normal;
  font-size: 11px;
  background: rgba(0, 0, 0, 0.08);
  border-radius: 999px;
  padding: 0 5px;
}
.cat-item.active .cat-count {
  background: rgba(255, 255, 255, 0.25);
}
.market-search {
  width: 220px;
}
.market-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}
.m-card {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--card-bg);
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.2s, transform 0.2s;
}
.m-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.m-cover {
  position: relative;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--fill-lighter);
  border-bottom: 1px solid var(--border-color);
}
.m-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  box-shadow: var(--shadow-sm);
}
.m-type {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  border: 1px solid;
  border-radius: 999px;
  padding: 1px 8px;
  background: var(--card-bg);
}
.m-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  flex: 1;
}
.m-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.m-name {
  font-size: 15px;
}
.m-cat {
  font-size: 11px;
  background: var(--fill-lighter);
  color: var(--text-tertiary);
  border-radius: 999px;
  padding: 1px 8px;
}
.m-desc {
  margin: 8px 0 12px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-tertiary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}
.m-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.m-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.market-pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
