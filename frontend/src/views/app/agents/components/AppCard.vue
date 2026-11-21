<script setup lang="ts">
import { Delete } from '@element-plus/icons-vue'
import type { AppAgent } from '@/api/types'
import { formatTime } from '@/utils/format'
import { typeMeta } from '../agentMeta'

defineProps<{ row: AppAgent }>()

const emit = defineEmits<{
  edit: [row: AppAgent]
  chat: [row: AppAgent]
  publish: [row: AppAgent]
  remove: [row: AppAgent]
}>()
</script>

<template>
  <article class="app-card hover-card">
    <div class="card-top">
      <span class="app-type"
        ><el-icon><component :is="typeMeta[row.type]?.icon" /></el-icon
        >{{ typeMeta[row.type]?.label || row.type }}</span
      >
      <span class="status" :class="{ published: row.status === 1 }">{{
        row.status === 1 ? '已发布' : '草稿'
      }}</span>
    </div>
    <button class="app-name" :title="row.name" @click="emit('edit', row)">{{ row.name }}</button>
    <p class="app-desc">{{ row.description || '暂无描述' }}</p>
    <p class="app-time">更新于 {{ formatTime(row.updateTime) }}</p>
    <div class="app-actions">
      <el-button link type="primary" @click="emit('edit', row)">编排</el-button>
      <el-button link @click="emit('chat', row)">对话</el-button>
      <el-button link @click="emit('publish', row)">发布</el-button>
      <el-tooltip content="删除应用" placement="top"
        ><button class="delete-btn" :aria-label="'删除' + row.name" @click="emit('remove', row)">
          <el-icon><Delete /></el-icon></button
      ></el-tooltip>
    </div>
  </article>
</template>

<style scoped>
.app-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 16px 18px 4px;
}
.card-top,
.app-type,
.app-actions {
  display: flex;
  align-items: center;
}
.card-top {
  justify-content: space-between;
  gap: 8px;
}
.app-type {
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}
.status {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--fill-light);
  border-radius: 3px;
  padding: 2px 6px;
}
.status.published {
  color: var(--published-text);
  background: var(--published-bg);
}
.app-name,
.delete-btn {
  font: inherit;
  color: inherit;
  background: none;
  border: 0;
  cursor: pointer;
}
.app-name {
  margin: 16px 0 6px;
  font-weight: 600;
  font-size: 17px;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.app-name:hover {
  color: var(--brand-1);
}
.app-desc,
.app-time {
  font-size: 13px;
  color: var(--text-secondary);
}
.app-desc {
  line-height: 1.6;
  overflow-wrap: anywhere;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.app-time {
  margin-top: auto;
  padding: 18px 0 12px;
  font-size: 12px;
}
.app-actions {
  gap: 14px;
  border-top: 1px solid var(--border-color);
  min-height: 40px;
}
.app-actions .el-button {
  margin: 0;
}
.delete-btn {
  margin-left: auto;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--text-secondary);
}
.delete-btn:hover {
  color: var(--el-color-danger);
}
</style>
