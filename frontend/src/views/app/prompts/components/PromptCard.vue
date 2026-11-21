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
import { categoryLabelOf } from '../promptMeta'
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
    <div class="prompt-cover">
      <el-icon :size="16"><Collection /></el-icon>
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

    <div class="prompt-actions">
      <el-tooltip content="编辑模板" placement="top">
        <button aria-label="编辑模板" class="action-btn" @click="emit('edit', row)">
          <el-icon :size="16"><Edit /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="版本历史" placement="top">
        <button aria-label="版本历史" class="action-btn" @click="emit('versions', row)">
          <el-icon :size="16"><Clock /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="调试试跑" placement="top">
        <button aria-label="调试试跑" class="action-btn" @click="emit('debug', row)">
          <el-icon :size="16"><VideoPlay /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip :content="row.status === 1 ? '禁用' : '启用'" placement="top">
        <button aria-label="切换启用状态" class="action-btn" @click="emit('toggle', row)">
          <el-icon :size="16"><Promotion /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="删除" placement="top">
        <button aria-label="删除" class="action-btn danger" @click="emit('remove', row)">
          <el-icon :size="16"><Delete /></el-icon>
        </button>
      </el-tooltip>
    </div>
  </div>
</template>

<style scoped>
.prompt-card {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.prompt-cover {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 16px 16px 0;
  font-size: 12px;
  color: var(--text-secondary);
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

.prompt-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  border-top: 1px solid var(--border-color);
}
.action-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: 0;
  border-radius: 4px;
  color: var(--text-secondary);
  cursor: pointer;
}
.action-btn:hover {
  color: var(--brand-1);
  background: var(--accent-surface);
}
.action-btn.danger {
  margin-left: auto;
}
.action-btn.danger:hover {
  color: var(--el-color-danger);
}
</style>
