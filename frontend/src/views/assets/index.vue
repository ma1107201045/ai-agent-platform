<template>
  <div class="page-container assets-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head-info">
        <h2 class="head-title">素材管理</h2>
        <p class="head-desc">
          集中存放图片、文档、音视频等素材文件，支持分类筛选、搜索、下载与引用；素材可被对话渲染与知识库引用。
        </p>
      </div>
      <div class="head-actions">
        <el-button type="primary" @click="uploadDialogVisible = true">
          <el-icon><Upload /></el-icon>&nbsp;上传素材
        </el-button>
      </div>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar hover-card">
      <div class="category-tabs">
        <button
          v-for="c in categories"
          :key="c.value"
          class="category-tab"
          :class="{ active: categoryFilter === c.value }"
          @click="changeCategory(c.value)"
        >
          <el-icon v-if="c.value !== ''" class="cat-icon"><component :is="c.icon" /></el-icon>
          {{ c.label }}
        </button>
      </div>
      <div class="filter-right">
        <el-input v-model="keyword" placeholder="搜索素材名称" clearable class="search-input" @keyup.enter="reload" @clear="reload">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="reload"><el-icon><Refresh /></el-icon></el-button>
      </div>
    </div>

    <!-- 素材网格 -->
    <div v-loading="loading" class="asset-area">
      <div v-if="!assets.length && !loading" class="hover-card empty-guide">
        <el-empty description="还没有素材，点击右上角「上传素材」开始" />
      </div>
      <div v-else class="asset-grid">
        <div v-for="asset in assets" :key="asset.id" class="asset-card hover-card" @click="openDetail(asset)">
          <div class="asset-preview" :class="`cat-${asset.category}`">
            <el-icon v-if="categoryIconComp(asset.category)">
              <component :is="categoryIconComp(asset.category)" />
            </el-icon>
            <span v-if="asset.ext" class="ext-badge">{{ asset.ext }}</span>
          </div>
          <div class="asset-info">
            <p class="asset-name" :title="asset.name">{{ asset.name }}</p>
            <p class="asset-meta">
              <span>{{ formatSize(asset.size) }}</span>
              <span class="dot">·</span>
              <span>{{ assetCategoryText[asset.category] }}</span>
              <span class="dot">·</span>
              <span>{{ formatTime(asset.createTime) }}</span>
            </p>
          </div>
          <div class="asset-actions" @click.stop>
            <el-tooltip content="预览/查看" placement="top">
              <button class="round-btn" @click="openDetail(asset)"><el-icon><View /></el-icon></button>
            </el-tooltip>
            <el-tooltip content="下载" placement="top">
              <button class="round-btn" @click="download(asset)"><el-icon><Download /></el-icon></button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top">
              <button class="round-btn" @click="openEdit(asset)"><el-icon><EditPen /></el-icon></button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <button class="round-btn danger" @click="removeAsset(asset)"><el-icon><Delete /></el-icon></button>
            </el-tooltip>
          </div>
        </div>
      </div>
      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[12, 24, 48]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="reload"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadDialogVisible" title="上传素材" width="600px" destroy-on-close>
      <div class="upload-form">
        <el-form label-width="80px">
          <el-form-item label="分类">
            <el-select v-model="uploadCategory" class="full-width">
              <el-option value="" label="自动识别" />
              <el-option value="image" label="图片" />
              <el-option value="document" label="文档" />
              <el-option value="audio" label="音频" />
              <el-option value="video" label="视频" />
              <el-option value="other" label="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="选择文件">
            <el-upload
              drag
              multiple
              :auto-upload="false"
              :limit="20"
              :on-change="onFilesChange"
              :on-exceed="() => ElMessage.warning('单次最多上传 20 个文件')"
              class="uploader"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em>（可多选）</div>
              <template #tip>
                <div class="el-upload__tip">支持图片 / 文档 / 音视频等，单个文件不超过 20MB。</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
        <div v-if="pendingFiles.length" class="file-list">
          <div v-for="(f, i) in pendingFiles" :key="i" class="file-row">
            <el-icon class="file-icon"><Paperclip /></el-icon>
            <span class="file-name" :title="f.name">{{ f.name }}</span>
            <span class="file-size">{{ formatSize(f.size) }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!pendingFiles.length" @click="doUpload">
          开始上传（{{ pendingFiles.length }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- 预览 / 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="素材详情" width="720px" destroy-on-close @closed="releasePreview">
      <div v-loading="previewLoading" class="detail-body">
        <template v-if="detail">
          <div class="preview-area" v-if="previewUrl && detail.category === 'image'">
            <img :src="previewUrl" alt="预览" class="preview-image" />
          </div>
          <div class="preview-area" v-else-if="previewUrl && detail.category === 'video'">
            <video :src="previewUrl" controls class="preview-video" />
          </div>
          <div class="preview-area" v-else-if="previewUrl && detail.category === 'audio'">
            <audio :src="previewUrl" controls class="preview-audio" />
          </div>
          <div class="preview-area file-preview" v-else>
            <el-icon class="file-preview-icon">
              <component :is="categoryIconComp(detail.category)" />
            </el-icon>
            <span class="file-preview-ext">{{ detail.ext || 'FILE' }}</span>
          </div>

          <div class="detail-info">
            <h3 class="detail-name" :title="detail.name">{{ detail.name }}</h3>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">原始文件</span>
                <span class="info-value">{{ detail.originalName || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">类型</span>
                <span class="info-value">{{ assetCategoryText[detail.category] }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">大小</span>
                <span class="info-value">{{ formatSize(detail.size) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">上传时间</span>
                <span class="info-value">{{ formatTime(detail.createTime) }}</span>
              </div>
            </div>
            <div class="detail-actions">
              <el-button type="primary" :disabled="!previewUrl" @click="download(detail)">
                <el-icon><Download /></el-icon>&nbsp;下载
              </el-button>
              <el-button :disabled="!previewUrl" @click="copyLink">
                <el-icon><Link /></el-icon>&nbsp;复制引用地址
              </el-button>
              <el-button @click="openEdit(detail)"><el-icon><EditPen /></el-icon>&nbsp;编辑</el-button>
              <el-button type="danger" plain @click="removeAsset(detail)"><el-icon><Delete /></el-icon>&nbsp;删除</el-button>
            </div>
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑素材" width="480px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="editForm.name" placeholder="素材展示名称" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" class="full-width">
            <el-option value="image" label="图片" />
            <el-option value="document" label="文档" />
            <el-option value="audio" label="音频" />
            <el-option value="video" label="视频" />
            <el-option value="other" label="其他" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Delete,
  Download,
  Document,
  EditPen,
  Folder,
  Headset,
  Link,
  Paperclip,
  Picture,
  Refresh,
  Search,
  Upload,
  UploadFilled,
  VideoCamera,
  View
} from '@element-plus/icons-vue'
import { assetsApi, assetCategoryText, assetContentUrl, downloadAsset } from '@/api/assets'
import type { AssetCategory, AssetFile } from '@/api/types'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AnyIcon = any

const categories: { value: string; label: string; icon?: AnyIcon }[] = [
  { value: '', label: '全部', icon: undefined },
  { value: 'image', label: '图片', icon: Picture },
  { value: 'document', label: '文档', icon: Document },
  { value: 'audio', label: '音频', icon: Headset },
  { value: 'video', label: '视频', icon: VideoCamera },
  { value: 'other', label: '其他', icon: Folder }
]

const categoryIconComp = (c: string) =>
  ({ image: Picture, document: Document, audio: Headset, video: VideoCamera, other: Folder } as Record<string, AnyIcon>)[c] ||
  Folder

// ---------- 列表 ----------
const loading = ref(false)
const assets = ref<AssetFile[]>([])
const categoryFilter = ref('')
const keyword = ref('')
const page = ref(1)
const size = ref(24)
const total = ref(0)

async function reload() {
  loading.value = true
  try {
    const res = await assetsApi.page({
      page: page.value,
      size: size.value,
      category: categoryFilter.value || undefined,
      keyword: keyword.value || undefined
    })
    assets.value = res.records || []
    total.value = Number(res.total || 0)
  } finally {
    loading.value = false
  }
}

function changeCategory(v: string) {
  categoryFilter.value = v
  page.value = 1
  reload()
}

function onSizeChange() {
  page.value = 1
  reload()
}

const formatSize = (bytes: number) => {
  if (!bytes && bytes !== 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const formatTime = (t?: string) => (t ? t.replace('T', ' ').slice(0, 16) : '—')

// ---------- 上传 ----------
const uploadDialogVisible = ref(false)
const uploadCategory = ref('')
const pendingFiles = ref<File[]>([])
const uploading = ref(false)

function onFilesChange(_file: { raw?: File }, files: { raw?: File }[]) {
  pendingFiles.value = files
    .map((f) => f.raw)
    .filter((f): f is File => !!f)
}

async function doUpload() {
  if (!pendingFiles.value.length) return
  uploading.value = true
  try {
    for (const file of pendingFiles.value) {
      await assetsApi.upload(file, undefined, uploadCategory.value || undefined)
    }
    ElMessage.success(`成功上传 ${pendingFiles.value.length} 个文件`)
    uploadDialogVisible.value = false
    pendingFiles.value = []
    uploadCategory.value = ''
    page.value = 1
    await reload()
  } finally {
    uploading.value = false
  }
}

// ---------- 预览 / 详情 ----------
const detailVisible = ref(false)
const detail = ref<AssetFile | null>(null)
const previewLoading = ref(false)
const previewUrl = ref('')

async function openDetail(asset: AssetFile) {
  detail.value = asset
  detailVisible.value = true
  previewLoading.value = true
  try {
    releasePreview()
    const { url } = await assetsApi.fetchBlob(asset.id)
    previewUrl.value = url
  } catch (e) {
    ElMessage.error((e as Error).message || '素材加载失败')
  } finally {
    previewLoading.value = false
  }
}

function releasePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

async function download(asset: AssetFile) {
  try {
    let url = previewUrl.value
    if (!url || detail.value?.id !== asset.id) {
      const res = await assetsApi.fetchBlob(asset.id)
      url = res.url
    }
    downloadAsset(asset, url)
  } catch (e) {
    ElMessage.error((e as Error).message || '下载失败')
  }
}

async function copyLink() {
  const asset = detail.value
  if (!asset) return
  try {
    await navigator.clipboard.writeText(assetContentUrl(asset.id))
    ElMessage.success('引用地址已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制：' + assetContentUrl(asset.id))
  }
}

// ---------- 编辑 ----------
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive<{ id?: number; name: string; category: AssetCategory }>({ id: undefined, name: '', category: 'other' })

function openEdit(asset: AssetFile) {
  editForm.id = asset.id
  editForm.name = asset.name
  editForm.category = asset.category
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.id) return
  saving.value = true
  try {
    await assetsApi.update(editForm.id, { name: editForm.name.trim(), category: editForm.category })
    ElMessage.success('素材已更新')
    editVisible.value = false
    await reload()
    if (detail.value?.id === editForm.id) {
      detail.value = await assetsApi.get(editForm.id)
    }
  } finally {
    saving.value = false
  }
}

// ---------- 删除 ----------
async function removeAsset(asset: AssetFile) {
  await ElMessageBox.confirm(`确定删除素材「${asset.name}」吗？删除后文件与记录均无法恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await assetsApi.remove(asset.id)
  ElMessage.success('素材已删除')
  if (detail.value?.id === asset.id) {
    detailVisible.value = false
    detail.value = null
  }
  await reload()
}

onMounted(reload)
</script>

<style scoped>
.assets-page {
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

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin: 16px 0;
}
.category-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
}
.category-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  padding: 6px 14px;
  border-radius: 18px;
  font-size: 13px;
  color: var(--text-secondary, #4a4e57);
  cursor: pointer;
  transition: all 0.2s;
}
.category-tab:hover {
  background: var(--bg-hover, #f0f2f5);
}
.category-tab.active {
  background: var(--primary-color, #3458fa);
  color: #fff;
}
.cat-icon {
  font-size: 15px;
}
.filter-right {
  display: flex;
  gap: 8px;
  align-items: center;
}
.search-input {
  width: 240px;
}

.asset-area {
  min-height: 300px;
}
.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 14px;
}
.asset-card {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  cursor: pointer;
  padding: 0;
}
.asset-preview {
  height: 130px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44px;
  color: #fff;
  position: relative;
}
.asset-preview.cat-image {
  background: linear-gradient(135deg, #7c5dfa, #00a8ff);
}
.asset-preview.cat-document {
  background: linear-gradient(135deg, #3458fa, #7c5dfa);
}
.asset-preview.cat-audio {
  background: linear-gradient(135deg, #ff8a50, #ff5f8f);
}
.asset-preview.cat-video {
  background: linear-gradient(135deg, #12b886, #04a7a8);
}
.asset-preview.cat-other {
  background: linear-gradient(135deg, #8a94a6, #6b7280);
}
.ext-badge {
  position: absolute;
  right: 10px;
  bottom: 8px;
  font-size: 11px;
  text-transform: uppercase;
  background: rgba(0, 0, 0, 0.28);
  padding: 1px 7px;
  border-radius: 8px;
}
.asset-info {
  padding: 10px 12px 12px;
}
.asset-name {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.asset-meta {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
.dot {
  margin: 0 4px;
}
.asset-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
  opacity: 0;
  transform: translateY(-6px);
  transition: all 0.2s;
}
.asset-card:hover .asset-actions {
  opacity: 1;
  transform: translateY(0);
}
.round-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.92);
  color: #4a4e57;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
  transition: all 0.15s;
}
.round-btn:hover {
  color: #3458fa;
}
.round-btn.danger:hover {
  color: #f56c6c;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
.empty-guide {
  margin-top: 20px;
  padding: 30px 0;
}

.uploader {
  width: 100%;
}
.upload-form .full-width {
  width: 100%;
}
.file-list {
  margin-top: 8px;
  border: 1px solid var(--border-color, #eef0f3);
  border-radius: 10px;
  max-height: 180px;
  overflow: auto;
  padding: 4px 10px;
}
.file-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border-color, #eef0f3);
}
.file-row:last-child {
  border-bottom: none;
}
.file-icon {
  color: #3458fa;
}
.file-name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size {
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}

.preview-area {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f1013;
  border-radius: 12px;
  min-height: 260px;
  max-height: 420px;
  overflow: hidden;
  position: relative;
}
.preview-image {
  max-width: 100%;
  max-height: 420px;
  object-fit: contain;
}
.preview-video,
.preview-audio {
  width: 100%;
}
.file-preview {
  flex-direction: column;
  gap: 8px;
  color: #fff;
  font-size: 72px;
  background: linear-gradient(135deg, #23262c, #2b2f36);
}
.file-preview-ext {
  font-size: 13px;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.7);
}
.detail-info {
  padding-top: 14px;
}
.detail-name {
  margin: 0 0 12px;
  font-size: 17px;
  font-weight: 700;
}
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px 16px;
  margin-bottom: 14px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.info-label {
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
.info-value {
  font-size: 13px;
  color: var(--text-primary);
  word-break: break-all;
}
.detail-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
