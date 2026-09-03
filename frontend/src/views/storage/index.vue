<template>
  <div class="page-container storage-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head-info">
        <h2 class="head-title">数据存储</h2>
        <p class="head-desc">
          面向智能体的结构化数据管理：自定义数据表与行记录，支持 JSON / CSV 导入导出，可与记忆、知识库能力配合使用。
        </p>
      </div>
      <div class="head-actions">
        <el-button type="primary" @click="openTableDialog()">
          <el-icon><Plus /></el-icon>&nbsp;新建数据表
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="hover-card" v-loading="loading" style="min-height: 300px" />

    <div v-else class="store-layout">
      <!-- 左侧：数据表列表 -->
      <el-card shadow="never" class="table-list-card">
        <div class="table-list-head">
          <el-input v-model="listKeyword" placeholder="搜索数据表" clearable class="list-search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <div v-if="!tables.length" class="list-empty">
          <el-empty :image-size="80" description="暂无数据表" />
        </div>
        <div v-else class="table-list">
          <div
            v-for="t in tables"
            :key="t.id"
            class="table-item"
            :class="{ active: selectedId === t.id }"
            @click="selectTable(t)"
          >
            <div class="table-item-top">
              <span class="table-icon"><el-icon><Collection /></el-icon></span>
              <div class="table-item-body">
                <p class="table-name">{{ t.name }}</p>
                <p class="table-meta">{{ t.rowCount }} 行 · {{ tableColumns(t).length }} 列</p>
              </div>
            </div>
            <p v-if="t.description" class="table-desc">{{ t.description }}</p>
          </div>
        </div>
      </el-card>

      <!-- 右侧：表详情 -->
      <el-card shadow="never" class="table-detail-card">
        <template v-if="!current">
          <el-empty description="选择左侧数据表查看记录，或点击右上角新建数据表" />
        </template>
        <template v-else>
          <div class="detail-head">
            <div class="detail-title-box">
              <div class="detail-title-line">
                <h3 class="detail-title">{{ current.name }}</h3>
                <span v-if="current.label && current.label !== current.name" class="label-tag">
                  {{ current.label }}
                </span>
              </div>
              <p v-if="current.description" class="detail-desc">{{ current.description }}</p>
            </div>
            <div class="detail-actions">
              <el-button type="primary" @click="openRowDialog()">
                <el-icon><Plus /></el-icon>&nbsp;新增行
              </el-button>
              <el-button @click="openTableDialog(current)">
                <el-icon><Setting /></el-icon>&nbsp;列设置
              </el-button>
              <el-dropdown trigger="click" @command="onMoreCommand">
                <el-button>
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="import"><el-icon><Upload /></el-icon>导入数据</el-dropdown-item>
                    <el-dropdown-item command="export"><el-icon><Download /></el-icon>导出 CSV</el-dropdown-item>
                    <el-dropdown-item divided command="delete" class="danger-item">
                      <el-icon><Delete /></el-icon>删除数据表
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>

          <div class="records-toolbar">
            <div class="records-summary">
              共 <b>{{ rowPage.total }}</b> 条记录
              <span v-if="cols.length" class="cols-tip">
                <template v-for="c in cols" :key="c.key">
                  <span class="col-chip">{{ c.label || c.key }}</span>
                </template>
              </span>
            </div>
            <div class="records-filter">
              <el-input
                v-model="rowKeyword"
                placeholder="搜索记录内容"
                clearable
                class="row-search"
                @keyup.enter="loadRecords"
                @clear="loadRecords"
              >
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-button @click="loadRecords"><el-icon><Refresh /></el-icon></el-button>
            </div>
          </div>

          <el-table
            v-loading="rowsLoading"
            :data="rowPage.records"
            class="records-table"
            empty-text="暂无数据记录，点击「新增行」或导入数据"
            size="default"
          >
            <el-table-column type="index" label="#" width="54" align="center" />
            <el-table-column
              v-for="c in cols"
              :key="c.key"
              :label="c.label || c.key"
              min-width="150"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span v-if="c.type === 'boolean'">
                  <el-tag :type="row.data?.[c.key] ? 'success' : 'info'" size="small" effect="light">
                    {{ row.data?.[c.key] ? '是' : '否' }}
                  </el-tag>
                </span>
                <span v-else>{{ cellText(row.data?.[c.key]) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openRowDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeRow(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="rowPage.current"
              v-model:page-size="rowPage.size"
              :page-sizes="[10, 20, 50, 100]"
              :total="rowPage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadRecords"
              @size-change="sizeChange"
            />
          </div>
        </template>
      </el-card>
    </div>

    <!-- 数据表 新建/编辑 弹窗 -->
    <el-dialog
      v-model="tableDialogVisible"
      :title="tableForm.id ? '数据表设置' : '新建数据表'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="tableFormRef" :model="tableForm" :rules="tableRules" label-width="90px">
        <el-form-item label="表名" prop="name">
          <el-input v-model="tableForm.name" placeholder="数据表名称（租户内唯一），如：客户资料" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="tableForm.label" placeholder="可选，列表页展示的别名" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="tableForm.description" type="textarea" :rows="2" placeholder="可选，说明这张表存什么" />
        </el-form-item>
        <el-form-item label="列定义">
          <div class="columns-editor">
            <div v-for="(col, idx) in tableForm.columns" :key="idx" class="column-row">
              <el-input v-model="col.key" placeholder="key（字母/下划线）" class="col-key" />
              <el-input v-model="col.label" placeholder="列名（显示用）" class="col-label" />
              <el-select v-model="col.type" class="col-type">
                <el-option value="text" label="文本 text" />
                <el-option value="number" label="数字 number" />
                <el-option value="boolean" label="布尔 boolean" />
                <el-option value="date" label="日期 date" />
                <el-option value="select" label="选项 select" />
              </el-select>
              <el-input
                v-if="col.type === 'select'"
                v-model="col.optionsText"
                placeholder="选项，用英文逗号分隔"
                class="col-options"
              />
              <el-button
                link
                type="danger"
                :icon="Delete"
                :disabled="tableForm.columns.length <= 1"
                @click="removeColumn(idx)"
              />
            </div>
            <div class="columns-actions">
              <el-button size="small" type="primary" plain @click="addColumn">
                <el-icon><Plus /></el-icon>&nbsp;添加列
              </el-button>
              <span class="editor-tip">列的 key 用于记录字段与模板引用；已有数据后再调整列名不会清空数据。</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tableDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingTable" @click="submitTable">保存</el-button>
      </template>
    </el-dialog>

    <!-- 行记录 新增/编辑 弹窗 -->
    <el-dialog
      v-model="rowDialogVisible"
      :title="rowForm.id ? '编辑记录' : '新增记录'"
      width="640px"
      destroy-on-close
    >
      <el-form :model="rowForm.data" label-width="110px" class="row-form">
        <el-form-item v-for="c in cols" :key="c.key" :label="c.label || c.key">
          <el-input
            v-if="c.type === 'text'"
            v-model="rowForm.data[c.key]"
            :placeholder="`请输入${c.label || c.key}`"
          />
          <el-input-number
            v-else-if="c.type === 'number'"
            v-model="rowForm.data[c.key]"
            :controls="false"
            class="full-width"
          />
          <el-switch
            v-else-if="c.type === 'boolean'"
            v-model="rowForm.data[c.key]"
            active-text="是"
            inactive-text="否"
          />
          <el-date-picker
            v-else-if="c.type === 'date'"
            v-model="rowForm.data[c.key]"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            class="full-width"
          />
          <el-select
            v-else-if="c.type === 'select'"
            v-model="rowForm.data[c.key]"
            clearable
            placeholder="请选择"
            class="full-width"
          >
            <el-option v-for="opt in c.options || []" :key="opt" :value="opt" :label="opt" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rowDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingRow" @click="submitRow">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入数据 弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入数据" width="620px" destroy-on-close>
      <el-radio-group v-model="importMode" class="import-mode">
        <el-radio-button value="json">粘贴 JSON</el-radio-button>
        <el-radio-button value="csv">上传 CSV</el-radio-button>
      </el-radio-group>

      <template v-if="importMode === 'json'">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="import-alert"
          title="按行粘贴 JSON 数组，键与列 key 对应，例如：[{&quot;name&quot;: &quot;张三&quot;, &quot;age&quot;: 30}]"
        />
        <el-input
          v-model="importJsonText"
          type="textarea"
          :rows="8"
          placeholder='[{"name": "张三", "age": 30}, {"name": "李四", "age": 28}]'
          class="full-width"
        />
      </template>

      <template v-else>
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          accept=".csv"
          :on-change="onCsvFileChange"
          :on-exceed="onCsvExceed"
          :on-remove="() => (importCsvFile = null)"
          class="csv-uploader"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将 CSV 拖到此处，或<em>点击选择文件</em></div>
          <template #tip>
            <div class="el-upload__tip">首行需为表头（列名或列 key）；UTF-8 编码；大小不超过 5MB。</div>
          </template>
        </el-upload>
      </template>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, MoreFilled, Plus, Refresh, Search, Setting, Upload, Download, Collection } from '@element-plus/icons-vue'
import { dataStoreApi, downloadTableCsv } from '@/api/data-store'
import type { DataColumn, DataRecordRow, DataTable } from '@/api/types'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AnyRow = Record<string, any>

/** 列编辑器行（含选项文本输入） */
interface ColumnEditRow extends DataColumn {
  optionsText?: string
}

// ---------- 列表 ----------
const loading = ref(false)
const tables = ref<DataTable[]>([])
const listKeyword = ref('')
const selectedId = ref<number | null>(null)
const current = ref<DataTable | null>(null)

async function loadTables() {
  loading.value = true
  try {
    const res = await dataStoreApi.tablePage({ page: 1, size: 200, keyword: listKeyword.value || undefined })
    tables.value = res.records || []
    if (selectedId.value != null) {
      const keep = tables.value.find((t) => t.id === selectedId.value)
      if (!keep) selectedId.value = null
    }
  } finally {
    loading.value = false
  }
}

const tableColumns = (t: DataTable | null): DataColumn[] => {
  if (!t?.columnsJson) return []
  try {
    const list = JSON.parse(t.columnsJson) as DataColumn[]
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

const cols = computed<DataColumn[]>(() => tableColumns(current.value))

async function selectTable(t: DataTable) {
  selectedId.value = t.id
  current.value = t
  rowKeyword.value = ''
  rowPage.current = 1
  await loadRecords()
}

// ---------- 行记录 ----------
const rowPage = reactive({ current: 1, size: 20, total: 0, records: [] as DataRecordRow[] })
const rowsLoading = ref(false)
const rowKeyword = ref('')

async function loadRecords() {
  if (selectedId.value == null) return
  rowsLoading.value = true
  try {
    const res = await dataStoreApi.recordPage(selectedId.value, {
      page: rowPage.current,
      size: rowPage.size,
      keyword: rowKeyword.value || undefined
    })
    rowPage.records = res.records || []
    rowPage.total = Number(res.total || 0)
  } finally {
    rowsLoading.value = false
  }
}

function sizeChange() {
  rowPage.current = 1
  loadRecords()
}

const cellText = (v: unknown) => {
  if (v === null || v === undefined) return '—'
  return String(v)
}

const formatTime = (t?: string) => (t ? t.replace('T', ' ').slice(0, 19) : '—')

// ---------- 数据表 编辑 ----------
const tableDialogVisible = ref(false)
const submittingTable = ref(false)
const tableFormRef = ref<FormInstance>()
const tableForm = ref<{ id?: number; name: string; label?: string; description?: string; columns: ColumnEditRow[] }>({
  name: '',
  label: '',
  description: '',
  columns: []
})
const tableRules: FormRules = {
  name: [{ required: true, message: '请输入数据表名称', trigger: 'blur' }]
}

function newColumn(): ColumnEditRow {
  return { key: '', label: '', type: 'text', options: [], optionsText: '' }
}

function addColumn() {
  tableForm.value.columns.push(newColumn())
}

function removeColumn(idx: number) {
  tableForm.value.columns.splice(idx, 1)
}

function openTableDialog(table?: DataTable) {
  if (table) {
    const colsOf = tableColumns(table)
    tableForm.value = {
      id: table.id,
      name: table.name,
      label: table.label,
      description: table.description,
      columns: colsOf.map((c) => ({ ...c, optionsText: (c.options || []).join(',') }))
    }
  } else {
    tableForm.value = {
      name: '',
      label: '',
      description: '',
      columns: [newColumn(), newColumn()]
    }
  }
  tableDialogVisible.value = true
}

function buildColumnsPayload(): DataColumn[] {
  return tableForm.value.columns
    .filter((c) => c.key.trim())
    .map((c) => {
      const base: DataColumn = {
        key: c.key.trim(),
        label: c.label?.trim() || c.key.trim(),
        type: c.type
      }
      if (c.type === 'select') {
        base.options = (c.optionsText || '')
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean)
      }
      return base
    })
}

async function submitTable() {
  const valid = await tableFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!buildColumnsPayload().length) {
    ElMessage.warning('请至少填写一个有效的列 key')
    return
  }
  submittingTable.value = true
  try {
    const payload = {
      name: tableForm.value.name.trim(),
      label: tableForm.value.label?.trim(),
      description: tableForm.value.description,
      columns: buildColumnsPayload()
    }
    if (tableForm.value.id) {
      const updated = await dataStoreApi.updateTable(tableForm.value.id, payload)
      ElMessage.success('数据表已更新')
      current.value = updated
      await loadRecords()
    } else {
      const created = await dataStoreApi.createTable(payload)
      ElMessage.success('数据表已创建')
      tableDialogVisible.value = false
      await loadTables()
      selectedId.value = created.id
      current.value = created
      await loadRecords()
    }
  } finally {
    submittingTable.value = false
  }
}

// ---------- 行记录 新增/编辑 ----------
const rowDialogVisible = ref(false)
const submittingRow = ref(false)
const rowForm = ref<{ id?: number; data: AnyRow }>({ data: {} })

function blankRow(): AnyRow {
  const data: AnyRow = {}
  for (const c of cols.value) {
    if (c.type === 'boolean') data[c.key] = false
    else if (c.type === 'number') data[c.key] = undefined
    else data[c.key] = ''
  }
  return data
}

function openRowDialog(row?: DataRecordRow) {
  if (row) {
    const data: AnyRow = {}
    for (const c of cols.value) {
      const v = row.data?.[c.key]
      data[c.key] = v === undefined || v === null ? (c.type === 'boolean' ? false : '') : v
    }
    rowForm.value = { id: row.id, data }
  } else {
    rowForm.value = { data: blankRow() }
  }
  rowDialogVisible.value = true
}

async function submitRow() {
  if (selectedId.value == null) return
  submittingRow.value = true
  try {
    const data: AnyRow = {}
    for (const c of cols.value) {
      const v = rowForm.value.data[c.key]
      if (c.type === 'text') {
        data[c.key] = (v ?? '').toString().trim()
      } else {
        data[c.key] = v
      }
    }
    if (rowForm.value.id) {
      await dataStoreApi.updateRecord(rowForm.value.id, data)
      ElMessage.success('记录已更新')
    } else {
      await dataStoreApi.createRecord(selectedId.value, data)
      ElMessage.success('记录已新增')
    }
    rowDialogVisible.value = false
    await loadRecords()
    await loadTables()
  } finally {
    submittingRow.value = false
  }
}

async function removeRow(row: DataRecordRow) {
  await ElMessageBox.confirm('确定删除该行记录吗？', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await dataStoreApi.removeRecord(row.id)
  ElMessage.success('记录已删除')
  await loadRecords()
  await loadTables()
}

// ---------- 更多操作 ----------
async function onMoreCommand(cmd: string) {
  if (cmd === 'import') {
    importDialogVisible.value = true
  } else if (cmd === 'export') {
    await doExport()
  } else if (cmd === 'delete') {
    await deleteTable()
  }
}

async function deleteTable() {
  const t = current.value
  if (!t) return
  await ElMessageBox.confirm(
    `删除数据表「${t.name}」将同时删除其全部 ${t.rowCount} 行记录，且无法恢复。是否继续？`,
    '删除数据表',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await dataStoreApi.removeTable(t.id)
  ElMessage.success('数据表已删除')
  selectedId.value = null
  current.value = null
  rowPage.records = []
  rowPage.total = 0
  await loadTables()
}

async function doExport() {
  const t = current.value
  if (!t) return
  try {
    await downloadTableCsv(t.id, t.name)
    ElMessage.success('已开始导出 CSV')
  } catch (e) {
    ElMessage.error((e as Error).message || '导出失败')
  }
}

// ---------- 导入 ----------
const importDialogVisible = ref(false)
const importMode = ref<'json' | 'csv'>('json')
const importJsonText = ref('')
const importCsvFile = ref<File | null>(null)
const importing = ref(false)

function onCsvFileChange(file: { raw?: File }) {
  if (file.raw) importCsvFile.value = file.raw
}

function onCsvExceed() {
  ElMessage.warning('每次仅支持一个 CSV 文件')
}

async function doImport() {
  const t = current.value
  if (!t) return
  importing.value = true
  try {
    let count: number
    if (importMode.value === 'json') {
      let rows: AnyRow[]
      try {
        const parsed = JSON.parse(importJsonText.value)
        if (!Array.isArray(parsed)) throw new Error('JSON 顶层应为数组')
        rows = parsed
      } catch (e) {
        ElMessage.error(`JSON 解析失败：${(e as Error).message}`)
        return
      }
      if (!rows.length) {
        ElMessage.warning('没有可导入的数据')
        return
      }
      count = await dataStoreApi.importJson(t.id, rows)
    } else {
      if (!importCsvFile.value) {
        ElMessage.warning('请先选择 CSV 文件')
        return
      }
      count = await dataStoreApi.importCsv(t.id, importCsvFile.value)
    }
    ElMessage.success(`成功导入 ${count} 行记录`)
    importDialogVisible.value = false
    importJsonText.value = ''
    importCsvFile.value = null
    await loadRecords()
    await loadTables()
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  await loadTables()
  if (tables.value.length) {
    await selectTable(tables.value[0])
  }
})
</script>

<style scoped>
.storage-page {
  min-height: 100%;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.page-head-info .head-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.head-desc {
  margin: 6px 0 0;
  max-width: 720px;
  font-size: 13px;
  color: var(--text-tertiary, #8a8f98);
  line-height: 1.6;
}

.store-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 14px;
  margin-top: 16px;
  align-items: start;
}
.table-list-card,
.table-detail-card {
  border-radius: 14px;
  background: var(--bg-card, #fff);
}
.table-list-card {
  max-height: calc(100vh - 210px);
  overflow: auto;
}
.table-list-head {
  padding: 0 0 10px;
  position: sticky;
  top: 0;
  background: var(--bg-card, #fff);
  z-index: 2;
}
.list-search {
  width: 100%;
}
.table-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.table-item {
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.table-item:hover {
  background: var(--bg-hover, #f5f6f8);
}
.table-item.active {
  background: var(--primary-bg, rgba(52, 88, 250, 0.08));
  border-color: var(--primary-color, #3458fa);
}
.table-item-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.table-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(124, 93, 250, 0.1);
  color: #7c5dfa;
  flex-shrink: 0;
}
.table-item-body {
  min-width: 0;
}
.table-name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.table-meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
.table-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.list-empty {
  padding: 20px 0;
}

.table-detail-card {
  min-height: 420px;
}
.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-color, #eef0f3);
  margin-bottom: 12px;
}
.detail-title-line {
  display: flex;
  align-items: center;
  gap: 10px;
}
.detail-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}
.label-tag {
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
  background: var(--bg-tertiary, #f1f3f5);
  padding: 2px 10px;
  border-radius: 12px;
}
.detail-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-tertiary, #8a8f98);
}
.detail-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.danger-item {
  color: #f56c6c !important;
}

.records-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.records-summary {
  font-size: 13px;
  color: var(--text-secondary, #4a4e57);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.cols-tip {
  display: inline-flex;
  gap: 4px;
}
.col-chip {
  font-size: 11px;
  color: #7c5dfa;
  background: rgba(124, 93, 250, 0.07);
  padding: 1px 7px;
  border-radius: 8px;
}
.records-filter {
  display: flex;
  gap: 8px;
}
.row-search {
  width: 240px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

.columns-editor {
  width: 100%;
  border: 1px dashed var(--border-color, #d9dce1);
  border-radius: 10px;
  padding: 12px;
}
.column-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.col-key {
  width: 170px;
}
.col-label {
  width: 140px;
}
.col-type {
  width: 130px;
}
.col-options {
  flex: 1;
}
.columns-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.editor-tip {
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
.full-width {
  width: 100%;
}
.import-mode {
  margin-bottom: 14px;
}
.import-alert {
  margin-bottom: 10px;
}
.csv-uploader {
  width: 100%;
}
</style>
