<script setup lang="ts">
import { ChatDotRound, Delete, Edit, Promotion } from '@element-plus/icons-vue'
import type { AppAgent } from '@/api/types'
import { formatTime } from '@/utils/format'
import { gradientOf, typeLabels, typeMeta } from '../agentMeta'

/** 应用卡片：封面 + 信息 + hover 浮现操作 */
defineProps<{ row: AppAgent }>()

const emit = defineEmits<{
  edit: [row: AppAgent]
  chat: [row: AppAgent]
  publish: [row: AppAgent]
  remove: [row: AppAgent]
}>()
</script>

<template>
  <div class="app-card hover-card">
    <div class="app-cover" :style="{ background: gradientOf(row) }">
      <div class="cover-deco"></div>
      <span class="cover-icon">{{ typeMeta[row.type]?.icon }}</span>
      <div class="cover-type">{{ typeLabels[row.type] }}</div>
    </div>

    <div class="app-body">
      <div class="app-title-row">
        <span class="app-name">{{ row.name }}</span>
        <el-tag v-if="row.status === 1" size="small" type="success" effect="light">已发布</el-tag>
        <el-tag v-else size="small" type="info" effect="plain">草稿</el-tag>
      </div>
      <p class="app-desc">{{ row.description || '暂无描述' }}</p>
      <div class="app-foot">
        <span class="app-time">{{ formatTime(row.updateTime) }}</span>
      </div>
    </div>

    <div class="app-actions">
      <el-tooltip content="编排工作流" placement="top">
        <div class="action-btn" @click="emit('edit', row)">
          <el-icon :size="16"><Edit /></el-icon>
        </div>
      </el-tooltip>
      <el-tooltip content="开始对话" placement="top">
        <div class="action-btn" @click="emit('chat', row)">
          <el-icon :size="16"><ChatDotRound /></el-icon>
        </div>
      </el-tooltip>
      <el-tooltip content="发布版本" placement="top">
        <div class="action-btn" @click="emit('publish', row)">
          <el-icon :size="16"><Promotion /></el-icon>
        </div>
      </el-tooltip>
      <el-tooltip content="删除" placement="top">
        <div class="action-btn danger" @click="emit('remove', row)">
          <el-icon :size="16"><Delete /></el-icon>
        </div>
      </el-tooltip>
    </div>
  </div>
</template>

<style scoped>
.app-card {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  flex-direction: column;
}
.app-cover {
  position: relative;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.cover-deco {
  position: absolute;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  top: -80px;
  right: -50px;
}
.cover-deco::after {
  content: '';
  position: absolute;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  bottom: -60px;
  left: -30px;
}
.cover-icon {
  font-size: 34px;
  position: relative;
  z-index: 1;
  transition: transform 0.25s ease;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.2));
}
.app-card:hover .cover-icon {
  transform: scale(1.18) rotate(-6deg);
}
.cover-type {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 10px;
  backdrop-filter: blur(4px);
}

.app-body {
  padding: 14px 16px 16px;
  flex: 1;
}
.app-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.app-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.app-desc {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-tertiary);
  min-height: 34px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.app-foot {
  margin-top: 8px;
}
.app-time {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

/* hover 浮现操作 */
.app-actions {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 10px 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: linear-gradient(180deg, transparent, var(--bg-card) 30%);
  opacity: 0;
  transform: translateY(6px);
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}
.app-card:hover .app-actions {
  opacity: 1;
  transform: translateY(0);
}
.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  transition: all 0.18s ease;
}
.action-btn:hover {
  background: var(--brand-gradient);
  color: #fff;
}
.action-btn.danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.action-btn.danger:hover {
  background: #ef4444;
  color: #fff;
}
</style>
