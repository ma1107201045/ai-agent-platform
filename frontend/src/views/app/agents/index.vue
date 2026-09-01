<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Delete, Edit, EditPen, Files, MagicStick, Plus, Promotion, Search } from '@element-plus/icons-vue'
import { appAgentApi } from '@/api/app-agent.ts'
import type { AppAgent, AppAgentType } from '@/api/types.ts'

const router = useRouter()
const loading = ref(false)
const list = ref<AppAgent[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const typeFilter = ref('')

const dialogVisible = ref(false)
const creating = ref(false)
/** 创建向导步骤：choose=选择创建方式 template=模板选择 form=填写信息 */
const step = ref<'choose' | 'template' | 'form'>('choose')
const createForm = reactive({
  name: '',
  type: 'chatflow' as AppAgentType,
  description: ''
})

const typeMeta: Record<string, { label: string; desc: string; icon: string; color: string }> = {
  chatflow: { label: '对话流', desc: '多轮对话式智能体，适合客服、助手', icon: '💬', color: '#5b6cff' },
  workflow: { label: '工作流', desc: '流程化编排，按节点依次执行任务', icon: '⚙️', color: '#0ea5e9' },
  agent: { label: '智能体', desc: '自主规划工具调用，处理复杂任务', icon: '🤖', color: '#8b5cf6' }
}

const typeLabels: Record<string, string> = {
  chatflow: '对话流',
  workflow: '工作流',
  agent: '智能体'
}

const coverColors: Record<string, [string, string]> = {
  chatflow: ['#5b6cff', '#8b5cf6'],
  workflow: ['#0ea5e9', '#5b6cff'],
  agent: ['#8b5cf6', '#d946ef']
}

/** 内置场景模板：创建时预填名称/类型/描述 */
interface AppTemplate {
  key: string
  name: string
  type: AppAgentType
  icon: string
  color: [string, string]
  desc: string
  useCase: string
}

const appTemplates: AppTemplate[] = [
  {
    key: 'customer-service',
    name: '智能客服助手',
    type: 'chatflow',
    icon: '💬',
    color: ['#5b6cff', '#8b5cf6'],
    desc: '基于知识库回答客户问题，支持多轮追问与转人工',
    useCase: '电商 / 企业服务'
  },
  {
    key: 'translator',
    name: '多语言翻译助手',
    type: 'chatflow',
    icon: '🌐',
    color: ['#0ea5e9', '#38bdf8'],
    desc: '中英互译与术语润色，支持行业术语定制',
    useCase: '出海业务'
  },
  {
    key: 'content',
    name: '内容创作助手',
    type: 'agent',
    icon: '✍️',
    color: ['#8b5cf6', '#a855f7'],
    desc: '撰写文章 / 文案 / 脚本，自动调用写作工具',
    useCase: '新媒体运营'
  },
  {
    key: 'data-analysis',
    name: '数据分析工作流',
    type: 'workflow',
    icon: '📊',
    color: ['#10b981', '#34d399'],
    desc: '数据接入-清洗-分析-报告一键生成',
    useCase: '经营分析'
  },
  {
    key: 'marketing',
    name: '营销文案助手',
    type: 'chatflow',
    icon: '📣',
    color: ['#f59e0b', '#fbbf24'],
    desc: '生成营销文案与活动创意，可切换文案风格',
    useCase: '市场运营'
  },
  {
    key: 'coding',
    name: '编程助手',
    type: 'agent',
    icon: '👨‍💻',
    color: ['#6366f1', '#818cf8'],
    desc: '代码生成 / 解释 / 重构，调用代码工具链',
    useCase: '研发提效'
  }
]

const dialogWidth = computed(() => (step.value === 'template' ? '760px' : step.value === 'choose' ? '640px' : '520px'))

async function load() {
  loading.value = true
  try {
    const data = await appAgentApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      type: typeFilter.value || undefined
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

/** 格式化时间：ISO 字符串 → YYYY-MM-DD HH:mm */
function formatTime(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function openCreate() {
  createForm.name = ''
  createForm.type = 'chatflow'
  createForm.description = ''
  step.value = 'choose'
  dialogVisible.value = true
}

function goTemplate() {
  step.value = 'template'
}

function goBlank() {
  createForm.name = ''
  createForm.type = 'chatflow'
  createForm.description = ''
  step.value = 'form'
}

function selectTemplate(tpl: AppTemplate) {
  createForm.name = tpl.name
  createForm.type = tpl.type
  createForm.description = tpl.desc
  step.value = 'form'
}

function backToChoose() {
  step.value = 'choose'
}

async function confirmCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入应用名称')
    return
  }
  creating.value = true
  try {
    await appAgentApi.create({
      name: createForm.name.trim(),
      type: createForm.type,
      description: createForm.description
    })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    load()
  } finally {
    creating.value = false
  }
}

function edit(row: AppAgent) {
  router.push(`/app-agents/${row.id}/edit`)
}

function chat(row: AppAgent) {
  router.push(`/app-agents/${row.id}/chat`)
}

function publish(row: AppAgent) {
  // chatflow / workflow 需要有已保存的草稿工作流才能发布；agent 直接发布空 DSL 由运行时按配置执行
  if (row.type !== 'agent' && !row.workflowJson) {
    ElMessageBox.confirm('该应用还没有可发布的工作流，请先到编辑器编排并保存草稿。', '暂无可发布内容', {
      confirmButtonText: '去编排',
      cancelButtonText: '取消',
      type: 'warning'
    })
      .then(() => edit(row))
      .catch(() => {})
    return
  }
  ElMessageBox.confirm(`发布「${row.name}」为新的线上版本？`, '发布确认', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await appAgentApi.publish(row.id, {
        workflowJson: row.workflowJson ?? JSON.stringify({ nodes: [], edges: [] }),
        promptConfig: ''
      })
      ElMessage.success('发布成功')
      load()
    })
    .catch(() => {})
}

function remove(row: AppAgent) {
  ElMessageBox.confirm(`确认删除「${row.name}」？该操作不可恢复。`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'error'
  })
    .then(async () => {
      await appAgentApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

const gradientOf = (row: AppAgent) => {
  const c = coverColors[row.type] || coverColors.chatflow
  return `linear-gradient(135deg, ${c[0]} 0%, ${c[1]} 100%)`
}

onMounted(load)
</script>

<template>
  <div class="page-container apps-page">
    <div class="apps-head">
      <div>
        <h2 class="head-title">智能体应用</h2>
        <p class="head-desc">共 {{ total }} 个应用，点击卡片快速进入编排或对话</p>
      </div>
      <el-button type="primary" class="btn-gradient create-btn" @click="openCreate">
        <el-icon><Plus /></el-icon>&nbsp;新建应用
      </el-button>
    </div>

    <!-- 筛选工具栏 -->
    <div class="apps-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索应用名称"
        clearable
        class="toolbar-search"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="typeFilter" placeholder="全部类型" clearable class="toolbar-type" @change="search">
        <el-option v-for="(meta, key) in typeMeta" :key="key" :label="meta.label" :value="key" />
      </el-select>
    </div>

    <!-- 卡片网格 -->
    <div v-loading="loading" class="app-grid">
      <div v-for="row in list" :key="row.id" class="app-card hover-card">
        <div class="app-cover" :style="{ background: gradientOf(row) }">
          <div class="cover-deco"></div>
          <span class="cover-icon">{{ typeMeta[row.type]?.icon }}</span>
          <div class="cover-type">{{ typeLabels[row.type] }}</div>
        </div>

        <div class="app-body">
          <div class="app-title-row">
            <span class="app-name">{{ row.name }}</span>
            <el-tag v-if="row.status === 1" size="small" type="success" effect="light">已发布</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">草稿</el-tag>
          </div>
          <p class="app-desc">{{ row.description || '暂无描述' }}</p>
          <div class="app-foot">
            <span class="app-time">{{ formatTime(row.updateTime) }}</span>
          </div>
        </div>

        <div class="app-actions">
          <el-tooltip content="编排工作流" placement="top">
            <div class="action-btn" @click="edit(row)">
              <el-icon :size="16"><Edit /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="开始对话" placement="top">
            <div class="action-btn" @click="chat(row)">
              <el-icon :size="16"><ChatDotRound /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="发布版本" placement="top">
            <div class="action-btn" @click="publish(row)">
              <el-icon :size="16"><Promotion /></el-icon>
            </div>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <div class="action-btn danger" @click="remove(row)">
              <el-icon :size="16"><Delete /></el-icon>
            </div>
          </el-tooltip>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && list.length === 0" class="empty-state">
        <div class="empty-icon">
          <el-icon :size="40"><MagicStick /></el-icon>
        </div>
        <p>{{ keyword || typeFilter ? '没有匹配的应用，换个条件试试' : '还没有应用，创建你的第一个智能体吧' }}</p>
        <el-button type="primary" class="btn-gradient" @click="openCreate">立即创建</el-button>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > size" class="apps-pagination">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <!-- 创建向导对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="新建智能体应用"
      :width="dialogWidth"
      :close-on-click-modal="false"
      class="create-dialog"
    >
      <el-steps v-if="step !== 'choose'" :active="1" align-center finish-status="success" class="create-steps">
        <el-step title="选择创建方式" />
        <el-step title="填写信息" />
      </el-steps>

      <!-- Step 1：选择创建方式 -->
      <div v-if="step === 'choose'" class="create-ways">
        <div class="way-card" @click="goTemplate">
          <div class="way-icon" style="background: linear-gradient(135deg, #5b6cff, #8b5cf6)">
            <el-icon :size="26"><Files /></el-icon>
          </div>
          <div class="way-title">
            从模板创建
            <el-tag size="small" type="primary" effect="light" class="way-tag">推荐</el-tag>
          </div>
          <div class="way-desc">基于内置场景模板快速开始，自动预填配置，适合首次使用</div>
          <div class="way-go">查看场景模板 →</div>
        </div>
        <div class="way-card" @click="goBlank">
          <div class="way-icon" style="background: linear-gradient(135deg, #0ea5e9, #38bdf8)">
            <el-icon :size="26"><EditPen /></el-icon>
          </div>
          <div class="way-title">空白创建</div>
          <div class="way-desc">从零开始搭建，完全自定义应用类型、名称与配置</div>
          <div class="way-go">自定义创建 →</div>
        </div>
      </div>

      <!-- Step 2a：模板选择 -->
      <div v-else-if="step === 'template'" class="template-panel">
        <div class="template-hint">选择场景模板将自动预填应用名称与描述，创建后仍可在编辑器中修改</div>
        <div class="template-grid">
          <div v-for="tpl in appTemplates" :key="tpl.key" class="template-card" @click="selectTemplate(tpl)">
            <div class="tpl-cover" :style="{ background: `linear-gradient(135deg, ${tpl.color[0]}, ${tpl.color[1]})` }">
              <span class="tpl-icon">{{ tpl.icon }}</span>
            </div>
            <div class="tpl-body">
              <div class="tpl-title-row">
                <span class="tpl-name">{{ tpl.name }}</span>
                <el-tag size="small" effect="plain">{{ typeLabels[tpl.type] }}</el-tag>
              </div>
              <p class="tpl-desc">{{ tpl.desc }}</p>
              <div class="tpl-foot">
                <span class="tpl-use">{{ tpl.useCase }}</span>
                <span class="tpl-arrow">使用 →</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Step 2b：填写信息 -->
      <div v-else class="create-form">
        <div class="create-field-label">应用名称</div>
        <el-input v-model="createForm.name" placeholder="例如：智能客服助手" maxlength="30" show-word-limit />
        <div class="create-field-label">应用类型</div>
        <div class="type-grid">
          <div
            v-for="(meta, key) in typeMeta"
            :key="key"
            class="type-option"
            :class="{ active: createForm.type === key }"
            @click="createForm.type = key as AppAgentType"
          >
            <span class="type-emoji">{{ meta.icon }}</span>
            <div class="type-info">
              <div class="type-name">{{ meta.label }}</div>
              <div class="type-desc">{{ meta.desc }}</div>
            </div>
          </div>
        </div>
        <div class="create-field-label">应用描述</div>
        <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="简单描述这个应用的用途（可选）" />
      </div>

      <template #footer>
        <template v-if="step === 'choose'">
          <el-button @click="dialogVisible = false">取消</el-button>
        </template>
        <template v-else-if="step === 'template'">
          <el-button @click="backToChoose">返回</el-button>
          <el-button type="primary" class="btn-gradient" @click="goBlank">跳过模板，空白创建</el-button>
        </template>
        <template v-else>
          <el-button @click="backToChoose">返回</el-button>
          <el-button type="primary" class="btn-gradient" :loading="creating" @click="confirmCreate">
            创建应用
          </el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.apps-page {
  max-width: 1280px;
  margin: 0 auto;
}

.apps-head {
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

/* ---------- 筛选工具栏 ---------- */
.apps-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}
.toolbar-search {
  width: 260px;
}
.toolbar-type {
  width: 160px;
}

/* ---------- 分页 ---------- */
.apps-pagination {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

/* ---------- 卡片网格 ---------- */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  min-height: 200px;
}
.app-card {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  flex-direction: column;
}
.app-cover {
  position: relative;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.cover-deco {
  position: absolute;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  top: -80px;
  right: -50px;
}
.cover-deco::after {
  content: '';
  position: absolute;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  bottom: -60px;
  left: -30px;
}
.cover-icon {
  font-size: 34px;
  position: relative;
  z-index: 1;
  transition: transform 0.25s ease;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.2));
}
.app-card:hover .cover-icon {
  transform: scale(1.18) rotate(-6deg);
}
.cover-type {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 10px;
  backdrop-filter: blur(4px);
}

.app-body {
  padding: 14px 16px 16px;
  flex: 1;
}
.app-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.app-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.app-desc {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-tertiary);
  min-height: 34px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.app-foot {
  margin-top: 8px;
}
.app-time {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

/* hover 浮现操作 */
.app-actions {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 10px 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: linear-gradient(180deg, transparent, var(--bg-card) 30%);
  opacity: 0;
  transform: translateY(6px);
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.app-card:hover .app-actions {
  opacity: 1;
  transform: translateY(0);
}
.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 9px;
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

/* ---------- 空状态 ---------- */
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

/* ---------- 创建向导对话框 ---------- */
.create-dialog .el-dialog__body {
  padding-top: 4px;
}
.create-steps {
  margin-bottom: 18px;
  padding-top: 4px;
}

/* Step 1：创建方式 */
.create-ways {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 8px 4px 4px;
}
.way-card {
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-lg, 14px);
  padding: 22px 20px;
  cursor: pointer;
  transition: all 0.22s ease;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.way-card:hover {
  border-color: var(--brand-1);
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(91, 108, 255, 0.12);
}
.way-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.way-title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.way-tag {
  margin-left: 4px;
}
.way-desc {
  font-size: 12.5px;
  color: var(--text-tertiary);
  line-height: 1.6;
  min-height: 40px;
}
.way-go {
  font-size: 12.5px;
  color: var(--brand-1);
  font-weight: 500;
  margin-top: auto;
}

/* Step 2a：模板选择 */
.template-panel {
  padding: 4px 2px 0;
}
.template-hint {
  font-size: 12.5px;
  color: var(--text-tertiary);
  margin-bottom: 14px;
}
.template-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.template-card {
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.22s ease;
  display: flex;
  flex-direction: column;
}
.template-card:hover {
  border-color: var(--brand-1);
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(91, 108, 255, 0.12);
}
.tpl-cover {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.tpl-icon {
  font-size: 26px;
  filter: drop-shadow(0 3px 6px rgba(0, 0, 0, 0.18));
}
.tpl-body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.tpl-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.tpl-name {
  font-size: 13.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tpl-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.55;
  min-height: 38px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tpl-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tpl-use {
  font-size: 11px;
  color: var(--text-secondary);
}
.tpl-arrow {
  font-size: 12px;
  color: var(--brand-1);
  font-weight: 500;
}

/* Step 2b：表单 */
.create-form {
  padding: 4px 8px 0;
}
.create-field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 14px 0 8px;
}
.create-field-label:first-child {
  margin-top: 0;
}
.type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.type-option {
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.type-option:hover {
  border-color: var(--brand-1);
  transform: translateY(-2px);
}
.type-option.active {
  border-color: var(--brand-1);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 3px rgba(91, 108, 255, 0.12);
}
.type-emoji {
  font-size: 22px;
}
.type-name {
  font-size: 13.5px;
  font-weight: 600;
}
.type-desc {
  font-size: 11.5px;
  color: var(--text-tertiary);
  line-height: 1.5;
}
</style>
