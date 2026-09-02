<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Clock,
  Collection,
  CopyDocument,
  Delete,
  Edit,
  MagicStick,
  Plus,
  Promotion,
  RefreshLeft,
  Search,
  VideoPlay
} from '@element-plus/icons-vue'
import { appPromptApi } from '@/api/app-prompt'
import { modelApi } from '@/api/model'
import type { ChatModelInfo, PromptTemplate, PromptTemplateVersion } from '@/api/types'

/** {{ 与 }} 字面量：避免与 Vue 插值冲突 */
const VAR_L = '{{'
const VAR_R = '}}'
const varSample = (n: string) => VAR_L + n + VAR_R

/** 变量定义 JSON 输入提示：内容含双引号，抽为常量避免在模板双引号属性内转义出错 */
const VARS_PLACEHOLDER = '[{"name":"role","desc":"角色描述"}]'

const loading = ref(false)
const list = ref<PromptTemplate[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const categoryFilter = ref('')

const categoryOptions = [
  { value: 'general', label: '通用', desc: '通用助手、综合场景' },
  { value: 'system', label: '系统', desc: '平台内置、系统级模板' },
  { value: 'business', label: '业务', desc: '业务专用模板' },
  { value: 'custom', label: '自定义', desc: '个人自定义模板' }
]
const categoryLabels: Record<string, string> = Object.fromEntries(
  categoryOptions.map((c) => [c.value, c.label])
)
const categoryColor: Record<string, string> = {
  general: '#64748b',
  system: '#f59e0b',
  business: '#10b981',
  custom: '#5b6cff'
}

async function load() {
  loading.value = true
  try {
    const data = await appPromptApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      category: categoryFilter.value || undefined
    })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

/** 格式化时间 */
function formatTime(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 复制文本到剪贴板 */
function copyText(text: string, tip = '已复制到剪贴板') {
  if (!text) return
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success(tip))
    .catch(() => ElMessage.warning('复制失败，请手动选择复制'))
}

/** 复制当前查看版本的正文字段（content / variables） */
function copyVersionField(field: 'content' | 'variables') {
  const v = versionView.value
  if (!v) return
  if (field === 'content') {
    copyText(v.content, '模板正文已复制')
  } else if (v.variables) {
    copyText(v.variables, '变量定义已复制')
  }
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const form = reactive({
  name: '',
  description: '',
  category: 'general',
  content: '',
  variables: '',
  status: 1
})

function openCreate() {
  editId.value = null
  form.name = ''
  form.description = ''
  form.category = 'general'
  form.content = ''
  form.variables = ''
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: PromptTemplate) {
  editId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.category = row.category || 'general'
  form.content = row.content
  form.variables = row.variables || ''
  form.status = row.status ?? 1
  dialogVisible.value = true
}

/** 从正文提取 {{var}} 生成变量定义 JSON */
async function extractVars() {
  if (!form.content.trim()) {
    ElMessage.warning('请先填写模板正文')
    return
  }
  const names = await appPromptApi.extractVariables(form.content)
  if (names.length === 0) {
    ElMessage.info('正文中未发现变量占位符')
    return
  }
  let parsed: { name: string; desc?: string }[] = []
  try {
    parsed = form.variables ? JSON.parse(form.variables) : []
  } catch {
    parsed = []
  }
  const seen = new Set(parsed.map((v) => v.name))
  for (const n of names) {
    if (!seen.has(n)) {
      parsed.push({ name: n, desc: '' })
    }
  }
  form.variables = JSON.stringify(parsed, null, 2)
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请输入模板正文')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description,
      category: form.category,
      content: form.content,
      variables: form.variables || undefined,
      status: form.status
    }
    if (editId.value) {
      await appPromptApi.update(editId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await appPromptApi.create(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/* ---------------- 删除 / 启停用 ---------------- */
function remove(row: PromptTemplate) {
  ElMessageBox.confirm(
    `确认删除「${row.name}」？历史版本将一并清除，不可恢复。`,
    '删除确认',
    { type: 'error' }
  )
    .then(async () => {
      await appPromptApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

async function toggleStatus(row: PromptTemplate) {
  const next = row.status === 1 ? 0 : 1
  await appPromptApi.update(row.id, { ...row, status: next })
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  load()
}

/* ---------------- 版本历史 / 回退 ---------------- */
const versionVisible = ref(false)
const versions = ref<PromptTemplateVersion[]>([])
const currentTemplate = ref<PromptTemplate | null>(null)
const versionView = ref<PromptTemplateVersion | null>(null)
const versionViewVisible = ref(false)
const rolling = ref(false)

async function openVersions(row: PromptTemplate) {
  currentTemplate.value = row
  versions.value = await appPromptApi.versions(row.id)
  versionVisible.value = true
}

function viewVersion(v: PromptTemplateVersion) {
  versionView.value = v
  versionViewVisible.value = true
}

async function rollback(v: PromptTemplateVersion) {
  if (!currentTemplate.value) return
  ElMessageBox.confirm(
    `确认将「${currentTemplate.value.name}」回退到 v${v.version}？回退会生成一条新的历史版本用于留痕。`,
    '回退确认',
    { type: 'warning' }
  )
    .then(async () => {
      rolling.value = true
      try {
        const updated = await appPromptApi.rollback(currentTemplate.value!.id, v.version)
        ElMessage.success(`已回退到 v${v.version}，当前版本 v${updated.version}`)
        versionVisible.value = false
        load()
      } finally {
        rolling.value = false
      }
    })
    .catch(() => {})
}

/* ---------------- 调试试跑 ---------------- */
const debugVisible = ref(false)
const debugTemplate = ref<PromptTemplate | null>(null)
const debugForm = reactive({
  content: '',
  variableValues: {} as Record<string, string>,
  varNames: [] as string[],
  question: '用一句话总结这段内容，并给出建议。',
  modelId: 0
})
const chatModels = ref<ChatModelInfo[]>([])
const rendered = ref('')
const testing = ref(false)
const testResult = ref('')
/** 变量名 → 说明（来自模板 variables 定义，作为试跑输入框提示） */
const debugVarDesc = ref<Record<string, string>>({})

async function openDebug(row: PromptTemplate) {
  debugTemplate.value = row
  debugForm.content = row.content
  debugForm.question = '用一句话总结这段内容，并给出建议。'
  debugForm.modelId = 0
  testResult.value = ''
  rendered.value = ''
  debugVarDesc.value = {}
  const names: string[] = []
  const vals: Record<string, string> = {}
  try {
    const defs: { name: string; desc?: string }[] = row.variables ? JSON.parse(row.variables) : []
    for (const d of defs) {
      if (d.name) {
        names.push(d.name)
        vals[d.name] = ''
      }
    }
  } catch {
    // 忽略非法 JSON
  }
  debugForm.varNames = names
  debugForm.variableValues = vals
  // 从后端解析变量说明（服务端宽松解析，非法定义返回空 map）
  if (row.variables) {
    try {
      debugVarDesc.value = await appPromptApi.parseVariables(row.variables)
    } catch {
      // 解析失败不阻塞调试
    }
  }
  debugVisible.value = true
  loadChatModels()
}

async function loadChatModels() {
  try {
    chatModels.value = await modelApi.chatModels()
    if (chatModels.value.length > 0) {
      debugForm.modelId = chatModels.value[0].id
    }
  } catch {
    // 无可用模型时不阻塞调试
  }
}

function ensureVariableValue(name: string) {
  if (!(name in debugForm.variableValues)) {
    debugForm.variableValues[name] = ''
  }
}

function appendVar(name: string) {
  if (!debugForm.varNames.includes(name)) {
    debugForm.varNames.push(name)
    ensureVariableValue(name)
  }
}

/** 提取调试内容里的 {{var}} 并自动补全变量 */
async function refreshVars() {
  if (!debugForm.content.trim()) return
  const names = await appPromptApi.extractVariables(debugForm.content)
  for (const n of names) {
    appendVar(n)
  }
}

async function doRender() {
  if (!debugForm.content.trim()) {
    ElMessage.warning('请输入要调试的正文')
    return
  }
  await refreshVars()
  const payload: Record<string, string> = {}
  for (const n of debugForm.varNames) {
    payload[n] = debugForm.variableValues[n] ?? ''
  }
  rendered.value = await appPromptApi.render(debugForm.content, payload)
}

async function runTest() {
  if (!rendered.value) {
    ElMessage.warning('请先点击「渲染预览」生成完整提示词')
    return
  }
  if (!debugForm.modelId) {
    ElMessage.warning('请选择测试模型')
    return
  }
  testing.value = true
  testResult.value = ''
  try {
    const resp = await modelApi.chat({
      modelId: debugForm.modelId,
      systemPrompt: rendered.value,
      prompt: debugForm.question || undefined
    })
    testResult.value = resp.content || '(模型未返回内容)'
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container prompts-page">
    <div class="prompts-head">
      <div>
        <h2 class="head-title">提示词库</h2>
        <p class="head-desc">共 {{ total }} 个模板 · 复用提示词模板，版本留痕与在线调试</p>
      </div>
      <el-button type="primary" class="btn-gradient create-btn" @click="openCreate">
        <el-icon><Plus /></el-icon>&nbsp;新建模板
      </el-button>
    </div>

    <!-- 筛选工具栏 -->
    <div class="prompts-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索模板名称 / 描述"
        clearable
        class="toolbar-search"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="categoryFilter" placeholder="全部分类" clearable class="toolbar-category" @change="search">
        <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
    </div>

    <!-- 模板卡片 -->
    <div v-loading="loading" class="prompt-grid">
      <div v-for="row in list" :key="row.id" class="prompt-card hover-card">
        <div class="prompt-top">
          <div class="prompt-icon" :style="{ '--prompt-c': categoryColor[row.category] || '#64748b' }">
            <el-icon :size="18"><Collection /></el-icon>
          </div>
          <div class="prompt-title-row">
            <span class="prompt-name">{{ row.name }}</span>
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </div>
          <el-tag size="small" class="category-tag" effect="plain">{{ categoryLabels[row.category] || row.category }}</el-tag>
        </div>

        <p class="prompt-desc">{{ row.description || '暂无描述' }}</p>

        <div class="prompt-meta">
          <span class="prompt-version" title="当前版本">
            <el-icon :size="12"><RefreshLeft /></el-icon>&nbsp;v{{ row.version }}
          </span>
          <span class="prompt-time">{{ formatTime(row.updateTime) }}</span>
        </div>

        <div class="prompt-actions">
          <el-tooltip content="编辑模板" placement="top">
            <div class="action-btn" @click="openEdit(row)">
              <el-icon :size="15"><Edit /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="版本历史" placement="top">
            <div class="action-btn" @click="openVersions(row)">
              <el-icon :size="15"><Clock /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="调试试跑" placement="top">
            <div class="action-btn" @click="openDebug(row)">
              <el-icon :size="15"><VideoPlay /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip :content="row.status === 1 ? '禁用' : '启用'" placement="top">
            <div class="action-btn" @click="toggleStatus(row)">
              <el-icon :size="15"><Promotion /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <div class="action-btn danger" @click="remove(row)">
              <el-icon :size="15"><Delete /></el-icon>
            </div>
          </el-tooltip>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && list.length === 0" class="empty-state">
        <div class="empty-icon">
          <el-icon :size="40"><MagicStick /></el-icon>
        </div>
        <p>{{ keyword || categoryFilter ? '没有匹配的模板，换个条件试试' : '还没有提示词模板，创建第一个吧' }}</p>
        <el-button type="primary" class="btn-gradient" @click="openCreate">立即创建</el-button>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > size" class="prompts-pagination">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editId ? '编辑模板' : '新建模板'"
      width="720px"
      :close-on-click-modal="false"
      class="edit-dialog"
    >
      <div class="form-body">
        <div class="field-row">
          <div class="field-group grow">
            <div class="field-label">模板名称 <span class="req">*</span></div>
            <el-input v-model="form.name" placeholder="例如：智能客服开场白" maxlength="128" show-word-limit />
          </div>
          <div class="field-group" style="width: 180px">
            <div class="field-label">分类</div>
            <el-select v-model="form.category" style="width: 100%">
              <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </div>
        </div>

        <div class="field-group">
          <div class="field-label">模板描述</div>
          <el-input v-model="form.description" placeholder="说明模板的用途与适用场景（可选）" maxlength="512" />
        </div>

        <div class="field-group">
          <div class="field-label">
            模板正文 <span class="req">*</span>
            <span class="tip">支持 {{ VAR_L }}变量名{{ VAR_R }} 占位，试跑时自动替换</span>
          </div>
          <el-input v-model="form.content" type="textarea" :rows="8" :placeholder="'你是' + varSample('role') + '，请帮我…'" />
        </div>

        <div class="field-group">
          <div class="field-label">
            变量定义
            <el-button link type="primary" size="small" @click="extractVars">从正文提取</el-button>
            <span class="tip">JSON 数组，可留空</span>
          </div>
          <el-input
            v-model="form.variables"
            type="textarea"
            :rows="4"
            :placeholder="VARS_PLACEHOLDER"
            class="var-input"
          />
        </div>

        <div class="field-group status-row">
          <span class="field-label">状态</span>
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史抽屉 -->
    <el-drawer v-model="versionVisible" title="版本历史" size="480px">
      <template v-if="currentTemplate">
        <div class="version-summary">
          <span class="vs-name">{{ currentTemplate.name }}</span>
          <el-tag size="small" type="primary" effect="light">当前 v{{ currentTemplate.version }}</el-tag>
        </div>
        <div v-if="versions.length === 0" class="drawer-empty">暂无历史版本</div>
        <el-timeline v-else class="version-timeline">
          <el-timeline-item
            v-for="v in versions"
            :key="v.id"
            :timestamp="formatTime(v.createTime)"
            placement="top"
            :type="v.version === currentTemplate.version ? 'primary' : 'info'"
            :hollow="v.version !== currentTemplate.version"
          >
            <div class="version-item">
              <div class="vi-head">
                <span class="vi-version">v{{ v.version }}</span>
                <span class="vi-remark">{{ v.remark || '版本更新' }}</span>
                <el-tag v-if="v.version === currentTemplate.version" size="small" type="success" effect="light">当前</el-tag>
              </div>
              <div class="vi-actions">
                <el-button link type="primary" size="small" @click="viewVersion(v)">查看内容</el-button>
                <el-button
                  v-if="v.version !== currentTemplate.version"
                  link
                  type="warning"
                  size="small"
                  :loading="rolling"
                  @click="rollback(v)"
                >
                  回退到此版本
                </el-button>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <!-- 版本内容查看 -->
    <el-dialog v-model="versionViewVisible" title="版本内容" width="680px">
      <template v-if="versionView">
        <div class="view-meta">
          <el-tag size="small" type="primary" effect="light">v{{ versionView.version }}</el-tag>
          <span class="view-time">{{ formatTime(versionView.createTime) }} · {{ versionView.remark || '版本更新' }}</span>
        </div>
        <div class="view-block">
          <div class="view-block-head">
            <span>模板正文</span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              @click="copyVersionField('content')"
            >
              复制正文
            </el-button>
          </div>
          <pre class="content-pre">{{ versionView.content }}</pre>
        </div>
        <div v-if="versionView.variables" class="view-block">
          <div class="view-block-head">
            <span>变量定义</span>
            <el-button
              link
              type="primary"
              size="small"
              :icon="CopyDocument"
              @click="copyVersionField('variables')"
            >
              复制变量
            </el-button>
          </div>
          <pre class="content-pre var-pre">{{ versionView.variables }}</pre>
        </div>
      </template>
    </el-dialog>

    <!-- 调试试跑对话框 -->
    <el-dialog
      v-model="debugVisible"
      title="调试试跑"
      width="880px"
      :close-on-click-modal="false"
      top="4vh"
      class="debug-dialog"
    >
      <template v-if="debugTemplate">
        <div class="debug-head">
          <span class="dh-name">{{ debugTemplate.name }}</span>
          <span class="dh-version">当前 v{{ debugTemplate.version }} · 试跑修改不会影响模板</span>
        </div>

        <div class="debug-grid">
          <!-- 左：编辑正文 -->
          <div class="debug-col">
            <div class="col-title">1. 提示词正文（可临时修改）</div>
            <el-input
              v-model="debugForm.content"
              type="textarea"
              :rows="11"
              :placeholder="'支持' + varSample('var') + '变量占位'"
              class="debug-content"
              @blur="refreshVars"
            />

            <div class="col-title var-title">2. 变量值</div>
            <div v-if="debugForm.varNames.length === 0" class="no-var">
              未发现变量，可直接点击「渲染预览」测试固定写法
            </div>
            <div v-for="name in debugForm.varNames" :key="name" class="var-row">
              <el-tooltip :content="debugVarDesc[name] || name" placement="top" :show-after="200">
                <span class="var-name">{{ varSample(name) }}</span>
              </el-tooltip>
              <el-input
                v-model="debugForm.variableValues[name]"
                size="small"
                :placeholder="debugVarDesc[name] || '请输入变量值'"
                @input="doRender"
              />
            </div>

            <el-button type="primary" plain class="render-btn" @click="doRender">渲染预览</el-button>
            <div class="col-title render-title">
              <span>渲染结果</span>
              <el-button v-if="rendered" link type="primary" size="small" :icon="CopyDocument" @click="copyText(rendered, '渲染结果已复制')">
                复制
              </el-button>
            </div>
            <el-input
              :model-value="rendered"
              type="textarea"
              :rows="5"
              readonly
              class="rendered-box"
              placeholder="渲染后的完整提示词将显示在这里"
            />
          </div>

          <!-- 右：模型测试 -->
          <div class="debug-col">
            <div class="col-title">3. 模型测试</div>
            <div class="model-row">
              <el-select v-model="debugForm.modelId" placeholder="选择模型" class="model-select">
                <el-option
                  v-for="m in chatModels"
                  :key="m.id"
                  :label="`${m.providerName} · ${m.modelName}`"
                  :value="m.id"
                />
              </el-select>
              <el-button type="primary" class="btn-gradient" :loading="testing" @click="runTest">运行</el-button>
            </div>
            <div class="col-title">测试问题</div>
            <el-input
              v-model="debugForm.question"
              type="textarea"
              :rows="3"
              placeholder="输入要发送给模型的问题"
            />
            <div class="col-title">模型回复</div>
            <div v-loading="testing" class="result-box">
              <pre v-if="testResult">{{ testResult }}</pre>
              <span v-else class="result-placeholder">运行后在此查看模型回复，可反复修改正文/变量对比不同写法效果</span>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.prompts-page {
  max-width: 1280px;
  margin: 0 auto;
}
.prompts-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
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
.create-btn {
  height: 38px;
}

/* ---------- 工具栏 ---------- */
.prompts-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}
.toolbar-search {
  width: 280px;
}
.toolbar-category {
  width: 160px;
}

/* ---------- 卡片 ---------- */
.prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  min-height: 200px;
}
.prompt-card {
  position: relative;
  padding: 16px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.prompt-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.prompt-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--brand-gradient-soft);
  color: var(--prompt-c, var(--brand-1));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.prompt-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.prompt-name {
  font-size: 14.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.category-tag {
  flex-shrink: 0;
}
.prompt-desc {
  font-size: 12.5px;
  color: var(--text-tertiary);
  min-height: 38px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.6;
}
.prompt-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}
.prompt-version {
  font-size: 12px;
  color: var(--brand-1);
  background: var(--brand-gradient-soft);
  padding: 2px 8px;
  border-radius: 8px;
  display: flex;
  align-items: center;
}
.prompt-time {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

/* hover 浮现操作 */
.prompt-actions {
  position: absolute;
  right: 12px;
  top: 12px;
  display: flex;
  gap: 6px;
  background: color-mix(in srgb, var(--bg-card) 92%, transparent);
  padding: 6px;
  border-radius: 10px;
  opacity: 0;
  transform: translateY(-4px);
  transition: all 0.22s ease;
}
.prompt-card:hover .prompt-actions {
  opacity: 1;
  transform: translateY(0);
}
.action-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  transition: all 0.18s ease;
}
.action-btn:hover {
  background: var(--brand-gradient);
  color: #fff;
}
.action-btn.danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.action-btn.danger:hover {
  background: #ef4444;
  color: #fff;
}

/* ---------- 空状态 / 分页 ---------- */
.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
}
.empty-icon {
  width: 84px;
  height: 84px;
  border-radius: 24px;
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-state p {
  color: var(--text-secondary);
  font-size: 14px;
}
.prompts-pagination {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

/* ---------- 编辑对话框 ---------- */
.edit-dialog .form-body {
  padding: 0 4px;
}
.field-row {
  display: flex;
  gap: 12px;
}
.field-group {
  margin-bottom: 14px;
}
.field-group.grow {
  flex: 1;
}
.field-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-primary);
}
.req {
  color: #ef4444;
}
.tip {
  font-weight: 400;
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.var-input {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 0;
}
.status-row .field-label {
  margin-bottom: 0;
}

/* ---------- 版本抽屉 ---------- */
.version-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.vs-name {
  font-size: 15px;
  font-weight: 600;
}
.drawer-empty {
  text-align: center;
  color: var(--text-tertiary);
  padding: 40px 0;
}
.version-timeline {
  padding-left: 4px;
}
.version-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.vi-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.vi-version {
  font-weight: 700;
  color: var(--brand-1);
}
.vi-remark {
  font-size: 12.5px;
  color: var(--text-secondary);
}
.vi-actions {
  display: flex;
  gap: 12px;
}

/* ---------- 版本内容查看 ---------- */
.view-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.view-time {
  font-size: 12px;
  color: var(--text-tertiary);
}
.content-pre {
  background: var(--bg-fill, #f5f7fa);
  border-radius: 10px;
  padding: 14px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 60vh;
  overflow: auto;
}
.view-block {
  margin-bottom: 14px;
}
.view-block:last-child {
  margin-bottom: 0;
}
.view-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.var-pre {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--text-secondary);
}
.render-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* ---------- 调试试跑 ---------- */
.debug-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.dh-name {
  font-size: 15px;
  font-weight: 600;
}
.dh-version {
  font-size: 12px;
  color: var(--text-tertiary);
}
.debug-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.debug-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.col-title {
  font-size: 13px;
  font-weight: 600;
  margin: 12px 0 6px;
  color: var(--text-primary);
}
.col-title:first-child {
  margin-top: 0;
}
.var-title {
  margin-top: 14px;
}
.no-var {
  font-size: 12px;
  color: var(--text-tertiary);
  background: var(--bg-fill, #f5f7fa);
  padding: 8px 12px;
  border-radius: 8px;
}
.var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.var-name {
  font-size: 12px;
  color: var(--brand-1);
  background: var(--brand-gradient-soft);
  padding: 4px 8px;
  border-radius: 6px;
  white-space: nowrap;
  font-family: var(--font-mono, monospace);
}
.render-btn {
  margin-top: 10px;
}
.rendered-box {
  font-size: 12px;
}
.model-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.model-select {
  flex: 1;
}
.result-box {
  min-height: 180px;
  background: var(--bg-fill, #f5f7fa);
  border-radius: 10px;
  padding: 12px;
  position: relative;
}
.result-box pre {
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.result-placeholder {
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
