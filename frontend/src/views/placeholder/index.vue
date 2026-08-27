<script setup lang="ts">
import { useRoute } from 'vue-router'
import { CircleCheck, Tools } from '@element-plus/icons-vue'

const route = useRoute()
const meta = route.meta as {
  title?: string
  desc?: string
  phase?: string
  dependency?: string
  features?: { name: string; detail: string }[]
}

const phaseMap: Record<string, string> = {
  P0: '核心能力 · 推荐优先建设',
  P1: '基础数据已具备 · 前端低成本落地',
  P2: '平台增强项 · 可延后'
}
</script>

<template>
  <div class="page-container placeholder-page">
    <!-- 头部 -->
    <div class="placeholder-head">
      <div class="ph-title-row">
        <h2 class="head-title">{{ meta.title }}</h2>
        <el-tag type="primary" effect="plain" class="ph-tag">功能建设中</el-tag>
      </div>
      <p class="head-desc">{{ meta.desc }}</p>
    </div>

    <div class="hover-card ph-card">
      <div class="ph-status">
        <el-icon :size="44" class="ph-status-icon"><Tools /></el-icon>
        <div>
          <div class="ph-status-title">该模块已纳入菜单规划，正在建设中</div>
          <div class="ph-status-desc">
            菜单结构已就位，功能页面将按优先级逐步交付。以下为规划范围：
          </div>
        </div>
      </div>

      <!-- 规划功能卡片 -->
      <div v-if="meta.features?.length" class="ph-features">
        <div v-for="(f, i) in meta.features" :key="i" class="ph-feature">
          <el-icon :size="16" class="ph-feature-icon"><CircleCheck /></el-icon>
          <div>
            <div class="ph-feature-name">{{ f.name }}</div>
            <div class="ph-feature-detail">{{ f.detail }}</div>
          </div>
        </div>
      </div>

      <!-- 阶段与依赖 -->
      <div class="ph-meta">
        <div class="ph-meta-item">
          <span class="ph-meta-label">优先级</span>
          <el-tag size="small" :type="meta.phase === 'P0' ? 'danger' : meta.phase === 'P1' ? 'warning' : 'info'">
            {{ meta.phase }}
          </el-tag>
          <span class="ph-meta-text">{{ meta.phase ? phaseMap[meta.phase] : '' }}</span>
        </div>
        <div v-if="meta.dependency" class="ph-meta-item">
          <span class="ph-meta-label">依赖</span>
          <span class="ph-meta-text">{{ meta.dependency }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.placeholder-page {
  max-width: 960px;
  margin: 0 auto;
}
.placeholder-head {
  margin-bottom: 16px;
}
.ph-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ph-tag {
  transform: translateY(2px);
}

.ph-card {
  padding: 28px;
}
.ph-status {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px dashed var(--border-color);
}
.ph-status-icon {
  color: var(--brand-1);
  flex-shrink: 0;
}
.ph-status-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.ph-status-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-tertiary);
}

.ph-features {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  padding: 20px 0;
}
.ph-feature {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  background: var(--bg-page);
  border: 1px solid var(--border-color);
}
.ph-feature-icon {
  color: var(--brand-1);
  margin-top: 2px;
  flex-shrink: 0;
}
.ph-feature-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary);
}
.ph-feature-detail {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--text-tertiary);
  line-height: 1.5;
}

.ph-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px dashed var(--border-color);
}
.ph-meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.ph-meta-label {
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.ph-meta-text {
  color: var(--text-secondary);
}
</style>
