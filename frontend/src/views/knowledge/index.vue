<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'
import {
  ChatDotRound, Delete, FolderOpened,
  Plus, Refresh, Search, Upload, UploadFilled
} from '@element-plus/icons-vue'
import { knowledgeApi } from '@/api/knowledge'
import { modelApi } from '@/api/model'
import type {
  ChatModelInfo, KnowledgeChunk, KnowledgeDataset, KnowledgeDocument, SearchHit
} from '@/api/types'

// ---------- 数据集列表 ----------
const activeTab = ref<'datasets' | 'documents' | 'search'>('datasets')
const loading = ref(false)
const datasets = ref<KnowledgeDataset[]>([])

// ---------- 选中的数据集 ----------
const selectedDatasetId = ref<number | null>(null)
const selectedDataset = computed(
  () => datasets.value.find((d) => d.id === selectedDatasetId.value) ?? null
)

/** 非空数据集别名（用于模板中 v-if 收敛后的场景，规避 vue-tsc 空值推断） */
const ds = computed(() => selectedDataset.value as KnowledgeDataset)

// ---------- 文档 ----------
const documents = ref<KnowledgeDocument[]>([])
const loadingDocs = ref(false)
const uploadVisible = ref(false)
const uploadMode = ref<'file' | 'text'>('file')
const uploadForm = ref({ name: '', content: '' })
const fileList = ref<UploadUserFile[]>([])
const uploadFileObj = ref<File | null>(null)
const uploading = ref(false)

// ---------- 分块预览 ----------
const chunkDrawerVisible = ref(false)
const chunks = ref<KnowledgeChunk[]>([])
const loadingChunks = ref(false)
const chunkDocName = ref('')

// ---------- 检索测试 ----------
const embeddingModels = ref<ChatModelInfo[]>([])
const rerankModels = ref<ChatModelInfo[]>([])
const searchQuery = ref('')
const searchTopK = ref(3)
const searchRerankId = ref<number | null>(null)
const searchHits = ref<SearchHit[]>([])
const searching = ref(false)

// ---------- 数据集编辑对话框 ----------
const dsDialogVisible = ref(false)
const dsDialogTitle = ref('')
const dsForm = ref<Partial<KnowledgeDataset>>({})
const dsSaving = ref(false)

const docStatusType: Record<string, string> = {
  ready: 'success',
  indexing: 'warning',
  pending: 'info',
  failed: 'danger'
}
const docStatusLabel: Record<string, string> = {
  ready: '已就绪',
  indexing: '向量化中',
  pending: '待处理',
  failed: '失败'
}

async function loadDatasets() {
  loading.value = true
  try {
    const data = await knowledgeApi.datasetPage({ size: 100 })
    datasets.value = data.records
    if (selectedDatasetId.value === null && datasets.value.length > 0) {
      selectDataset(datasets.value[0])
    }
  } finally {
    loading.value = false
  }
}

function selectDataset(ds: KnowledgeDataset) {
  selectedDatasetId.value = ds.id
  activeTab.value = 'datasets'
  loadDocuments()
}

async function loadDocuments() {
  if (!selectedDatasetId.value) return
  loadingDocs.value = true
  try {
    documents.value = (await knowledgeApi.documentPage(selectedDatasetId.value, { size: 100 })).records
  } finally {
    loadingDocs.value = false
  }
}

async function loadModels() {
  embeddingModels.value = await modelApi.embeddingModels().catch(() => [])
  rerankModels.value = await modelApi.rerankModels().catch(() => [])
}

// ---------- 数据集 CRUD ----------
function openCreateDs() {
  dsDialogTitle.value = '新建数据集'
  dsForm.value = {
    name: '',
    description: '',
    embeddingModel: embeddingModels.value[0]?.id,
    chunkSize: 500,
    chunkOverlap: 50,
    status: 1
  }
  dsDialogVisible.value = true
}

