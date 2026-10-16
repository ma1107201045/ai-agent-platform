<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import {
  Clock,
  CopyDocument,
  Delete,
  MagicStick,
  Plus,
  RefreshLeft,
  Search,
  UploadFilled
} from '@element-plus/icons-vue'
import { appSkillApi } from '@/api/app-skill'
import { appAgentToolApi } from '@/api/app-tool-info.ts'
import { knowledgeApi } from '@/api/knowledge'
import type { Skill, SkillVersion } from '@/api/types'

/** 模板中用于展示字面量花括号占位（避免 {{ }} 嵌套被编译器误解析） */
const M_OPEN = '{{'
const M_CLOSE = '}}'
const VAR_SAMPLE = '{{var}}'

const loading = ref(false)
const list = ref<Skill[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const categoryFilter = ref('')
const typeFilter = ref('')

/* ---------------- 元信息 ---------------- */
const categoryOptions = [
  { value: 'general', label: '通用', desc: '通用能力、综合场景' },
  { value: 'system', label: '系统', desc: '平台内置、系统级能力' },
  { value: 'business', label: '业务', desc: '业务专用能力' },
  { value: 'custom', label: '自定义', desc: '个人自定义能力' }
]
const coverGradients: Record<string, [string, string]> = {
  general: ['#64748b', '#94a3b8'],
  system: ['#d97706', '#f59e0b'],
  business: ['#059669', '#34d399'],
  custom: ['#4f46e5', '#8b5cf6']
}
const coverGradientOf = (c?: string) => {
  const g = (c && coverGradients[c]) || coverGradients.general
  return `linear-gradient(135deg, ${g[0]} 0%, ${g[1]} 100%)`
}
const categoryLabelOf = (c?: string) => {
  const it = categoryOptions.find((x) => x.value === c)
  return it ? it.label : c || ''
}

const typeMeta: Record<
  string,
  { label: string; type: 'primary' | 'success' | 'warning'; icon: string }
> = {
  instruction: { label: '指令型', type: 'primary', icon: '🧠' },
  tool: { label: '工具复合', type: 'warning', icon: '🛠️' },
  knowledge: { label: '知识技能', type: 'success', icon: '📚' }
}
const typeLabelOf = (t?: string) => (t ? typeMeta[t]?.label || t : '')

/* ---------------- 工具 / 数据集名称映射（卡片标签展示与表单下拉） ---------------- */
const toolOptions = ref<{ id: number; name: string }[]>([])
const datasetOptions = ref<{ id: number; name: string }[]>([])
const toolNameOf = (id: number) => toolOptions.value.find((t) => t.id === id)?.name || `#${id}`
const datasetNameOf = (id: number) =>
  datasetOptions.value.find((d) => d.id === id)?.name || `#${id}`

async function loadMaps() {
  try {
    toolOptions.value = (await appAgentToolApi.enabled())
      .filter((t) => t.status === 1)
      .map((t) => ({ id: t.id, name: t.name }))
  } catch {
    toolOptions.value = []
  }
  try {
    const pageData = await knowledgeApi.datasetPage({ page: 1, size: 500 })
    datasetOptions.value = pageData.records
      .filter((d) => d.status === 1)
      .map((d) => ({ id: d.id, name: d.name }))
  } catch {
    datasetOptions.value = []
  }
}

function parseIdList(s?: string): number[] {
  if (!s) return []
  try {
    const arr = JSON.parse(s)
    return Array.isArray(arr) ? arr.map(Number).filter((n) => Number.isFinite(n)) : []
  } catch {
    return []
  }
}

/* ---------------- 列表 ---------------- */
async function load() {
  loading.value = true
  try {
    const data = await appSkillApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      category: categoryFilter.value || undefined,
      type: typeFilter.value || undefined
    })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
/** 切换分类筛选并重查 */
function setCategoryFilter(v: string) {
  categoryFilter.value = v
  search()
}

function search() {
  page.value = 1
  load()
}

function formatTime(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function copyText(text: string, tip = '已复制到剪贴板') {
  if (!text) return
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success(tip))
    .catch(() => ElMessage.warning('复制失败，请手动选择复制'))
}

/* ---------------- 新建 / 编辑 ---------------- */
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
  nextTick(loadMaps)
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
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除 / 启停用 ---------------- */
async function remove(row: Skill) {
  const refs = (await appSkillApi.apps(row.id).catch(() => [])) as { id: number; name: string }[]
  if (refs.length) {
    const names = refs
      .slice(0, 5)
      .map((a) => `「${a.name}」`)
      .join('、')
    ElMessage.warning(
      `技能已被 ${refs.length} 个应用引用（${names}${refs.length > 5 ? ' 等' : ''}），请先解除绑定`
    )
    return
  }
  ElMessageBox.confirm(`确认删除技能「${row.name}」？历史版本将一并清除，不可恢复。`, '删除确认', {
    type: 'error'
  })
    .then(async () => {
      await appSkillApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

async function toggleStatus(row: Skill) {
  const next = row.status === 1 ? 0 : 1
  await appSkillApi.update(row.id, { status: next })
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  load()
}

/* ---------------- 版本历史 / 回退 ---------------- */
const versionVisible = ref(false)
const versions = ref<SkillVersion[]>([])
const currentSkill = ref<Skill | null>(null)
const rolling = ref(false)

async function openVersions(row: Skill) {
  currentSkill.value = row
  versions.value = await appSkillApi.versions(row.id)
  versionVisible.value = true
}

async function rollback(v: SkillVersion) {
  if (!currentSkill.value) return
  ElMessageBox.confirm(
    `确认将「${currentSkill.value.name}」回退到 v${v.version}？回退会生成一条新的历史版本用于留痕。`,
    '回退确认',
    { type: 'warning' }
  )
    .then(async () => {
      rolling.value = true
      try {
        const updated = await appSkillApi.rollback(currentSkill.value!.id, v.version)
        ElMessage.success(`已回退到 v${v.version}，当前版本 v${updated.version}`)
        versionVisible.value = false
        load()
      } finally {
        rolling.value = false
      }
    })
    .catch(() => {})
}

/* ---------------- 导入 / 导出 ---------------- */
async function exportSkill(row: Skill) {
  const data = await appSkillApi.exportSkill(row.id)
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `skill-${data.code || row.id}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('技能已导出')
}

const importInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
function pickImportFile() {
  importInput.value?.click()
}
async function onImportFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  const instance = ElLoading.service({ text: '正在解析并导入…', lock: true })
  try {
    const text = await file.text()
    const data = JSON.parse(text) as Partial<Skill>
    if (!data.name || !data.content)
      throw new Error('文件缺少 name / content 字段，不是有效的技能 JSON')
    const skill = await appSkillApi.importSkill(data)
    ElMessage.success(`技能「${skill.name}」导入成功，当前版本 v${skill.version}`)
    load()
  } catch (err) {
    ElMessage.error((err as Error).message || '导入失败，请检查文件内容')
  } finally {
    importing.value = false
    instance.close()
  }
}

onMounted(() => {
  load()
  loadMaps()
})
</script>

<template>
  <div class="page-container skill-page">
    <div class="skill-head">
      <div>
        <h2 class="head-title">技能</h2>
        <p class="head-desc">
          将指令、变量与工具/数据集封装为可复用能力包，绑定到应用后自动注入运行循环
        </p>
      </div>
      <div class="head-actions">
        <input
          ref="importInput"
          type="file"
          accept=".json,application/json"
          class="hidden-file"
          @change="onImportFile"
        />
        <el-button :icon="UploadFilled" :loading="importing" @click="pickImportFile"
          >导入</el-button
        >
        <el-button type="primary" class="btn-gradient" :icon="Plus" @click="openCreate"
          >新建技能</el-button
        >
      </div>
    </div>

    <div class="skill-toolbar hover-card">
      <div class="filter-chips">
        <button
          class="chip"
          :class="{ active: categoryFilter === '' }"
          @click="setCategoryFilter('')"
        >
          全部
        </button>
        <button
          v-for="c in categoryOptions"
          :key="c.value"
          class="chip"
          :class="{ active: categoryFilter === c.value }"
          @click="setCategoryFilter(c.value)"
        >
          {{ c.label }}
        </button>
      </div>
      <div class="toolbar-right">
        <el-select
          v-model="typeFilter"
          placeholder="类型"
          clearable
          style="width: 130px"
          @change="search"
        >
          <el-option v-for="(m, key) in typeMeta" :key="key" :label="m.label" :value="key" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索名称 / 标识 / 描述"
          clearable
          style="width: 230px"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <el-button @click="search">搜索</el-button>
      </div>
    </div>

    <div v-loading="loading" class="skill-grid">
      <div v-for="row in list" :key="row.id" class="skill-card hover-card">
        <div class="card-top">
          <div class="card-icon" :style="{ background: coverGradientOf(row.category) }">
            {{ row.icon || typeMeta[row.type]?.icon || '⚡' }}
          </div>
          <div class="card-tags">
            <el-tag size="small" effect="dark" type="info">{{
              categoryLabelOf(row.category)
            }}</el-tag>
            <el-tag size="small" :type="typeMeta[row.type]?.type || 'info'">{{
              typeLabelOf(row.type)
            }}</el-tag>
            <el-switch
              :model-value="row.status === 1"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              class="status-switch"
              @change="toggleStatus(row)"
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
            <span
              v-for="id in parseIdList(row.datasetIds).slice(0, 3)"
              :key="id"
              class="dep-chip"
              >{{ datasetNameOf(id) }}</span
            >
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
            <el-button size="small" link type="primary" @click="openVersions(row)">版本</el-button>
            <el-button size="small" link @click="exportSkill(row)">导出</el-button>
            <el-button size="small" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!loading && list.length === 0" class="empty-card hover-card">
      <el-empty description="暂无匹配的技能，点击右上角新建技能" :image-size="90">
        <el-button type="primary" class="btn-gradient" :icon="Plus" @click="openCreate"
          >新建技能</el-button
        >
      </el-empty>
    </div>

    <el-pagination
      v-if="total > size"
      style="margin-top: 16px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      v-model:current-page="page"
      @current-change="load"
    />

    <!-- 新建 / 编辑技能 -->
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

    <!-- 版本历史 -->
    <el-drawer
      v-model="versionVisible"
      :title="currentSkill ? `版本历史 · ${currentSkill.name}` : '版本历史'"
      size="560px"
    >
      <div v-loading="rolling" class="version-body">
        <el-timeline v-if="versions.length">
          <el-timeline-item
            v-for="v in versions"
            :key="v.id"
            :type="v.version === currentSkill?.version ? 'primary' : 'info'"
            :hollow="v.version === currentSkill?.version"
            :timestamp="formatTime(v.createTime)"
          >
            <div class="version-item">
              <div class="version-title">
                <el-tag
                  size="small"
                  :type="v.version === currentSkill?.version ? 'primary' : 'info'"
                  >v{{ v.version }}</el-tag
                >
                <span v-if="v.version === currentSkill?.version" class="current-tag">当前</span>
                <span class="dim-text">{{ v.remark || '内容更新' }}</span>
              </div>
              <pre class="version-content">{{ v.content }}</pre>
              <div class="version-actions">
                <el-button
                  size="small"
                  link
                  :icon="CopyDocument"
                  @click="copyText(v.content, `v${v.version} 正文已复制`)"
                  >复制</el-button
                >
                <el-button
                  v-if="v.version !== currentSkill?.version"
                  size="small"
                  link
                  type="warning"
                  :icon="RefreshLeft"
                  @click="rollback(v)"
                  >回退到此版本</el-button
                >
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无版本记录" :image-size="80" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.skill-page {
  max-width: 1440px;
  margin: 0 auto;
}
.skill-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.head-title {
  font-size: 22px;
  font-weight: 700;
}
.head-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-tertiary);
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hidden-file {
  display: none;
}

.skill-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 18px;
}
.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1 1 auto;
  min-width: 0;
}
.chip {
  border: none;
  padding: 5px 12px;
  border-radius: 16px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition:
    background-color 0.15s,
    color 0.15s;
}
.chip:hover {
  background: var(--hover-bg);
  color: var(--text-primary);
}
.chip.active {
  background: var(--brand-1);
  color: #fff;
  font-weight: 600;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
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
/* 状态开关已合并到右上角 card-tags 中，不再使用 .card-status */
.status-switch {
  flex-shrink: 0;
}
.status-switch :deep(.el-switch.is-checked) .el-switch__core {
  background: var(--brand-1);
}
.status-switch :deep(.el-switch__core) {
  border-color: var(--border-color);
}
.empty-card {
  padding: 30px;
  display: flex;
  justify-content: center;
}

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

.version-body {
  padding: 0 4px;
}
.version-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}
.version-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.current-tag {
  font-size: 11px;
  font-weight: 600;
  color: var(--brand-1);
}
.dim-text {
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.version-content {
  margin: 8px 0 4px;
  padding: 8px 10px;
  background: var(--bg-muted, #f8fafc);
  border-radius: var(--radius-sm);
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 180px;
  overflow: auto;
}
.version-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
