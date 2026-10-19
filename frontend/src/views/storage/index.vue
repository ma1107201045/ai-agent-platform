<template>
  <div class="page-container storage-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head-info">
        <h2 class="head-title">数据存储</h2>
        <p class="head-desc">
          面向智能体的结构化数据管理：自定义数据表与行记录，支持 JSON / CSV
          导入导出，可与记忆、知识库能力配合使用。
        </p>
      </div>
      <div class="head-actions">
        <el-button type="primary" @click="tableDialog?.open()">
          <el-icon><Plus /></el-icon>&nbsp;新建数据表
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="hover-card" v-loading="loading" style="min-height: 300px" />

    <div v-else class="store-layout">
      <!-- 左侧：数据表列表 -->
      <el-card shadow="never" class="table-list-card">
        <div class="table-list-head">
          <el-input
            v-model="listKeyword"
            placeholder="搜索数据表"
            clearable
            class="list-search"
            @keyup.enter="loadTables"
            @clear="loadTables"
          >
            <template #prefix
              ><el-icon><Search /></el-icon
            ></template>
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
              <span class="table-icon"
                ><el-icon><Collection /></el-icon
              ></span>
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
              <el-button type="primary" @click="rowDialog?.open()">
                <el-icon><Plus /></el-icon>&nbsp;新增行
              </el-button>
              <el-button @click="tableDialog?.open(current ?? undefined)">
                <el-icon><Setting /></el-icon>&nbsp;列设置
              </el-button>
              <el-dropdown trigger="click" @command="onMoreCommand">
                <el-button>
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="import"
                      ><el-icon><Upload /></el-icon>导入数据</el-dropdown-item
                    >
                    <el-dropdown-item command="export"
                      ><el-icon><Download /></el-icon>导出 CSV</el-dropdown-item
                    >
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
                <template #prefix
                  ><el-icon><Search /></el-icon
                ></template>
              </el-input>
              <el-button @click="loadRecords"
                ><el-icon><Refresh /></el-icon
              ></el-button>
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
                  <el-tag
                    :type="row.data?.[c.key] ? 'success' : 'info'"
                    size="small"
                    effect="light"
                  >
                    {{ row.data?.[c.key] ? '是' : '否' }}
                  </el-tag>
                </span>
                <span v-else>{{ cellText(row.data?.[c.key]) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatTime(row.updateTime, true) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="rowDialog?.open(row)"
                  >编辑</el-button
                >
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

    <TableEditDialog ref="tableDialog" @saved="onTableSaved" />
    <RowEditDialog ref="rowDialog" :table-id="selectedId" :cols="cols" @saved="onRowSaved" />
    <ImportDialog ref="importDialog" :table-id="selectedId" @imported="onRowSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  Delete,
  Download,
  MoreFilled,
  Plus,
  Refresh,
  Search,
  Setting,
  Upload
} from '@element-plus/icons-vue'
import { dataStoreApi, downloadTableCsv } from '@/api/data-store'
import type { DataColumn, DataRecordRow, DataTable } from '@/api/types'
import { formatTime } from '@/utils/format'
import { cellText, tableColumns } from './storageMeta'
import TableEditDialog from './components/TableEditDialog.vue'
import RowEditDialog from './components/RowEditDialog.vue'
import ImportDialog from './components/ImportDialog.vue'

// ---------- 列表 ----------
const loading = ref(false)
const tables = ref<DataTable[]>([])
const listKeyword = ref('')
const selectedId = ref<number | null>(null)
const current = ref<DataTable | null>(null)

async function loadTables() {
  loading.value = true
  try {
    const res = await dataStoreApi.tablePage({
      page: 1,
      size: 200,
      keyword: listKeyword.value || undefined
    })
    tables.value = res.records || []
    if (selectedId.value != null) {
      const keep = tables.value.find((t) => t.id === selectedId.value)
      if (!keep) selectedId.value = null
    }
  } finally {
    loading.value = false
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

// ---------- 弹窗回调 ----------
const tableDialog = ref<InstanceType<typeof TableEditDialog> | null>(null)
const rowDialog = ref<InstanceType<typeof RowEditDialog> | null>(null)
const importDialog = ref<InstanceType<typeof ImportDialog> | null>(null)

async function onTableSaved(table: DataTable, isEdit: boolean) {
  if (isEdit) {
    current.value = table
    await loadRecords()
  } else {
    await loadTables()
    selectedId.value = table.id
    current.value = table
    await loadRecords()
  }
}

async function onRowSaved() {
  await loadRecords()
  await loadTables()
}

async function removeRow(row: DataRecordRow) {
  try {
    await ElMessageBox.confirm('确定删除该行记录吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }
  await dataStoreApi.removeRecord(row.id)
  ElMessage.success('记录已删除')
  await loadRecords()
  await loadTables()
}

// ---------- 更多操作 ----------
async function onMoreCommand(cmd: string) {
  if (cmd === 'import') {
    importDialog.value?.open()
  } else if (cmd === 'export') {
    await doExport()
  } else if (cmd === 'delete') {
    await deleteTable()
  }
}

async function deleteTable() {
  const t = current.value
  if (!t) return
  try {
    await ElMessageBox.confirm(
      `删除数据表「${t.name}」将同时删除其全部 ${t.rowCount} 行记录，且无法恢复。是否继续？`,
      '删除数据表',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
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
</style>
