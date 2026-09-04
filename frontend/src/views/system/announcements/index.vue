<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { announcementApi, type SysAnnouncement } from '@/api/sys-announcement'

const loading = ref(false)
const list = ref<SysAnnouncement[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
/** -1 全部 / 0 草稿 / 1 发布中 / 2 已下线 */
const status = ref(-1)

const statusMeta: Record<number, { label: string; type: 'info' | 'success' | 'warning' }> = {
  0: { label: '草稿', type: 'info' },
  1: { label: '发布中', type: 'success' },
  2: { label: '已下线', type: 'warning' }
}

async function load() {
  loading.value = true
  try {
    const data = await announcementApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      status: status.value === -1 ? undefined : status.value
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

/* ---------- 新建 / 编辑 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ title: '', content: '', pinned: 0 })

function openCreate() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.pinned = 0
  dialogVisible.value = true
}
function openEdit(row: SysAnnouncement) {
  editingId.value = row.id
  form.title = row.title
  form.content = row.content || ''
  form.pinned = row.pinned
  dialogVisible.value = true
}
async function save() {
  if (!form.title.trim()) return ElMessage.warning('请输入公告标题')
  if (!form.content.trim()) return ElMessage.warning('请输入公告内容')
  saving.value = true
  try {
    const payload = { title: form.title.trim(), content: form.content, pinned: form.pinned }
    if (editingId.value == null) {
      await announcementApi.create(payload)
      ElMessage.success('草稿已创建')
    } else {
      await announcementApi.update(editingId.value, payload)
      ElMessage.success('公告已更新')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ---------- 发布 / 下线 / 删除 ---------- */
function publish(row: SysAnnouncement) {
  ElMessageBox.confirm(
    '发布后将向平台全体用户推送一条站内通知，确认发布？',
    '发布公告',
    { type: 'warning', confirmButtonText: '确认发布' }
  )
    .then(async () => {
      await announcementApi.publish(row.id)
      ElMessage.success('已发布')
      load()
    })
    .catch(() => {})
}
function offline(row: SysAnnouncement) {
  ElMessageBox.confirm(`确认下线公告「${row.title}」？`, '下线公告', { type: 'warning' })
    .then(async () => {
      await announcementApi.offline(row.id)
      ElMessage.success('已下线')
      load()
    })
    .catch(() => {})
}
function remove(row: SysAnnouncement) {
  ElMessageBox.confirm(`确认删除公告「${row.title}」？删除后不可恢复。`, '删除确认', { type: 'error' })
    .then(async () => {
      await announcementApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

function fmt(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="page-container announcements-page">
    <div class="ann-head">
      <div>
        <h2 class="head-title">公告管理</h2>
        <p class="head-desc">发布系统公告与运营通知，发布后将向全体用户推送站内通知</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="openCreate">新建公告</el-button>
    </div>

    <el-card shadow="never" class="ann-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-radio-group v-model="status" size="small" @change="search">
            <el-radio-button :value="-1">全部</el-radio-button>
            <el-radio-button :value="1">发布中</el-radio-button>
            <el-radio-button :value="0">草稿</el-radio-button>
            <el-radio-button :value="2">已下线</el-radio-button>
          </el-radio-group>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="keyword"
            placeholder="搜索标题 / 内容"
            clearable
            style="width: 240px"
            @keyup.enter="search"
            @clear="search"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="search">搜索</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list">
        <el-table-column type="expand" width="36">
          <template #default="{ row }">
            <div class="ann-preview">
              <div class="ann-preview-title">{{ row.title }}</div>
              <pre class="ann-preview-content">{{ row.content }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="公告" min-width="260">
          <template #default="{ row }">
            <div class="ann-title-cell">
              <el-tag v-if="row.pinned === 1" type="danger" size="small" effect="dark" class="pin-tag">置顶</el-tag>
              <span class="ann-title">{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusMeta[row.status]?.type" effect="light">
              {{ statusMeta[row.status]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ fmt(row.publishTime) }}</template>
        </el-table-column>
        <el-table-column label="下线时间" width="150">
          <template #default="{ row }">{{ fmt(row.offlineTime) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="150">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 1" link type="primary" @click="publish(row)">发布</el-button>
            <el-button v-else link type="warning" @click="offline(row)">下线</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > size"
        style="margin-top: 16px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="load"
      />
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? '新建公告' : '编辑公告'"
      width="620px"
      :close-on-click-modal="false"
    >
      <div class="dialog-body">
        <div class="field-group">
          <label>公告标题</label>
          <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="例如：平台维护通知" />
        </div>
        <div class="field-group">
          <label>公告内容</label>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="支持多段落文本，发布后将推送给全体用户"
          />
        </div>
        <div class="field-row">
          <div class="field-group half">
            <label>受众范围</label>
            <el-select :model-value="'all'" disabled style="width: 100%">
              <el-option label="全部用户" value="all" />
            </el-select>
          </div>
          <div class="field-group half switch-field">
            <label>置顶展示</label>
            <el-switch v-model="form.pinned" :active-value="1" :inactive-value="0" />
            <span class="dim-text">置顶公告将在列表优先展示</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">
          {{ editingId == null ? '保存草稿' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.announcements-page {
  max-width: 1400px;
  margin: 0 auto;
}
.ann-head {
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
.ann-card {
  border-radius: var(--radius-lg);
}
.toolbar-left {
  display: flex;
  align-items: center;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ann-preview {
  padding: 4px 12px 8px 56px;
}
.ann-preview-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
}
.ann-preview-content {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}
.ann-title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.pin-tag {
  flex-shrink: 0;
}
.ann-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
.field-row {
  display: flex;
  gap: 16px;
}
.half {
  flex: 1;
}
.switch-field {
  align-items: flex-start;
}
.switch-field .dim-text {
  font-size: 12px;
}
.dim-text {
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
