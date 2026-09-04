<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { evalApi, type EvalDataset, type EvalSample } from '@/api/eval'

const loading = ref(false)
const list = ref<EvalDataset[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const filterStatus = ref<number | undefined>()

const SOURCE_LABEL: Record<string, string> = { manual: '手动创建', import: '批量导入', feedback: '标注回流' }

function fmt(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const data = await evalApi.datasetPage({ page: page.value, size: size.value, keyword: keyword.value || undefined, status: filterStatus.value })
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

async function toggleDataset(row: EvalDataset, val: string | number | boolean) {
  const status = val === true || val === 1 ? 1 : 0
  try {
    await evalApi.updateDataset(row.id, { status })
    row.status = status
    ElMessage.success(status ? '已启用' : '已停用（将不参与评测运行）')
  } catch {
    load()
  }
}

async function removeDataset(row: EvalDataset) {
  try {
    await ElMessageBox.confirm(
      `确认删除数据集「${row.name}」？其下所有样本将被删除；若已被评测任务引用将无法删除。`,
      '删除确认',
      { type: 'error' }
    )
    await evalApi.removeDataset(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {
    /* cancel */
  }
}

/* ============ 数据集表单 ============ */
const formVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const form = reactive({ name: '', description: '', source: 'manual' })

function openCreate() {
  editId.value = null
  form.name = ''
  form.description = ''
  form.source = 'manual'
  formVisible.value = true
}
function openEdit(row: EvalDataset) {
  editId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.source = row.source
  formVisible.value = true
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入数据集名称')
  saving.value = true
  try {
    if (editId.value) {
      await evalApi.updateDataset(editId.value, { name: form.name.trim(), description: form.description.trim() || undefined })
      ElMessage.success('已保存')
    } else {
      await evalApi.createDataset({ name: form.name.trim(), description: form.description.trim() || undefined, source: form.source })
      ElMessage.success('数据集已创建')
    }
    formVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ============ 数据集详情（样本管理） ============ */
const detailVisible = ref(false)
const activeDataset = ref<EvalDataset | null>(null)
const samples = ref<EvalSample[]>([])
const sampleTotal = ref(0)
const samplePage = ref(1)
const sampleLoading = ref(false)
const sampleKeyword = ref('')

async function openDetail(row: EvalDataset) {
  activeDataset.value = row
  detailVisible.value = true
  samplePage.value = 1
  sampleKeyword.value = ''
  samples.value = []
  sampleTotal.value = 0
  await loadSamples()
}
async function loadSamples() {
  if (!activeDataset.value) return
  sampleLoading.value = true
  try {
    const data = await evalApi.samplePage(activeDataset.value.id, {
      page: samplePage.value,
      size: 10,
      keyword: sampleKeyword.value || undefined
    })
    samples.value = data.records
    sampleTotal.value = data.total
  } finally {
    sampleLoading.value = false
  }
}

/* 样本新增/编辑 */
const sampleDialog = ref(false)
const sampleSaving = ref(false)
const sampleEditId = ref<number | null>(null)
const sampleForm = reactive({ question: '', reference: '', category: '' })

function openAddSample() {
  sampleEditId.value = null
  sampleForm.question = ''
  sampleForm.reference = ''
  sampleForm.category = ''
  sampleDialog.value = true
}
function openEditSample(row: EvalSample) {
  sampleEditId.value = row.id
  sampleForm.question = row.question
  sampleForm.reference = row.reference || ''
  sampleForm.category = row.category || ''
  sampleDialog.value = true
}
async function saveSample() {
  if (!activeDataset.value) return
  if (!sampleForm.question.trim()) return ElMessage.warning('请填写提问')
  sampleSaving.value = true
  try {
    const payload = {
      question: sampleForm.question.trim(),
      reference: sampleForm.reference.trim() || undefined,
      category: sampleForm.category.trim() || undefined
    }
    if (sampleEditId.value) {
      await evalApi.updateSample(activeDataset.value.id, sampleEditId.value, payload)
      ElMessage.success('样本已更新')
    } else {
      await evalApi.addSample(activeDataset.value.id, payload)
      ElMessage.success('样本已添加')
    }
    sampleDialog.value = false
    await loadSamples()
    await refreshCount()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    sampleSaving.value = false
  }
}
async function removeSample(row: EvalSample) {
  if (!activeDataset.value) return
  try {
    await ElMessageBox.confirm('确认删除该样本？', '删除确认', { type: 'error' })
    await evalApi.removeSample(activeDataset.value.id, row.id)
    ElMessage.success('已删除')
    if (samples.value.length === 1 && samplePage.value > 1) samplePage.value -= 1
    await loadSamples()
    await refreshCount()
  } catch {
    /* cancel */
  }
}
async function refreshCount() {
  if (!activeDataset.value) return
  const ds = await evalApi.datasetPage({ page: 1, size: 1, keyword: activeDataset.value.name })
  const found = ds.records.find((d) => d.id === activeDataset.value?.id)
  if (found) activeDataset.value.sampleCount = found.sampleCount
}

/* 批量导入 */
const importDialog = ref(false)
const importText = ref('')
const importing = ref(false)
async function doImport() {
  if (!activeDataset.value) return
  importing.value = true
  try {
    const { imported } = await evalApi.importSamples(activeDataset.value.id, importText.value)
    ElMessage.success(`成功导入 ${imported} 条样本`)
    importDialog.value = false
    importText.value = ''
    await loadSamples()
    await refreshCount()
  } catch (e) {
    ElMessage.error((e as Error).message || '导入失败')
  } finally {
    importing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container datasets-page">
    <div class="page-head">
      <div>
        <h2 class="head-title">评测数据集</h2>
        <p class="head-desc">管理用于评测的问答样本，支持手动维护与批量导入 · 共 {{ total }} 个数据集</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="openCreate">
        <el-icon><Plus /></el-icon>&nbsp;新建数据集
      </el-button>
    </div>

    <el-card shadow="never" class="data-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input v-model="keyword" placeholder="搜索数据集名称 / 描述" clearable class="toolbar-search" @keyup.enter="search" @clear="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 130px" @change="search">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </div>
      </div>

      <el-table v-loading="loading" :data="list">
        <el-table-column label="数据集" min-width="220">
          <template #default="{ row }">
            <div class="cell-main">
              <span class="cell-name">{{ row.name }}</span>
              <span class="dim-text">{{ row.description || '暂无描述' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="样本数" width="100">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">
              {{ row.sampleCount }} 条样本
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }"><el-tag size="small" effect="light">{{ SOURCE_LABEL[row.source] || row.source }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(v: string | number | boolean) => toggleDataset(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }"><span class="dim-text">{{ fmt(row.updateTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-links">
              <el-button link type="primary" size="small" @click="openDetail(row)">管理样本</el-button>
              <el-button link size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeDataset(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 16px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="search"
      />
    </el-card>

    <!-- 数据集表单 -->
    <el-dialog v-model="formVisible" :title="editId ? '编辑数据集' : '新建数据集'" width="520px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>数据集名称 <span class="req">*</span></label>
          <el-input v-model="form.name" placeholder="例如：客服问答基准集" maxlength="128" />
        </div>
        <div class="field-group">
          <label>描述</label>
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="说明数据覆盖场景与用途（可选）" maxlength="512" />
        </div>
        <div v-if="!editId" class="field-group">
          <label>创建方式</label>
          <el-radio-group v-model="form.source">
            <el-radio value="manual">手动录入</el-radio>
            <el-radio value="import">导入现有数据</el-radio>
          </el-radio-group>
        </div>
      </div>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 样本管理抽屉 -->
    <el-drawer v-model="detailVisible" :title="activeDataset?.name ? `样本管理 · ${activeDataset.name}` : '样本管理'" size="760px">
      <template v-if="activeDataset">
        <div class="drawer-tip">
          <el-text size="small" type="info">
            每个样本为「提问 + 参考答案（可选）」。评测时无参考答案视为可执行性通过；有参考答案则按字符重合度自动打分。
          </el-text>
        </div>
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-input v-model="sampleKeyword" placeholder="搜索提问 / 参考答案" clearable style="width: 230px" @keyup.enter="samplePage = 1; loadSamples()" @clear="samplePage = 1; loadSamples()">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button @click="samplePage = 1; loadSamples()">查询</el-button>
          </div>
          <div class="toolbar-right">
            <el-button @click="importDialog = true">
              <el-icon><Upload /></el-icon>&nbsp;批量导入
            </el-button>
            <el-button type="primary" @click="openAddSample">
              <el-icon><Plus /></el-icon>&nbsp;新增样本
            </el-button>
          </div>
        </div>

        <el-table v-loading="sampleLoading" :data="samples" size="small">
          <el-table-column label="提问" prop="question" min-width="200" show-overflow-tooltip />
          <el-table-column label="参考答案" min-width="160" show-overflow-tooltip>
            <template #default="{ row }"><span class="dim-text">{{ row.reference || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="分类" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.category" size="small" effect="plain">{{ row.category }}</el-tag>
              <span v-else class="dim-text">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button link size="small" @click="openEditSample(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeSample(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 12px; justify-content: flex-end"
          small
          layout="total, prev, pager, next"
          :total="sampleTotal"
          :page-size="10"
          v-model:current-page="samplePage"
          @current-change="loadSamples"
        />
      </template>
    </el-drawer>

    <!-- 样本表单 -->
    <el-dialog v-model="sampleDialog" :title="sampleEditId ? '编辑样本' : '新增样本'" width="600px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>提问 <span class="req">*</span></label>
          <el-input v-model="sampleForm.question" type="textarea" :rows="3" placeholder="输入需要评测的提问" maxlength="4000" />
        </div>
        <div class="field-group">
          <label>参考答案</label>
          <el-input v-model="sampleForm.reference" type="textarea" :rows="3" placeholder="期望的回答要点（可选，留空则只评测执行性）" maxlength="8000" />
        </div>
        <div class="field-group">
          <label>分类标签</label>
          <el-input v-model="sampleForm.category" placeholder="例如：售后退款 / 物流查询（可选）" maxlength="64" />
        </div>
      </div>
      <template #footer>
        <el-button @click="sampleDialog = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="sampleSaving" @click="saveSample">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importDialog" title="批量导入样本" width="620px" :close-on-click-modal="false">
      <p class="import-tip">
        支持两种格式：<br />
        ① 每行一条，提问与参考答案用 Tab 分隔：<code>请问退货政策？\t支持 7 天无理由退货</code><br />
        ② JSON 数组：<code>[{"question":"...","reference":"...","category":"..."}]</code>
      </p>
      <el-input v-model="importText" type="textarea" :rows="10" placeholder="粘贴要导入的样本内容…" />
      <template #footer>
        <el-button @click="importDialog = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.datasets-page {
  max-width: 1400px;
  margin: 0 auto;
}
.page-head {
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
.data-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.toolbar-left {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.toolbar-search {
  width: 240px;
}
.toolbar-right {
  display: flex;
  gap: 8px;
}
.cell-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 2px 0;
}
.cell-name {
  font-size: 13.5px;
  font-weight: 600;
}
.dim-text {
  color: var(--text-tertiary);
  font-size: 12px;
}
.row-links {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
}
.row-links .el-button {
  padding: 0 2px;
}
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-group label {
  font-size: 13px;
  font-weight: 600;
}
.req {
  color: #f56c6c;
}
.drawer-tip {
  margin-bottom: 12px;
}
.import-tip {
  font-size: 12.5px;
  color: var(--text-secondary);
  line-height: 1.9;
  margin: 0 0 10px;
}
.import-tip code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  background: var(--fill-light);
  padding: 0 4px;
  border-radius: 4px;
}
</style>
