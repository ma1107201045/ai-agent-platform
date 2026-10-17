<script setup lang="ts">
import { Clock } from '@element-plus/icons-vue'
import type { Skill } from '@/api/types'
import { formatTime } from '@/utils/format'
import { coverGradientOf, categoryLabelOf, typeLabelOf, typeMeta, parseIdList } from '../skillMeta'

/** 技能卡片：封面/标签/依赖/操作 */
const props = defineProps<{
  row: Skill
  toolOptions: { id: number; name: string }[]
  datasetOptions: { id: number; name: string }[]
}>()

const emit = defineEmits<{
  edit: [row: Skill]
  remove: [row: Skill]
  versions: [row: Skill]
  export: [row: Skill]
  toggle: [row: Skill]
}>()

const toolNameOf = (id: number) => props.toolOptions.find((t) => t.id === id)?.name || `#${id}`
const datasetNameOf = (id: number) =>
  props.datasetOptions.find((d) => d.id === id)?.name || `#${id}`
</script>

<template>
  <div class="skill-card hover-card">
    <div class="card-top">
      <div class="card-icon" :style="{ background: coverGradientOf(row.category) }">
        {{ row.icon || typeMeta[row.type]?.icon || '⚡' }}
      </div>
      <div class="card-tags">
        <el-tag size="small" effect="dark" type="info">{{ categoryLabelOf(row.category) }}</el-tag>
        <el-tag size="small" :type="typeMeta[row.type]?.type || 'info'">{{
          typeLabelOf(row.type)
        }}</el-tag>
        <el-switch
          :model-value="row.status === 1"
          inline-prompt
          active-text="启用"
          inactive-text="停用"
          class="status-switch"
          @change="emit('toggle', row)"
        />
      </div>
    </div>
    <div class="card-name">
      {{ row.name }}
      <span class="code-chip">{{ row.code }}</span>
    </div>
    <div class="card-desc">{{ row.description || '暂无描述' }}</div>

    <div class="card-deps">
      <template v-if="row.type === 'tool' && parseIdList(row.toolIds).length">
        <span class="dep-label">工具</span>
        <span v-for="id in parseIdList(row.toolIds).slice(0, 3)" :key="id" class="dep-chip">{{
          toolNameOf(id)
        }}</span>
        <span v-if="parseIdList(row.toolIds).length > 3" class="dep-more"
          >+{{ parseIdList(row.toolIds).length - 3 }}</span
        >
      </template>
      <template v-else-if="row.type === 'knowledge' && parseIdList(row.datasetIds).length">
        <span class="dep-label">数据集</span>
        <span v-for="id in parseIdList(row.datasetIds).slice(0, 3)" :key="id" class="dep-chip">{{
          datasetNameOf(id)
        }}</span>
        <span v-if="parseIdList(row.datasetIds).length > 3" class="dep-more"
          >+{{ parseIdList(row.datasetIds).length - 3 }}</span
        >
      </template>
      <template v-else>
        <span class="dep-empty">{{ row.variables ? '含变量注入' : '无外部依赖' }}</span>
      </template>
    </div>

    <div class="card-foot">
      <div class="foot-meta">
        <span
          ><el-icon><Clock /></el-icon> v{{ row.version }}</span
        >
        <span>更新于 {{ formatTime(row.updateTime) }}</span>
      </div>
      <div class="foot-actions">
        <el-button size="small" link type="primary" @click="emit('versions', row)">版本</el-button>
        <el-button size="small" link @click="emit('export', row)">导出</el-button>
        <el-button size="small" link @click="emit('edit', row)">编辑</el-button>
        <el-button size="small" link type="danger" @click="emit('remove', row)">删除</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skill-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  min-height: 220px;
  position: relative;
}
.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.card-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  flex-shrink: 0;
}
.card-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.card-name {
  margin-top: 12px;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.code-chip {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--text-tertiary);
  background: var(--hover-bg, #f1f5f9);
  border-radius: 4px;
  padding: 1px 6px;
}
.card-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-deps {
  margin-top: 10px;
  min-height: 20px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.dep-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  margin-right: 2px;
}
.dep-chip {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 10px;
  background: var(--brand-gradient-soft, #f1f2ff);
  color: var(--brand-1, #5b6cff);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dep-more {
  font-size: 11px;
  color: var(--text-tertiary);
}
.dep-empty {
  font-size: 11px;
  color: var(--text-tertiary);
}
.card-foot {
  margin-top: auto;
  padding-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--border-color);
}
.foot-meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.foot-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.foot-actions {
  display: flex;
  align-items: center;
}
.status-switch {
  flex-shrink: 0;
}
.status-switch :deep(.el-switch.is-checked) .el-switch__core {
  background: var(--brand-1);
}
.status-switch :deep(.el-switch__core) {
  border-color: var(--border-color);
}
</style>
