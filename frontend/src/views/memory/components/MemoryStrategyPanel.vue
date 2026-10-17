<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { memoryApi } from '@/api/memory'
import type { ChatModelInfo, MemStrategy } from '@/api/types'

/** 记忆策略面板：v-model 绑定 strategy（父级用于统计卡片），保存成功 emit saved */
defineProps<{ chatModels: ChatModelInfo[] }>()
const strategy = defineModel<MemStrategy | null>({ required: true })
const emit = defineEmits<{ saved: [] }>()

const saving = ref(false)

async function saveStrategy() {
  const s = strategy.value
  if (!s) return
  if (s.autoExtract === 1 && !s.extractModelId) {
    ElMessage.warning('已开启自动抽取但未选择抽取模型，自动抽取将不会执行')
  }
  saving.value = true
  try {
    await memoryApi.saveStrategy({
      ...s,
      keepDays: s.keepDays || null,
      extractModelId: s.autoExtract === 1 ? s.extractModelId || null : null
    })
    ElMessage.success('记忆策略已保存')
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="strategy" class="strategy-wrap">
    <div class="strategy-intro">
      <h3 class="strategy-title">记忆策略</h3>
      <p class="strategy-desc">
        配置该应用如何存储与召回长期记忆。建议在发布时同步调整；自动抽取依赖可用的对话模型完成记忆沉淀。
      </p>
    </div>
    <el-form label-width="180px" class="strategy-form" label-position="left">
      <el-form-item label="启用长期记忆">
        <el-switch v-model="strategy.enabled" :active-value="1" :inactive-value="0" />
        <span class="field-tip">关闭后该应用的记忆不再写入，也不会被注入到对话上下文</span>
      </el-form-item>
      <el-form-item label="对话后自动抽取">
        <el-switch v-model="strategy.autoExtract" :active-value="1" :inactive-value="0" />
        <span class="field-tip">每次对话结束后，由抽取模型将用户偏好/事实沉淀为记忆条目</span>
      </el-form-item>
      <el-form-item v-if="strategy.autoExtract === 1" label="抽取使用模型">
        <el-select
          v-model="strategy.extractModelId"
          placeholder="请选择对话模型"
          clearable
          filterable
          class="model-select"
        >
          <el-option
            v-for="m in chatModels"
            :key="m.id"
            :value="m.id"
            :label="`${m.providerName} · ${m.modelName}`"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="每次注入条数">
        <el-input-number v-model="strategy.topN" :min="1" :max="20" />
        <span class="field-tip">对话时从记忆中挑选并注入上下文的最大条数</span>
      </el-form-item>
      <el-form-item label="单应用条目上限">
        <el-input-number v-model="strategy.maxItems" :min="10" :max="100000" :step="100" />
        <span class="field-tip">达到上限后按“重要度优先”淘汰旧条目</span>
      </el-form-item>
      <el-form-item label="记忆保留天数">
        <el-input-number
          v-model="strategy.keepDays"
          :min="0"
          :max="3650"
          placeholder="永久保留"
          class="keep-days-input"
        />
        <span class="field-tip">留空（0）表示永久保留，非 0 时超过保留期的记忆自动失效</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="saveStrategy">保存记忆策略</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.strategy-wrap {
  padding: 12px 4px 20px;
  max-width: 780px;
}
.strategy-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
}
.strategy-desc {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--text-tertiary, #8a8f98);
  line-height: 1.7;
}
.field-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
.model-select {
  width: 320px;
}
.keep-days-input {
  width: 200px;
}
</style>
