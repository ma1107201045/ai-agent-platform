<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { appTemplateApi, type AppTemplate } from '@/api/app-template'

const router = useRouter()
const loading = ref(false)
const list = ref<AppTemplate[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const categoryFilter = ref('')
const appTypeFilter = ref('')

const categoryMeta: Record<string, { label: string; color: string; bg: string }> = {
  'customer-service': { label: '智能客服', color: '#5b6cff', bg: '#eef0ff' },
  translate: { label: '翻译', color: '#0ea5e9', bg: '#e6f6fe' },
  content: { label: '内容创作', color: '#8b5cf6', bg: '#f3edfe' },
  'data-analysis': { label: '数据分析', color: '#10b981', bg: '#e6faf3' },
  marketing: { label: '营销', color: '#f59e0b', bg: '#fef5e6' },
  coding: { label: '编程', color: '#6366f1', bg: '#eeeefe' },
  custom: { label: '自定义', color: '#64748b', bg: '#f1f5f9' }
}
const appTypeMeta: Record<string, { label: string; type: 'success' | 'warning' | 'primary' }> = {
  chatflow: { label: '对话流', type: 'primary' },
  workflow: { label: '工作流', type: 'warning' },
  agent: { label: '智能体', type: 'success' }
}

async function load() {
  loading.value = true
  try {
    const data = await appTemplateApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      category: categoryFilter.value || undefined,
      appType: appTypeFilter.value || undefined
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

/* ---------- 一键使用模板 ---------- */
const using = ref(false)
function useTemplate(row: AppTemplate) {
  ElMessageBox.prompt('将以该模板创建应用草稿，可自定义应用名称', '使用模板', {
    confirmButtonText: '创建应用',
    cancelButtonText: '取消',
    inputValue: row.name,
    inputPattern: /\S+/,
    inputErrorMessage: '应用名称不能为空'
  })
    .then(async ({ value }) => {
      using.value = true
      try {
        const app = await appTemplateApi.instantiate(row.id, value.trim())
        ElMessage.success(`应用「${app.name}」已创建（草稿）`)
        ElMessageBox.confirm('是否立即进入应用进行编排完善？', '创建成功', {
          confirmButtonText: '去编排',
          cancelButtonText: '稍后',
          type: 'success'
        })
          .then(() => router.push(`/app/agents/${app.id}/edit`))
          .catch(() => load())
      } finally {
        using.value = false
      }
    })
    .catch(() => {})
}

/* ---------- 新建 / 编辑自定义模板 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  category: 'custom',
  appType: 'chatflow',
  icon: '📦',
  description: '',
  useCase: '',
  welcomeMessage: ''
})

function openCreate() {
  editingId.value = null
  form.name = ''
  form.category = 'custom'
  form.appType = 'chatflow'
  form.icon = '📦'
  form.description = ''
  form.useCase = ''
  form.welcomeMessage = ''
  dialogVisible.value = true
}
function openEdit(row: AppTemplate) {
  editingId.value = row.id
  form.name = row.name
  form.category = row.category || 'custom'
  form.appType = row.appType
  form.icon = row.icon || '📦'
  form.description = row.description || ''
  form.useCase = row.useCase || ''
  form.welcomeMessage = row.welcomeMessage || ''
  dialogVisible.value = true
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入模板名称')
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      category: form.category,
      appType: form.appType,
      icon: form.icon || undefined,
      description: form.description.trim() || undefined,
      useCase: form.useCase.trim() || undefined,
      welcomeMessage: form.welcomeMessage || undefined,
      status: 1
    }
    if (editingId.value == null) {
      await appTemplateApi.create(payload)
      ElMessage.success('模板已保存')
    } else {
      await appTemplateApi.update(editingId.value, payload)
      ElMessage.success('模板已更新')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function remove(row: AppTemplate) {
  ElMessageBox.confirm(`确认删除模板「${row.name}」？删除后不可恢复。`, '删除确认', { type: 'error' })
    .then(async () => {
      await appTemplateApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<template>
  <div class="page-container tpl-page">
    <div class="tpl-head">
      <div>
        <h2 class="head-title">应用模板</h2>
        <p class="head-desc">覆盖常见业务场景的场景模板库，一键创建应用快速起步</p>
      </div>
      <el-button type="primary" class="btn-gradient" :icon="Plus" @click="openCreate">保存为模板</el-button>
    </div>

    <div class="tpl-toolbar hover-card">
      <div class="filter-chips">
        <button class="chip" :class="{ active: categoryFilter === '' }" @click="categoryFilter = ''; search()">全部</button>
        <button
          v-for="(meta, key) in categoryMeta"
          :key="key"
          class="chip"
          :class="{ active: categoryFilter === key }"
          @click="categoryFilter = key; search()"
        >{{ meta.label }}</button>
      </div>
      <div class="toolbar-right">
        <el-select v-model="appTypeFilter" placeholder="应用类型" clearable style="width: 130px" @change="search">
          <el-option label="对话流" value="chatflow" />
          <el-option label="工作流" value="workflow" />
          <el-option label="智能体" value="agent" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索模板 / 场景"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="search">搜索</el-button>
      </div>
    </div>

    <div v-loading="loading" class="tpl-grid">
      <div v-for="row in list" :key="row.id" class="tpl-card hover-card">
        <div class="tpl-top">
          <div class="tpl-icon" :style="{ background: categoryMeta[row.category || '']?.bg || '#f1f5f9' }">
            {{ row.icon || '📦' }}
          </div>
          <div class="tpl-tags">
            <el-tag v-if="row.builtin === 1" size="small" effect="dark" type="primary">官方</el-tag>
            <span v-else class="custom-tag">自定义</span>
            <el-tag size="small" effect="plain" :type="appTypeMeta[row.appType]?.type || 'info'">
              {{ appTypeMeta[row.appType]?.label || row.appType }}
            </el-tag>
          </div>
        </div>
        <div class="tpl-name">{{ row.name }}</div>
        <div class="tpl-desc">{{ row.description || '暂无简介' }}</div>
        <div v-if="row.useCase" class="tpl-usecase">
          <span class="use-label" :style="{ color: categoryMeta[row.category || '']?.color }">适用</span>
          {{ row.useCase }}
        </div>
        <div class="tpl-foot">
          <span class="dim-text">{{ row.usageCount || 0 }} 次使用</span>
          <div class="foot-actions">
            <el-button size="small" type="primary" plain @click="useTemplate(row)">使用模板</el-button>
            <el-button v-if="row.builtin !== 1" size="small" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.builtin !== 1" size="small" link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="!loading && list.length === 0" class="empty-card hover-card">
      <el-empty description="暂无匹配的模板" :image-size="90" />
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

    <!-- 新建 / 编辑模板 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '保存为模板' : '编辑模板'" width="620px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>模板名称</label>
          <el-input v-model="form.name" maxlength="128" show-word-limit placeholder="例如：周报生成助手" />
        </div>
        <div class="form-grid">
          <div class="field-group">
            <label>分类</label>
            <el-select v-model="form.category" style="width: 100%">
              <el-option v-for="(meta, key) in categoryMeta" :key="key" :label="meta.label" :value="key" />
            </el-select>
          </div>
          <div class="field-group">
            <label>应用类型</label>
            <el-select v-model="form.appType" style="width: 100%">
              <el-option label="对话流" value="chatflow" />
              <el-option label="工作流" value="workflow" />
              <el-option label="智能体" value="agent" />
            </el-select>
          </div>
        </div>
        <div class="form-grid">
          <div class="field-group">
            <label>图标（emoji）</label>
            <el-input v-model="form.icon" maxlength="4" placeholder="📦" />
          </div>
          <div class="field-group">
            <label>适用场景</label>
            <el-input v-model="form.useCase" placeholder="例如：市场运营 / 研发提效" />
          </div>
        </div>
        <div class="field-group">
          <label>模板简介</label>
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="512" placeholder="一句话描述该模板的用途" />
        </div>
        <div class="field-group">
          <label>默认开场白（可选）</label>
          <el-input v-model="form.welcomeMessage" type="textarea" :rows="2" maxlength="500" placeholder="使用该模板创建应用后自动填写的开场白" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tpl-page {
  max-width: 1400px;
  margin: 0 auto;
}
.tpl-head {
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

.tpl-toolbar {
  display: flex;
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
}
.chip {
  border: none;
  padding: 5px 12px;
  border-radius: 16px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
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
}

.tpl-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 16px;
}
.tpl-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  min-height: 205px;
}
.tpl-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tpl-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.tpl-tags {
  display: flex;
  align-items: center;
  gap: 6px;
}
.custom-tag {
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 4px;
  background: rgba(100, 116, 139, 0.12);
  color: #64748b;
  line-height: 1.5;
}
.tpl-name {
  margin-top: 12px;
  font-size: 15px;
  font-weight: 700;
}
.tpl-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tpl-usecase {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.use-label {
  font-weight: 600;
}
.tpl-foot {
  margin-top: auto;
  padding-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--border-color);
}
.foot-actions {
  display: flex;
  align-items: center;
}
.dim-text {
  font-size: 12px;
  color: var(--text-tertiary);
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
  grid-template-columns: 1fr 1fr;
  gap: 14px;
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
</style>
