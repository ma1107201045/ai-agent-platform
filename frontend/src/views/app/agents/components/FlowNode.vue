<script setup lang="ts">
import type { FlowNodeData } from '@/utils/flow'
import { Handle, Position } from '@vue-flow/core'
import { Notebook } from '@element-plus/icons-vue'
import { branchHandlesOf } from '@/utils/flow'
import { hasNodeError, nodeWarnings } from '@/utils/flowValidation'
import { iconOf, metaOf, nodeRunClass } from '../nodeVisual'

/**
 * 画布自定义节点（VueFlow #node-flow-node 插槽内容）：
 * 图标 + 标题/描述 + 运行态徽标/着色 + 分支出点 + 「+」快速添加按钮
 */
defineProps<{
  id: string
  data: FlowNodeData
  selected?: boolean
  highlighted?: boolean
}>()

const emit = defineEmits<{
  openAddMenu: [e: MouseEvent, id: string, handle: string | null]
}>()
</script>

<template>
  <div
    class="flow-node"
    :class="[data.nodeType, { selected }, nodeRunClass(data), { 'run-highlight': highlighted }]"
    :style="
      data.nodeType === 'condition'
        ? { minHeight: `${52 + branchHandlesOf(data).length * 26}px` }
        : undefined
    "
  >
    <span v-if="data.runStatus === 'success'" class="run-badge run-badge-success">✓</span>
    <span v-else-if="data.runStatus === 'error'" class="run-badge run-badge-error">!</span>
    <span v-else-if="data.runStatus === 'canceled'" class="run-badge run-badge-canceled">✕</span>
    <span
      v-else-if="!data.runStatus && nodeWarnings(data).length"
      class="node-warn-badge"
      :class="{ warn: !hasNodeError(data) }"
      :title="
        nodeWarnings(data)
          .map((w) => w.text)
          .join('；')
      "
      >⚠</span
    >
    <div class="node-head">
      <div class="node-icon" :style="{ background: metaOf(data.nodeType).color }">
        <el-icon :size="16" color="#fff">
          <component :is="iconOf(metaOf(data.nodeType).icon)" />
        </el-icon>
      </div>
      <div class="node-head-text">
        <span class="node-title">{{ data.label }}</span>
        <span class="node-desc">{{ metaOf(data.nodeType).desc }}</span>
      </div>
      <el-tooltip v-if="data.remark" :content="data.remark" placement="top" :show-after="200">
        <span class="node-remark"
          ><el-icon :size="12"><Notebook /></el-icon
        ></span>
      </el-tooltip>
    </div>
    <span v-if="data.runCost !== undefined" class="run-cost">{{ data.runCost }}ms</span>
    <div v-if="data.runError" class="run-error-msg" :title="data.runError">
      {{ data.runError }}
    </div>
    <Handle type="target" :position="Position.Left" class="node-handle"></Handle>
    <!-- 条件分支：多分支模式下按分支配置动态渲染出点 -->
    <template v-if="data.nodeType === 'condition'">
      <template v-for="(h, i) in branchHandlesOf(data)" :key="h.key">
        <span
          class="branch-tag"
          :style="{
            top: `calc(${((i + 1) / (branchHandlesOf(data).length + 1)) * 100}% - 8px)`,
            color: h.color,
            borderColor: h.color,
            background: `${h.color}1f`
          }"
          >{{ h.label }}</span
        >
        <Handle
          type="source"
          :position="Position.Right"
          :id="h.key"
          class="node-handle handle-branch"
          :style="{
            top: `${((i + 1) / (branchHandlesOf(data).length + 1)) * 100}%`,
            borderColor: h.color,
            background: h.color
          }"
        ></Handle>
        <span
          class="node-add-btn add-btn-branch"
          :style="{ top: `${((i + 1) / (branchHandlesOf(data).length + 1)) * 100}%` }"
          :title="`在「${h.label}」分支后添加节点`"
          @click.stop="emit('openAddMenu', $event, id, h.key)"
          @mousedown.stop
          >+</span
        >
      </template>
    </template>
    <template v-else>
      <Handle type="source" :position="Position.Right" class="node-handle"></Handle>
      <span
        v-if="data.nodeType !== 'end'"
        class="node-add-btn"
        title="添加节点"
        @click.stop="emit('openAddMenu', $event, id, null)"
        @mousedown.stop
        >+</span
      >
    </template>
  </div>
