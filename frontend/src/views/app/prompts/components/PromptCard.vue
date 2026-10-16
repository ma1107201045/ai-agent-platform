<script setup lang="ts">
import {
  Clock,
  Collection,
  Delete,
  Edit,
  Promotion,
  RefreshLeft,
  VideoPlay
} from '@element-plus/icons-vue'
import type { PromptTemplate } from '@/api/types'
import { categoryLabelOf, coverGradientOf } from '../promptMeta'
import { formatTime } from '@/utils/format'

defineProps<{ row: PromptTemplate }>()

const emit = defineEmits<{
  edit: [row: PromptTemplate]
  versions: [row: PromptTemplate]
  debug: [row: PromptTemplate]
  toggle: [row: PromptTemplate]
  remove: [row: PromptTemplate]
}>()
</script>

<template>
  <div class="prompt-card hover-card">
    <!-- 渐变封面 -->
    <div class="prompt-cover" :style="{ background: coverGradientOf(row.category) }">
      <div class="cover-deco"></div>
      <el-icon :size="32" class="cover-icon"><Collection /></el-icon>
      <span v-if="row.category" class="cover-category">{{ categoryLabelOf(row.category) }}</span>
    </div>

    <!-- 信息区 -->
    <div class="prompt-body">
      <div class="prompt-title-row">
        <span class="prompt-name" :title="row.name">{{ row.name }}</span>
        <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">
          {{ row.status === 1 ? '启用' : '禁用' }}
        </el-tag>
      </div>
      <p class="prompt-desc">{{ row.description || '暂无描述' }}</p>
      <div class="prompt-foot">
        <span class="prompt-version" title="当前版本">
          <el-icon :size="12"><RefreshLeft /></el-icon>&nbsp;v{{ row.version }}
        </span>
        <span class="prompt-time">{{ formatTime(row.updateTime) }}</span>
      </div>
    </div>

    <!-- hover 浮现操作 -->
    <div class="prompt-actions">
      <el-tooltip content="编辑模板" placement="top">
        <div class="action-btn" @click="emit('edit', row)">
          <el-icon :size="16"><Edit /></el-icon>
        </div>
      </el-tooltip>
      <el-tooltip content="版本历史" placement="top">
        <div class="action-btn" @click="emit('versions', row)">
          <el-icon :size="16"><Clock /></el-icon>
        </div>
      </el-tooltip>
      <el-tooltip content="调试试跑" placement="top">
        <div class="action-btn" @click="emit('debug', row)">
          <el-icon :size="16"><VideoPlay /></el-icon>
        </div>
      </el-tooltip>
      <el-tooltip :content="row.status === 1 ? '禁用' : '启用'" placement="top">
        <div class="action-btn" @click="emit('toggle', row)">
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
.prompt-card {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  flex-direction: column;
}

/* 渐变封面（对齐智能体应用卡片） */
.prompt-cover {
  position: relative;
  height: 92px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
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
  position: relative;
  z-index: 1;
  color: #fff;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.22));
  transition: transform 0.25s ease;
}
.prompt-card:hover .cover-icon {
  transform: scale(1.18) rotate(-6deg);
}
.cover-category {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 9px;
  border-radius: 10px;
  backdrop-filter: blur(4px);
}

/* 信息区 */
.prompt-body {
  padding: 13px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.prompt-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.prompt-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.prompt-desc {
  font-size: 12.5px;
  color: var(--text-tertiary);
  min-height: 38px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.6;
}
.prompt-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}
.prompt-version {
  font-size: 12px;
  color: var(--brand-1);
  background: var(--brand-gradient-soft);
  padding: 2px 8px;
  border-radius: 8px;
  display: flex;
  align-items: center;
}
.prompt-time {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

/* hover 从底部浮现操作（对齐智能体应用卡片） */
.prompt-actions {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 10px 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: linear-gradient(180deg, transparent, var(--bg-card) 34%);
  opacity: 0;
  transform: translateY(8px);
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}
.prompt-card:hover .prompt-actions {
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
