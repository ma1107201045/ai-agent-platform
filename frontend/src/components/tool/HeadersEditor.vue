<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, View } from '@element-plus/icons-vue'

/**
 * 请求头（Headers）可视化编辑器：键值对行编辑，兼容整段 JSON 粘贴。
 * 使用方式：<HeadersEditor v-model="headers" />（JSON 对象字符串，如 {"X-Key":"val"}）
 */
const props = defineProps<{ modelValue?: string; keyPlaceholder?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

interface KV {
  key: string
  value: string
}

function parseJson(text: string | undefined): KV[] {
  const src = (text || '').trim()
  if (!src) return []
  try {
    const obj = JSON.parse(src)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
    return Object.keys(obj).map((k) => ({ key: k, value: String((obj as Record<string, unknown>)[k] ?? '') }))
  } catch {
    return []
  }
}

function buildJson(rows: KV[]): string {
  const obj: Record<string, string> = {}
  for (const r of rows) {
    const key = r.key.trim()
    if (!key) continue
    obj[key] = r.value
  }
  return JSON.stringify(obj)
}

const rows = ref<KV[]>([])
let lastEmitted = ''

function sync() {
  const next = buildJson(rows.value)
  if (next === lastEmitted && next === (props.modelValue || '')) return
  lastEmitted = next
  emit('update:modelValue', next)
}

watch(
  () => props.modelValue,
  (v) => {
    if ((v || '') === lastEmitted) return
    rows.value = parseJson(v)
  },
  { immediate: true }
)

function addRow() {
  rows.value.push({ key: '', value: '' })
}

function removeRow(i: number) {
  rows.value.splice(i, 1)
  sync()
}

/* ---------------- JSON 模式 ---------------- */
const jsonMode = ref(false)
const jsonText = ref('')

function openJson() {
  jsonText.value = (props.modelValue || '').trim() || buildJson(rows.value)
  jsonMode.value = true
}

function applyJson() {
  try {
    const parsed = JSON.parse(jsonText.value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('必须是 JSON 对象，如 {"X-Key":"value"}')
    }
    rows.value = parseJson(jsonText.value)
    lastEmitted = ''
    sync()
    jsonMode.value = false
    ElMessage.success('已按 JSON 更新请求头')
  } catch (e) {
    ElMessage.warning('JSON 不合法：' + (e instanceof Error ? e.message : '格式错误'))
  }
}
</script>

<template>
  <div class="hse">
    <template v-if="!jsonMode">
      <div v-for="(row, i) in rows" :key="i" class="hse-row">
        <el-input v-model="row.key" class="hse-key" :placeholder="keyPlaceholder || '请求头名，如 X-Api-Key'" @update:model-value="sync" />
        <el-input v-model="row.value" class="hse-value" placeholder="值" @update:model-value="sync" />
        <el-button link type="danger" :icon="Delete" @click="removeRow(i)" />
      </div>
      <div class="hse-actions">
        <el-button type="primary" link :icon="Plus" @click="addRow">添加请求头</el-button>
        <el-button link :icon="View" @click="openJson">粘贴整段 JSON</el-button>
      </div>
      <p v-if="!rows.length" class="hse-hint">一般无需配置；仅当目标接口要求固定请求头（如签名）时添加。</p>
    </template>
    <template v-else>
      <el-input
        v-model="jsonText"
        type="textarea"
        :rows="4"
        spellcheck="false"
        placeholder='{"X-Api-Key":"your-key","X-Requested-By":"agent-platform"}'
      />
      <div class="hse-actions">
        <el-button type="primary" link @click="applyJson">应用 JSON 并返回表单</el-button>
        <el-button link @click="jsonMode = false">放弃修改</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hse {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hse-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hse-key {
  width: 46%;
  flex-shrink: 0;
}
.hse-value {
  flex: 1;
}
.hse-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hse-hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