</template>

<style scoped>
/* 节点卡片（简约：纯色圆角图标 + 标题/描述，选中蓝框） */
.flow-node {
  position: relative;
  width: 210px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(31, 36, 55, 0.05);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.flow-node:hover {
  border-color: #2970ff;
}

.flow-node.selected {
  border-color: #2970ff;
  box-shadow: 0 0 0 2px rgba(41, 112, 255, 0.28);
}

.node-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}

.node-head-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.node-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-desc {
  margin-top: 1px;
  font-size: 11px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-handle {
  width: 9px !important;
  height: 9px !important;
  background: #2970ff !important;
  border: 2px solid var(--bg-card) !important;
  transition: transform 0.15s ease;
}

.flow-node:hover .node-handle {
  transform: scale(1.25);
}

.node-add-btn {
  position: absolute;
  right: -28px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--bg-card);
  border: 1px solid var(--brand-1);
  color: var(--brand-1);
  font-size: 14px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;
  z-index: 6;
  user-select: none;
}

.flow-node:hover .node-add-btn {
  opacity: 1;
}

.node-add-btn:hover {
  background: var(--brand-1);
  color: #fff;
  transform: translateY(-50%) scale(1.1);
}

/* 分支手柄的纵向位置与配色由节点内联样式按分支数量动态计算 */
.flow-node.condition .handle-branch {
  width: 9px !important;
  height: 9px !important;
  min-width: 9px;
  min-height: 9px;
  border-width: 1.5px;
}

.branch-tag {
  position: absolute;
  right: -22px;
  z-index: 1;
  font-size: 10px;
  line-height: 16px;
  font-weight: 600;
  padding: 0 5px;
  border-radius: 6px;
  pointer-events: none;
  white-space: nowrap;
  max-width: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- 节点运行状态（静态描边，克制动效） ---------- */
.flow-node.run-success {
  border-color: #67c23a;
  box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.18);
}

.flow-node.run-error {
  border-color: #f56c6c;
  box-shadow: 0 0 0 2px rgba(245, 108, 108, 0.22);
}

.flow-node.run-skipped {
  opacity: 0.5;
  filter: grayscale(0.5);
}

/* 执行中：呼吸式高亮（微弱脉冲，突出当前活跃节点） */
.flow-node.run-running {
  border-color: #409eff;
  animation: run-pulse 1.4s ease-in-out infinite;
}

@keyframes run-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.12);
  }
  50% {
    box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.35);
  }
}

/* 被取消的运行节点：灰色虚线终态 */
.flow-node.run-canceled {
  border-style: dashed;
  border-color: #909399;
  opacity: 0.65;
}

.flow-node.run-highlight {
  border-color: var(--brand-1);
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.25);
}

.run-badge {
  position: absolute;
  top: -7px;
  right: -7px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  z-index: 2;
}

.run-badge-success {
  background: #67c23a;
}

.run-badge-error {
  background: #f56c6c;
}

.run-badge-canceled {
  background: #909399;
}

.node-warn-badge {
  position: absolute;
  top: -7px;
  right: -7px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  z-index: 2;
  cursor: help;
}

.node-warn-badge.warn {
  background: #e6a23c;
}

.node-remark {
  display: inline-flex;
  align-items: center;
  color: #e6a23c;
  cursor: help;
  flex: none;
}

.run-cost {
  position: absolute;
  right: 10px;
  bottom: 6px;
  font-size: 10px;
  color: var(--text-tertiary);
  background: var(--bg-card);
  padding: 1px 6px;
  border-radius: 8px;
}

.run-error-msg {
  margin-top: 8px;
  font-size: 10.5px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border-radius: 6px;
  padding: 3px 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