function openEditDs(ds: KnowledgeDataset) {
  dsDialogTitle.value = '编辑数据集'
  dsForm.value = { ...ds }
  dsDialogVisible.value = true
}

async function saveDs() {
  if (!dsForm.value.name) {
    ElMessage.warning('请输入数据集名称')
    return
  }
  if (!dsForm.value.embeddingModel) {
    ElMessage.warning('请选择向量化模型')
    return
  }
  dsSaving.value = true
  try {
    if (dsForm.value.id) {
      await knowledgeApi.updateDataset(dsForm.value.id, dsForm.value)
      ElMessage.success('更新成功')
    } else {
      const created = await knowledgeApi.createDataset(dsForm.value)
      ElMessage.success('创建成功')
      datasets.value.unshift(created)
      selectDataset(created)
    }
    dsDialogVisible.value = false
    await loadDatasets()
  } finally {
    dsSaving.value = false
  }
}

async function removeDs(ds: KnowledgeDataset) {
  try {
    await ElMessageBox.confirm(
      `确认删除数据集「${ds.name}」？所有文档与分块将一并删除，不可恢复。`,
      '删除确认',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await knowledgeApi.removeDataset(ds.id)
  ElMessage.success('已删除')
  if (selectedDatasetId.value === ds.id) {
    selectedDatasetId.value = null
  }
  await loadDatasets()
}

// ---------- 文档 ----------
function openUpload() {
  uploadMode.value = 'file'
  uploadForm.value = { name: '', content: '' }
  fileList.value = []
  uploadFileObj.value = null
  uploadVisible.value = true
}

function onFileChange(file: UploadFile, files: UploadUserFile[]) {
  uploadFileObj.value = file.raw ?? null
  fileList.value = files.slice(-1)
}

function onFileExceed() {
  ElMessage.warning('一次只能上传一个文件')
}

async function doUpload() {
  if (!selectedDatasetId.value) return
  if (uploadMode.value === 'file') {
    if (!uploadFileObj.value) {
      ElMessage.warning('请选择要上传的文件')
      return
    }
    uploading.value = true
    try {
      await knowledgeApi.uploadDocument(selectedDatasetId.value, uploadFileObj.value)
      ElMessage.success('文件已上传，正在解析并向量化')
      uploadVisible.value = false
      fileList.value = []
      uploadFileObj.value = null
      await loadDocuments()
    } catch {
      /* 错误提示由拦截器统一处理 */
    } finally {
      uploading.value = false
    }
    return
  }
  if (!uploadForm.value.content) {
    ElMessage.warning('请输入文档内容')
    return
  }
  if (!uploadForm.value.name) {
    uploadForm.value.name = `文档-${Date.now()}`
  }
  uploading.value = true
  try {
    await knowledgeApi.createDocument(selectedDatasetId.value, {
      name: uploadForm.value.name,
      content: uploadForm.value.content
    })
    ElMessage.success('文档已上传并开始向量化')
    uploadVisible.value = false
    await loadDocuments()
  } finally {
    uploading.value = false
  }
}

async function reindexDoc(doc: KnowledgeDocument) {
  await ElMessageBox.confirm(`重新对文档「${doc.name}」进行向量化？`, '重试确认', {
    type: 'warning'
  }).catch(() => null).then(async (v) => {
    if (!v) return
    await knowledgeApi.reindexDocument(doc.id)
    ElMessage.success('已重新提交向量化')
    await loadDocuments()
  })
}

async function removeDoc(doc: KnowledgeDocument) {
  try {
    await ElMessageBox.confirm(`确认删除文档「${doc.name}」？`, '删除确认', {
      type: 'error', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await knowledgeApi.removeDocument(doc.id)
  ElMessage.success('已删除')
  await loadDocuments()
}

async function viewChunks(doc: KnowledgeDocument) {
  chunkDocName.value = doc.name
  chunkDrawerVisible.value = true
  loadingChunks.value = true
  try {
    chunks.value = await knowledgeApi.chunks(doc.id)
  } finally {
    loadingChunks.value = false
  }
}

// ---------- 检索 ----------
async function doSearch() {
  if (!selectedDatasetId.value) return
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }
  searching.value = true
  try {
    searchHits.value = await knowledgeApi.search(selectedDatasetId.value, {
      query: searchQuery.value,
      topK: searchTopK.value,
      rerankModelId: searchRerankId.value
    })
  } finally {
    searching.value = false
  }
}

onMounted(async () => {
  await loadModels()
  await loadDatasets()
})
</script>

<template>
  <div class="kb-page">
    <div class="kb-layout">
      <!-- 左侧数据集列表 -->
      <aside class="ds-panel">
        <div class="ds-head">
          <span class="ds-head-title">数据集</span>
          <el-button type="primary" size="small" class="btn-gradient" :icon="Plus" @click="openCreateDs">新建</el-button>
        </div>
        <el-scrollbar class="ds-scroll">
          <div
            v-for="ds in datasets"
            :key="ds.id"
            class="ds-item"
            :class="{ active: ds.id === selectedDatasetId }"
            @click="selectDataset(ds)"
          >
            <el-icon class="ds-icon"><FolderOpened /></el-icon>
            <div class="ds-info">
              <div class="ds-name">{{ ds.name }}</div>
              <div class="ds-desc">{{ ds.description || '无描述' }}</div>
            </div>
            <el-dropdown trigger="click" @click.stop>
              <el-icon class="ds-more"><ChatDotRound /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openEditDs(ds)">编辑</el-dropdown-item>
                  <el-dropdown-item @click="removeDs(ds)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <el-empty
            v-if="!loading && datasets.length === 0"
            description="暂无数据集"
            :image-size="60"
          />
        </el-scrollbar>
      </aside>

      <!-- 右侧详情 -->
      <div class="detail-panel">
        <template v-if="selectedDataset">
          <div class="detail-head">
            <div>
              <span class="detail-title">{{ ds.name }}</span>
              <el-tag size="small" type="info" effect="plain">{{ ds.description || '无描述' }}</el-tag>
            </div>
            <el-tabs v-model="activeTab" class="detail-tabs">
              <el-tab-pane label="文档管理" name="documents" />
              <el-tab-pane label="检索测试" name="search" />
              <el-tab-pane label="数据集设置" name="datasets" />
            </el-tabs>
          </div>

          <!-- 文档管理 -->
          <div v-show="activeTab === 'documents'" class="tab-content">
            <div class="toolbar">
              <span class="toolbar-count">共 {{ documents.length }} 个文档</span>
              <el-button type="primary" class="btn-gradient" :icon="Upload" @click="openUpload">上传文档</el-button>
            </div>
            <el-card shadow="never">
              <el-table v-loading="loadingDocs" :data="documents" style="width:100%">
                <el-table-column prop="name" label="文档名称" min-width="180" />
                <el-table-column label="字符数" width="100">
                  <template #default="{ row }">{{ row.charCount }}</template>
                </el-table-column>
                <el-table-column label="分块数" width="100">
                  <template #default="{ row }">{{ row.chunkCount }}</template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="(docStatusType[row.status] as any) || 'info'">
                      {{ docStatusLabel[row.status] || row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="updateTime" label="更新时间" width="170" />
                <el-table-column label="操作" width="230" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="viewChunks(row)">分块预览</el-button>
                    <el-button link type="warning" :icon="Refresh" @click="reindexDoc(row)">重建</el-button>
                    <el-button link type="danger" :icon="Delete" @click="removeDoc(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </div>

          <!-- 检索测试 -->
          <div v-show="activeTab === 'search'" class="tab-content">
            <el-card shadow="never" class="search-card">
              <div class="search-bar">
                <el-input
                  v-model="searchQuery"
                  placeholder="输入检索问题"
                  @keydown.enter="doSearch"
                />
                <el-input-number v-model="searchTopK" :min="1" :max="20" controls-position="right" style="width:110px" />
                <el-select
                  v-model="searchRerankId"
                  placeholder="重排序模型(可选)"
                  clearable
                  style="width:200px"
                >
                  <el-option
                    v-for="m in rerankModels"
                    :key="m.id"
                    :label="`${m.providerName} / ${m.modelName}`"
                    :value="m.id"
                  />
                </el-select>
                <el-button type="primary" :icon="Search" :loading="searching" @click="doSearch">检索</el-button>
              </div>
              <div class="search-result">
                <el-empty v-if="searchHits.length === 0" description="输入问题后检索相关分块" :image-size="80" />
                <div v-for="(hit, i) in searchHits" :key="hit.id" class="hit-item">
                  <div class="hit-head">
                    <el-tag size="small" type="success">#{{ i + 1 }}</el-tag>
                    <span class="hit-score">相似度 {{ (hit.score * 100).toFixed(2) }}%</span>
                    <span class="hit-meta">文档 #{{ hit.documentId }} · 块 #{{ hit.chunkIndex }}</span>
                  </div>
                  <div class="hit-content">{{ hit.content }}</div>
                </div>
              </div>
            </el-card>
          </div>

          <!-- 数据集设置 -->
          <div v-show="activeTab === 'datasets'" class="tab-content">
            <el-card shadow="never" class="setting-card">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="名称">{{ ds.name }}</el-descriptions-item>
                <el-descriptions-item label="描述">{{ ds.description || '-' }}</el-descriptions-item>
                <el-descriptions-item label="向量化模型">
                  {{ embeddingModels.find(m => m.id === ds.embeddingModel)?.modelName || ds.embeddingModel }}
                </el-descriptions-item>
                <el-descriptions-item label="分块大小">{{ ds.chunkSize }} 字符</el-descriptions-item>
                <el-descriptions-item label="分块重叠">{{ ds.chunkOverlap }} 字符</el-descriptions-item>
              </el-descriptions>
              <div style="margin-top:16px">
                <el-button type="primary" @click="openEditDs(ds)">编辑设置</el-button>
              </div>
            </el-card>
          </div>
        </template>
        <el-empty v-else description="请选择或创建一个数据集" :image-size="120" class="empty-center" />
      </div>
    </div>

    <!-- 数据集编辑对话框 -->
    <el-dialog v-model="dsDialogVisible" :title="dsDialogTitle" width="520px">
      <el-form :model="dsForm" label-width="110px">
        <el-form-item label="数据集名称" required>
          <el-input v-model="dsForm.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dsForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="向量化模型" required>
          <el-select v-model="dsForm.embeddingModel" placeholder="选择 embedding 模型" style="width:100%">
            <el-option
              v-for="m in embeddingModels"
              :key="m.id"
              :label="`${m.providerName} / ${m.modelName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分块大小">
          <el-input-number v-model="dsForm.chunkSize" :min="100" :max="4000" :step="100" />
          <span class="form-hint">字符数</span>
        </el-form-item>
        <el-form-item label="分块重叠">
          <el-input-number v-model="dsForm.chunkOverlap" :min="0" :max="500" :step="10" />
          <span class="form-hint">字符数</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dsSaving" @click="saveDs">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文档上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传文档" width="640px">
      <el-tabs v-model="uploadMode">
        <el-tab-pane label="文件上传" name="file">
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            accept=".txt,.md,.markdown,.pdf,.docx"
            :file-list="fileList"
            :on-change="onFileChange"
            :on-exceed="onFileExceed"
            class="upload-drag"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">
                支持 txt / md / pdf / docx，上传后自动提取文本、分块并向量化
              </div>
            </template>
          </el-upload>
        </el-tab-pane>
        <el-tab-pane label="文本粘贴" name="text">
          <el-form :model="uploadForm" label-width="80px">
            <el-form-item label="文档名称">
              <el-input v-model="uploadForm.name" placeholder="留空自动生成" />
            </el-form-item>
            <el-form-item label="文档内容" required>
              <el-input
                v-model="uploadForm.content"
                type="textarea"
                :rows="10"
                placeholder="粘贴文本内容，提交后将自动分块并向量化"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button
          type="primary"
          class="btn-gradient"
          :loading="uploading"
          :disabled="uploadMode === 'file' && !uploadFileObj"
          @click="doUpload"
        >
          {{ uploadMode === 'file' ? '上传并向量化' : '提交并向量化' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 分块预览抽屉 -->
    <el-drawer v-model="chunkDrawerVisible" :title="`分块预览 - ${chunkDocName}`" size="560px">
      <div v-loading="loadingChunks">
        <div v-for="c in chunks" :key="c.id" class="chunk-item">
          <div class="chunk-head">
            <el-tag size="small">块 #{{ c.chunkIndex }}</el-tag>
            <span class="chunk-count">{{ c.charCount }} 字符</span>
          </div>
          <div class="chunk-content">{{ c.content }}</div>
        </div>
        <el-empty v-if="!loadingChunks && chunks.length === 0" description="暂无分块" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.kb-page {
  height: 100%;
  padding: 20px 24px;
}
.kb-layout {
  display: flex;
  gap: 16px;
  height: 100%;
}
/* 数据集面板 */
.ds-panel {
  width: 280px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ds-head {
  height: 52px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-color);
}
.ds-head-title {
  font-size: 14px;
  font-weight: 600;
}
.ds-scroll {
  flex: 1;
  min-height: 0;
  padding: 8px;
}
.ds-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  cursor: pointer;
  border-radius: 10px;
  margin-bottom: 4px;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}
.ds-item:hover {
  background: var(--bg-page);
}
.ds-item.active {
  background: var(--brand-gradient-soft);
  border-color: rgba(91, 108, 255, 0.3);
}
.ds-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.ds-info {
  flex: 1;
  min-width: 0;
}
.ds-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ds-desc {
  font-size: 11px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ds-more {
  color: var(--text-tertiary);
  cursor: pointer;
  opacity: 0;
  transition: all 0.18s ease;
}
.ds-item:hover .ds-more {
  opacity: 1;
}
.ds-more:hover {
  color: var(--brand-1);
}
/* 详情面板 */
.detail-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.detail-head {
  padding: 16px 18px 0;
  border-bottom: 1px solid var(--border-color);
  background: #fff;
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
  margin-right: 8px;
}
.detail-tabs {
  margin-top: 8px;
}
.detail-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.detail-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--border-color);
}
.tab-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
}
.empty-center {
  margin-top: 12%;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.toolbar-count {
  color: var(--text-tertiary);
  font-size: 13px;
}
/* 检索 */
.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.search-bar .el-input {
  flex: 1;
}
.search-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.hit-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  background: #fff;
  box-shadow: var(--shadow-card);
  transition: all 0.2s ease;
}
.hit-item:hover {
  border-color: rgba(91, 108, 255, 0.35);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.hit-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.hit-score {
  font-size: 12px;
  color: #10b981;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(16, 185, 129, 0.1);
}
.hit-meta {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-tertiary);
}
.hit-content {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
/* 设置 */
.setting-card {
  max-width: 640px;
}
/* 分块预览 */
.chunk-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  margin-bottom: 10px;
  background: #fff;
  box-shadow: var(--shadow-card);
  transition: all 0.2s ease;
}
.chunk-item:hover {
  border-color: rgba(91, 108, 255, 0.3);
}
.chunk-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.chunk-count {
  font-size: 11px;
  color: var(--text-tertiary);
}
.chunk-content {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.form-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.upload-drag {
  padding: 8px 0;
}
</style>
