<script setup lang="ts">
import type { WorkflowNodeType } from '@/api/types'
import { iconOf, metaOf, paletteGroups } from '../nodeVisual'

/** 节点右侧「+」弹出的节点选择浮层（Dify 式），fixed 定位，由父组件负责外部点击关闭 */
defineProps<{
  menu: { x: number; y: number; sourceId: string; handle: string | null } | null
}>()

const emit = defineEmits<{
  select: [type: WorkflowNodeType]
}>()
</script>

<template>
  <div
    v-if="menu"
    class="add-node-menu"
    :style="{ left: menu.x + 'px', top: menu.y + 'px' }"
    @click.stop
  >
    <div class="add-node-menu-title">添加节点</div>
    <div v-for="g in paletteGroups" :key="g.title" class="add-node-group">
      <div class="add-node-group-title">{{ g.title }}</div>
      <div
        v-for="t in g.types"
        :key="t"
        class="add-node-item"
        :class="{ disabled: t === 'start' }"
        @click="t !== 'start' && emit('select', t)"
      >
        <div class="add-node-item-icon" :style="{ background: metaOf(t).color }">
          <el-icon :size="12" color="#fff">
            <component :is="iconOf(metaOf(t).icon)" />
          </el-icon>
        </div>
        <div class="add-node-item-text">
          <div class="add-node-item-label">{{ metaOf(t).label }}</div>
          <div class="add-node-item-desc">{{ metaOf(t).desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.add-node-menu {
  position: fixed;
  z-index: 3000;
  width: 240px;
  max-height: 330px;
  overflow-y: auto;
  background: var(--bg-elevated);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: var(--shadow-pop);
  padding: 8px;
}

.add-node-menu-title {
  font-size: 12px;
  color: var(--text-tertiary);
  font-weight: 600;
  padding: 2px 6px 8px;
}

.add-node-group {
  margin-bottom: 6px;
}

.add-node-group-title {
  font-size: 11px;
  color: var(--text-tertiary);
  padding: 2px 6px 4px;
}

.add-node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.add-node-item:hover {
  background: var(--el-color-primary-light-9);
}

.add-node-item.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.add-node-item-icon {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}

.add-node-item-text {
  min-width: 0;
}

.add-node-item-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
}

.add-node-item-desc {
  font-size: 11px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
