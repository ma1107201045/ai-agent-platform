<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, View } from '@element-plus/icons-vue'

/**
 * 可视化参数编辑器：把「参数 JSON Schema」转成普通用户能理解的行编辑表单。
 * 使用方式：<ParamsSchemaEditor v-model="tool.parameters" />（JSON Schema 字符串）
 */
const props = defineProps<{ modelValue?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

interface ParamRow {
  key: string
  type: string
  required: boolean
  desc: string
}

const TYPE_OPTIONS = [
  { value: 'string', label: '文本' },
  { value: 'integer', label: '整数' },
  { value: 'number', label: '数字' },
  { value: 'boolean', label: '是/否' }
]
const NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/

function parseSchema(text: string | undefined): ParamRow[] {
  const src = (text || '').trim()
  if (!src) return []
  try {
    const obj = JSON.parse(src)
    const propsMap: Record<string, Record<string, unknown>> = obj?.properties || {}
    const req: string[] = Array.isArray(obj?.required) ? obj.required : []
    return Object.keys(propsMap).map((k) => {
      const p = propsMap[k] || {}
      return {
        key: k,
        type: typeof p.type === 'string' ? p.type : 'string',
        required: req.includes(k),
        desc: typeof p.description === 'string' ? p.description : ''
      }
    })
  } catch {
    return []
  }
}

function buildSchema(rows: ParamRow[]): string {
  const propsMap: Record<string, Record<string, unknown>> = {}
  const required: string[] = []
  for (const r of rows) {
    const key = r.key.trim()
    if (!key) continue
    const prop: Record<string, unknown> = { type: TYPE_OPTIONS.some((t) => t.value === r.type) ? r.type : 'string' }
    if (r.desc.trim()) prop.description = r.desc.trim()
    propsMap[key] = prop
    if (r.required) required.push(key)
  }
  const schema: Record<string, unknown> = { type: 'object', properties: propsMap }
  if (required.length) schema.required = required
  return JSON.stringify(schema)
}

const rows = ref<ParamRow[]>([])
/** 记录上一次 emit 出去的内容，避免外部回写导致重复解析 */
let lastEmitted = ''

function sync() {
  const next = buildSchema(rows.value)
  if (next === lastEmitted && next === (props.modelValue || '')) return
  lastEmitted = next
  emit('update:modelValue', next)
}

watch(
  () => props.modelValue,
  (v) => {
    if ((v || '') === lastEmitted) return
    rows.value = parseSchema(v)
  },
  { immediate: true }
)

function addRow() {
  rows.value.push({ key: '', type: 'string', required: false, desc: '' })
}

function removeRow(i: number) {
  rows.value.splice(i, 1)
  sync()
}

/** 非法参数名提示（避免把中文/特殊字符写进 schema key） */
const badNameCount = computed(
  () => rows.value.filter((r) => r.key.trim() && !NAME_RE.test(r.key.trim())).length
)

/* ---------------- JSON 高级模式（可编辑预览） ---------------- */
const jsonMode = ref(false)
const jsonText = ref('')

function openJson() {
  jsonText.value = (props.modelValue || '').trim() || buildSchema(rows.value)
  jsonMode.value = true
}

function applyJson() {
  try {
    const parsed = JSON.parse(jsonText.value)
    if (!parsed || typeof parsed !== 'object' || parsed.type !== 'object') {
      throw new Error('必须是 {"type":"object",...} 结构')
    }
    rows.value = parseSchema(jsonText.value)
    lastEmitted = ''
    sync()
    jsonMode.value = false
    ElMessage.success('已按 JSON 更新参数')
  } catch (e) {
    ElMessage.warning('JSON 不合法：' + (e instanceof Error ? e.message : '格式错误'))
  }
}
</script>

<template>
  <div class="pse">
    <!-- 可视化编辑 -->
    <template v-if="!jsonMode">
      <div v-if="!rows.length" class="pse-empty">
        <span>还没配置参数。如果智能体调用它时需要传值（比如查询城市名），请点击「添加参数」逐条补充；不需要传参则直接跳过。</span>
      </div>

      <div v-for="(row, i) in rows" :key="i" class="pse-row">
        <el-input
          v-model="row.key"
          class="pse-key"
          placeholder="参数名（英文），如 city"
          :class="{ 'is-err': row.key.trim() && !NAME_RE.test(row.key.trim()) }"
          @update:model-value="sync"
        />
        <el-select v-model="row.type" class="pse-type" @update:model-value="sync">
          <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-tooltip content="是否必填（必填参数智能体会确保传入）" placement="top">
          <el-checkbox v-model="row.required" @update:model-value="sync">必填</el-checkbox>
        </el-tooltip>
        <el-input
          v-model="row.desc"
          class="pse-desc"
          placeholder="给智能体的说明，如：城市名称，例 北京"
          @update:model-value="sync"
        />
        <el-button link type="danger" :icon="Delete" @click="removeRow(i)" />
      </div>

      <div class="pse-actions">
        <el-button type="primary" link :icon="Plus" @click="addRow">添加参数</el-button>
        <el-button link :icon="View" @click="openJson">预览 / 编辑 JSON</el-button>
      </div>

      <p v-if="badNameCount" class="pse-hint err">参数名须为英文标识符（字母/数字/下划线，不能以数字开头），否则模型无法正确识别</p>
    </template>

    <!-- 高级 JSON 模式 -->
    <template v-else>
      <el-input
        v-model="jsonText"
        type="textarea"
        :rows="7"
        class="pse-json"
        spellcheck="false"
        placeholder='{"type":"object","properties":{"city":{"type":"string","description":"城市名"}},"required":["city"]}'
      />
      <div class="pse-actions">
        <el-button type="primary" link @click="applyJson">应用 JSON 并返回表单</el-button>
        <el-button link @click="jsonMode = false">放弃修改</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.pse {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pse-empty {
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  font-size: 12.5px;
  color: var(--text-tertiary);
  background: var(--fill-lighter);
  line-height: 1.7;
}
.pse-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pse-key {
  width: 150px;
  flex-shrink: 0;
}
.pse-type {
  width: 92px;
  flex-shrink: 0;
}
.pse-desc {
  flex: 1;
  min-width: 120px;
}
.pse-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pse-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
}
.pse-hint.err {
  color: #f56c6c;
}
.pse-json :deep(textarea) {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
}
.pse-row :deep(.el-input.is-err .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
</style>
