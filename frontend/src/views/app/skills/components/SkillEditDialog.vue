<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, MagicStick, Plus, RefreshLeft } from '@element-plus/icons-vue'
import { appSkillApi } from '@/api/app-skill'
import type { Skill } from '@/api/types'
import { parseIdList, categoryOptions, typeMeta } from '../skillMeta'

/** 新建/编辑技能弹窗：自管表单状态，保存成功后 emit saved */
const props = defineProps<{
  toolOptions: { id: number; name: string }[]
  datasetOptions: { id: number; name: string }[]
  /** 打开编辑时预载工具/数据集映射 */
  ensureMaps: () => void
}>()

const emit = defineEmits<{ saved: [] }>()

/** 模板中用于展示字面量花括号占位（避免 {{ }} 嵌套被编译器误解析） */
const M_OPEN = '{{'
const M_CLOSE = '}}'
const VAR_SAMPLE = '{{var}}'

const dialogVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const form = reactive({
  name: '',
  code: '',
  icon: '',
  category: 'general',
  type: 'instruction' as Skill['type'],
  description: '',
  content: '',
  toolIds: [] as number[],
  datasetIds: [] as number[],
  status: 1
})

/** 变量定义（结构化编辑，保存时转为 JSON 字符串） */
const varItems = ref<{ name: string; desc: string }[]>([])

/** 从正文提取 {{var}}，合并进变量定义 */
function extractVars() {
  const matches = Array.from(form.content.matchAll(/\{\{\s*([\w-]+)\s*\}\}/g))
  if (matches.length === 0) {
    ElMessage.info('正文中未发现变量占位符')
    return
  }
  const seen = new Set(varItems.value.map((v) => v.name))
  const added: string[] = []
  for (const m of matches) {
    const name = m[1]
    if (!seen.has(name)) {
      seen.add(name)
      varItems.value.push({ name, desc: '' })
      added.push(name)
    }
  }
  if (added.length) ElMessage.success(`已补充变量：${added.join('、')}`)
  else ElMessage.info('变量已全部在列表中')
}

/** 插入变量占位到正文末尾 */
function appendVar(item: { name: string }) {
  const base = form.content
  const sep = base && !/\s$/.test(base) ? ' ' : ''
  form.content = base + sep + `{{${item.name}}}`
}

/** 由名称推断 code（小写 kebab-case），无 ASCII 时降级为带时间戳的标识 */
function guessCode() {
  const ascii = form.name
    .replace(/[^a-zA-Z0-9\s_-]/g, ' ')
    .trim()
    .toLowerCase()
  const kebab = ascii.replace(/\s+/g, '-').replace(/-+/g, '-')
  form.code = kebab || `skill-${Date.now().toString(36)}`
}

