<script setup lang="ts">
import { Close, VideoPlay } from '@element-plus/icons-vue'
import type { RunResult, TraceItem } from '@/api/types'
import { nodeStatusColor } from '../nodeVisual'

/** 运行调试面板（SSE 实时节点状态） */
defineProps<{
  running: boolean
  curRunId: string | null
  runResult: RunResult | null
  displayTrace: TraceItem[]
  highlightedNodeId: string | null
  runStatusMeta: { tag: 'success' | 'danger' | 'info' | 'warning'; text: string }
}>()

const emit = defineEmits<{
  run: []
  stop: []
  highlight: [nodeId: string]
}>()

const visible = defineModel<boolean>('visible', { required: true })
const input = defineModel<string>('input', { required: true })
</script>

<template>
  <div v-if="visible" class="debug-panel">
    <div class="debug-head">
      <div class="debug-title">
        <el-icon>
          <VideoPlay />
        </el-icon>
        运行调试
      </div>
      <div class="debug-head-actions">
        <el-button
          v-if="running && curRunId"
          size="small"
          type="danger"
          plain
          :disabled="!curRunId"
          @click="emit('stop')"
          >停止</el-button
        >
        <el-button
          size="small"
          type="primary"
          :icon="VideoPlay"
          :loading="running"
          @click="emit('run')"
        >
          运行
        </el-button>
        <el-button size="small" text :icon="Close" @click="visible = false">收起</el-button>
      </div>
    </div>
    <div class="debug-body">
      <div class="debug-input-area">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入测试消息后点击「运行」，节点将逐个实时点亮（运行中可停止）"
          @keydown.enter.exact.prevent="emit('run')"
        />
      </div>
      <div class="debug-result-area">
        <template v-if="displayTrace.length > 0">
          <div v-if="runResult" class="debug-answer">
            <span class="debug-answer-label">最终回答</span>
            <div class="debug-answer-text">{{ runResult.answer }}</div>
          </div>
          <div class="debug-trace-head">
            <span>{{ runResult ? '执行轨迹（点击定位节点）' : '执行轨迹（实时）' }}</span>
            <el-tag v-if="running && !runResult" size="small" type="info" effect="light"
              >运行中…</el-tag
            >
            <el-tag v-else-if="runResult" size="small" :type="runStatusMeta.tag" effect="light">
              {{ runStatusMeta.text }}
            </el-tag>
          </div>
          <div class="debug-trace-list">
            <div
              v-for="(t, i) in displayTrace"
              :key="i"
              class="debug-trace-item"
              :class="{ active: t.nodeId === highlightedNodeId }"
              @click="emit('highlight', t.nodeId)"
            >
              <span class="dt-index">{{ i + 1 }}</span>
              <el-tag size="small" :type="nodeStatusColor[t.status] || 'info'" effect="light">
                {{ t.status }}
              </el-tag>
              <span class="dt-label" :title="t.label">{{ t.label }}</span>
              <span class="dt-cost">{{ t.costMs }}ms</span>
              <span v-if="t.error" class="dt-error" :title="t.error">⚠ {{ t.error }}</span>
            </div>
          </div>
        </template>
        <el-empty
          v-else-if="!running"
          description="输入测试消息后点击「运行」，节点将逐个实时点亮并在此展示结果"
          :image-size="52"
        />
        <div v-else class="debug-running-hint">正在运行，节点将逐个点亮…</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.debug-panel {
  flex-shrink: 0;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
  z-index: 5;
}

.debug-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid var(--border-color);
}

.debug-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-1);
}

.debug-head-actions {
  display: flex;
  gap: 6px;
}

.debug-body {
  display: flex;
  gap: 16px;
  padding: 12px 14px;
  min-height: 118px;
  max-height: 240px;
}

.debug-input-area {
  width: 300px;
  flex-shrink: 0;
}

.debug-result-area {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.debug-answer {
  background: var(--fill-light);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 10px;
}

.debug-running-hint {
  padding: 16px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}

.debug-answer-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.debug-answer-text {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.debug-trace-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.debug-trace-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.debug-trace-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: 12px;
}

.debug-trace-item:hover {
  border-color: var(--brand-3);
  background: var(--fill-lighter);
}

.debug-trace-item.active {
  border-color: var(--brand-1);
  background: var(--el-color-primary-light-9);
}

.dt-index {
  width: 16px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 11px;
}

.dt-label {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dt-cost {
  color: var(--text-tertiary);
  font-size: 11px;
  flex-shrink: 0;
}

.dt-error {
  color: #f56c6c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
