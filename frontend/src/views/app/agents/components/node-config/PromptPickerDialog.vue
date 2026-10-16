<script setup lang="ts">
import { ref } from 'vue'
import { appPromptApi } from '@/api/app-prompt'
import type { PromptTemplate } from '@/api/types'

/** 从提示词库选择模板（应用到节点的系统提示词） */
const emit = defineEmits<{ apply: [template: PromptTemplate] }>()

const visible = ref(false)
const loading = ref(false)
const promptTemplates = ref<PromptTemplate[]>([])
const PROMPT_CATEGORY_LABEL: Record<string, string> = {
  general: '通用',
  system: '系统',
  business: '业务',
  custom: '自定义'
}

async function open() {
  visible.value = true
  loading.value = true
  try {
    promptTemplates.value = await appPromptApi.enabled()
  } catch {
    promptTemplates.value = []
  } finally {
    loading.value = false
  }
}

function pick(t: PromptTemplate) {
  visible.value = false
  emit('apply', t)
}

/** 模板正文预览：折叠空白 + 截断 */
function previewTemplate(s?: string) {
  if (!s) return ''
  const t = s.replace(/\s+/g, ' ').trim()
  return t.length > 160 ? t.slice(0, 160) + '…' : t
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="从提示词库选择模板"
    width="680px"
    :close-on-click-modal="false"
    class="prompt-picker-dialog"
  >
    <div v-loading="loading" class="template-picker">
      <div v-for="t in promptTemplates" :key="t.id" class="template-picker-card" @click="pick(t)">
        <div class="tpc-head">
          <span class="tpc-name">{{ t.name }}</span>
          <el-tag size="small" effect="plain">{{
            PROMPT_CATEGORY_LABEL[t.category || 'general'] || t.category
          }}</el-tag>
          <span class="tpc-version">v{{ t.version }}</span>
        </div>
        <p v-if="t.description" class="tpc-desc">{{ t.description }}</p>
        <pre class="tpc-content">{{ previewTemplate(t.content) }}</pre>
      </div>
      <div v-if="!loading && promptTemplates.length === 0" class="template-picker-empty">
        提示词库暂无启用的模板，请先到「提示词库」页创建
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.prompt-picker-dialog .template-picker {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
  max-height: 56vh;
  overflow: auto;
}
.template-picker-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.18s ease;
  background: #fff;
}
.template-picker-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.tpc-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tpc-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.tpc-version {
  font-size: 12px;
  color: var(--brand-1);
  background: var(--brand-gradient-soft);
  padding: 1px 8px;
  border-radius: 8px;
}
.tpc-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
.tpc-content {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
  background: var(--bg-fill, #f5f7fa);
  border-radius: 8px;
  padding: 8px 10px;
  white-space: pre-wrap;
  word-break: break-word;
}
.template-picker-empty {
  text-align: center;
  color: var(--text-tertiary);
  padding: 40px 0;
  font-size: 13px;
}
</style>
