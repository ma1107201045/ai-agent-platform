<script setup lang="ts">
import { Cpu, Plus } from '@element-plus/icons-vue'
import type { ModelInfo, ModelProvider } from '@/api/types'
import { copyText } from '@/utils/clipboard'
import {
  capLabel,
  fmtK,
  fmtNum,
  fmtTime,
  modelTypeLabel,
  modelTypeTag,
  parseCaps
} from '../modelMeta'

defineProps<{
  provider: ModelProvider
  models: ModelInfo[]
  loading?: boolean
}>()

const emit = defineEmits<{
  createModel: [provider: ModelProvider]
  editModel: [m: ModelInfo, provider: ModelProvider]
  removeModel: [m: ModelInfo]
  testModel: [m: ModelInfo, provider: ModelProvider, mode: 'chat' | 'embed']
  statusChange: [m: ModelInfo]
}>()
</script>

<template>
  <div class="expand-panel">
    <div class="model-panel-head">
      <div class="model-panel-title">
        <el-icon><Cpu /></el-icon>
        <span>「{{ provider.name }}」下的模型</span>
        <span class="count-badge">{{ models.length }}</span>
      </div>
      <el-button type="primary" plain @click="emit('createModel', provider)">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>添加模型
      </el-button>
    </div>

    <el-table
      :data="models"
      size="small"
      :empty-text="loading ? '模型加载中…' : '暂无模型，点击右上角「添加模型」'"
    >
      <el-table-column label="模型名" min-width="180">
        <template #default="{ row: m }">
          <div class="model-name-cell">
            <span class="mono model-name">{{ m.name }}</span>
            <el-button link type="primary" @click="copyText(m.name, '已复制')">复制</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="160">
        <template #default="{ row: m }">
          <el-tag size="small" :type="modelTypeTag(m.modelType)" effect="light">
            {{ modelTypeLabel(m.modelType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上下文窗口" width="110">
        <template #default="{ row: m }">
          <span class="mono">{{ fmtK(m.contextWindow) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最大输出" width="110">
        <template #default="{ row: m }">
          <span class="mono">{{ fmtNum(m.maxTokens) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="能力" min-width="200">
        <template #default="{ row: m }">
          <div v-if="parseCaps(m.capabilities).length" class="cap-list">
            <el-tag
              v-for="c in parseCaps(m.capabilities)"
              :key="c"
              size="small"
              class="cap-tag"
              effect="plain"
            >
              {{ capLabel(c) }}
            </el-tag>
          </div>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row: m }">
          <el-switch
            v-model="m.status"
            :active-value="1"
            :inactive-value="0"
            @change="emit('statusChange', m)"
          />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="140">
        <template #default="{ row: m }">
          <span class="muted">{{ fmtTime(m.updateTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" align="right">
        <template #default="{ row: m }">
          <el-button
            v-if="m.modelType === 'llm'"
            link
            type="success"
            @click="emit('testModel', m, provider, 'chat')"
          >
            对话测试
          </el-button>
          <el-button
            v-else-if="m.modelType === 'embedding'"
            link
            type="success"
            @click="emit('testModel', m, provider, 'embed')"
          >
            向量测试
          </el-button>
          <el-button link type="primary" @click="emit('editModel', m, provider)">编辑</el-button>
          <el-button link type="danger" @click="emit('removeModel', m)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
/* 展开面板 */
.expand-panel {
  margin: 4px 8px 12px;
  padding: 12px 16px;
  background: var(--fill-lighter);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}
.model-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.model-panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}
.model-panel-title .el-icon {
  color: var(--brand-1);
}
.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 11px;
}
.model-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.model-name {
  font-weight: 500;
}
.cap-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.cap-tag {
  border-color: var(--border-color);
  color: var(--text-secondary);
}
.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
</style>