function resetForm() {
  editId.value = null
  form.name = ''
  form.code = ''
  form.icon = ''
  form.category = 'general'
  form.type = 'instruction'
  form.description = ''
  form.content = ''
  form.toolIds = []
  form.datasetIds = []
  form.status = 1
  varItems.value = []
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Skill) {
  resetForm()
  editId.value = row.id
  form.name = row.name
  form.code = row.code
  form.icon = row.icon || ''
  form.category = row.category || 'general'
  form.type = row.type
  form.description = row.description || ''
  form.content = row.content
  form.toolIds = parseIdList(row.toolIds)
  form.datasetIds = parseIdList(row.datasetIds)
  form.status = row.status ?? 1
  try {
    const arr = row.variables ? JSON.parse(row.variables) : []
    varItems.value = Array.isArray(arr)
      ? arr.map((v: { name?: string; desc?: string }) => ({
          name: v?.name || '',
          desc: v?.desc || ''
        }))
      : []
  } catch {
    varItems.value = []
  }
  dialogVisible.value = true
  nextTick(() => props.ensureMaps())
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入技能名称')
  if (!form.code.trim()) return ElMessage.warning('请输入技能标识（code）')
  if (!/^[a-z][a-z0-9-]*$/.test(form.code.trim())) {
    return ElMessage.warning('技能标识需为小写字母开头的 kebab-case（如 content-review）')
  }
  if (!form.content.trim()) return ElMessage.warning('请输入技能指令正文')
  if (form.type === 'tool' && form.toolIds.length === 0) {
    return ElMessage.warning('工具复合型技能至少需要选择一个依赖工具')
  }
  if (form.type === 'knowledge' && form.datasetIds.length === 0) {
    return ElMessage.warning('知识技能型至少需要选择一个参考数据集')
  }
  const defs = varItems.value
    .filter((v) => v.name.trim())
    .map((v) => ({ name: v.name.trim(), desc: v.desc.trim() || undefined }))
  saving.value = true
  try {
    const payload: Partial<Skill> = {
      name: form.name.trim(),
      code: form.code.trim(),
      description: form.description.trim() || undefined,
      category: form.category,
      type: form.type,
      icon: form.icon.trim() || undefined,
      content: form.content.trim(),
      variables: defs.length ? JSON.stringify(defs) : undefined,
      status: form.status
    }
    if (form.type === 'tool') {
      payload.toolIds = JSON.stringify(form.toolIds)
      payload.datasetIds = undefined
    } else if (form.type === 'knowledge') {
      payload.datasetIds = JSON.stringify(form.datasetIds)
      payload.toolIds = undefined
    } else {
      payload.toolIds = undefined
      payload.datasetIds = undefined
    }
    if (editId.value) {
      await appSkillApi.update(editId.value, payload)
      ElMessage.success('技能已更新')
    } else {
      await appSkillApi.create(payload)
      ElMessage.success('技能已创建')
    }
    dialogVisible.value = false
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="editId == null ? '新建技能' : '编辑技能'"
    width="820px"
    :close-on-click-modal="false"
    top="6vh"
  >
    <div class="dialog-body">
      <div class="form-grid form-grid-2">
        <div class="field-group">
          <label>技能名称 *</label>
          <el-input
            v-model="form.name"
            maxlength="64"
            placeholder="例如：内容安全审核"
            @blur="!form.code && guessCode()"
          />
        </div>
        <div class="field-group">
          <label>技能标识 code *</label>
          <el-input
            v-model="form.code"
            maxlength="64"
            placeholder="小写 kebab-case，如 content-review"
          >
            <template #append>
              <el-button :icon="MagicStick" @click="guessCode">生成</el-button>
            </template>
          </el-input>
        </div>
      </div>

      <div class="form-grid form-grid-3">
        <div class="field-group">
          <label>分类</label>
          <el-select v-model="form.category" style="width: 100%">
            <el-option
              v-for="c in categoryOptions"
              :key="c.value"
              :label="c.label"
              :value="c.value"
            >
              <span>{{ c.label }}</span>
              <span class="opt-desc">{{ c.desc }}</span>
            </el-option>
          </el-select>
        </div>
        <div class="field-group">
          <label>类型</label>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="指令型（注入指令增强行为）" value="instruction" />
            <el-option label="工具复合型（编排多个工具）" value="tool" />
            <el-option label="知识技能型（基于知识库问答）" value="knowledge" />
          </el-select>
        </div>
        <div class="field-group">
          <label>图标（emoji）</label>
          <el-input v-model="form.icon" maxlength="4" :placeholder="typeMeta[form.type]?.icon" />
        </div>
      </div>

      <div class="field-group">
        <label>技能描述</label>
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="256"
          placeholder="说明何时使用该技能、解决什么问题（运行期用于按需匹配）"
        />
      </div>

      <div v-if="form.type === 'tool'" class="field-group">
        <label>依赖工具 *</label>
        <el-select
          v-model="form.toolIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          style="width: 100%"
          placeholder="选择该技能可调用的工具"
        >
          <el-option v-for="t in toolOptions" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </div>
      <div v-if="form.type === 'knowledge'" class="field-group">
        <label>参考数据集 *</label>
        <el-select
          v-model="form.datasetIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          style="width: 100%"
          placeholder="选择该技能检索的知识库数据集"
        >
          <el-option v-for="d in datasetOptions" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
      </div>

      <div class="field-group">
        <div class="label-row">
          <label>技能指令正文（Markdown，支持 {{ VAR_SAMPLE }} 占位）*</label>
          <el-button link type="primary" size="small" :icon="RefreshLeft" @click="extractVars"
            >从正文提取变量</el-button
          >
        </div>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="7"
          maxlength="10000"
          show-word-limit
          :placeholder="`编写可复用的指令正文，使用 ${VAR_SAMPLE} 引用下方定义的变量，例如：请按 {{tone}} 的语气，审核以下内容并给出通过/拦截结论。`"
        />
        <div v-if="varItems.length" class="var-chips">
          <span class="var-title">变量占位（点击插入正文）</span>
          <span v-for="(v, i) in varItems" :key="i" class="var-chip" @click="appendVar(v)"
            >{{ M_OPEN }}{{ v.name }}{{ M_CLOSE }}</span
          >
        </div>
      </div>

      <div class="field-group">
        <div class="label-row">
          <label>变量定义</label>
          <el-button
            link
            type="primary"
            size="small"
            :icon="Plus"
            @click="varItems.push({ name: '', desc: '' })"
            >添加变量</el-button
          >
        </div>
        <div v-if="varItems.length" class="var-table">
          <div v-for="(v, i) in varItems" :key="i" class="var-row">
            <el-input
              v-model="v.name"
              maxlength="32"
              :placeholder="`变量名（不带 ${M_OPEN}${M_CLOSE}）`"
            />
            <el-input v-model="v.desc" maxlength="64" placeholder="变量说明（选填）" />
            <el-button link type="danger" :icon="Delete" @click="varItems.splice(i, 1)" />
          </div>
        </div>
        <p v-else class="hint-text">
          暂无变量。技能正文中的 {{ VAR_SAMPLE }} 占位会在保存后自动供应用编排时填写。
        </p>
      </div>
    </div>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" class="btn-gradient" :loading="saving" @click="save"
        >保存</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.form-grid {
  display: grid;
  gap: 14px;
}
.form-grid-2 {
  grid-template-columns: 1.1fr 1fr;
}
.form-grid-3 {
  grid-template-columns: 1fr 1.3fr 0.8fr;
}
.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-group label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-secondary);
}
.label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.opt-desc {
  float: right;
  font-size: 12px;
  color: var(--text-tertiary);
}
.hint-text {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0;
}
.var-table {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.var-row {
  display: grid;
  grid-template-columns: 180px 1fr auto;
  gap: 8px;
  align-items: center;
}
.var-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
.var-title {
  font-size: 11px;
  color: var(--text-tertiary);
}
.var-chip {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--brand-1);
  background: var(--brand-gradient-soft, #f1f2ff);
  border: 1px dashed var(--border-color, #b9c1ff);
  border-radius: 5px;
  padding: 1px 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.var-chip:hover {
  background: var(--brand-1);
  color: #fff;
  border-color: var(--brand-1);
}
</style>
