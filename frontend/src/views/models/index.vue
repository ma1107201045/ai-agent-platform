<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { modelApi } from '@/api/model'
import type { ModelInfo, ModelProvider } from '@/api/types'
import { copyText } from '@/utils/clipboard'
import { fmtTime, maskKey, providerTypeLabel } from './modelMeta'
import ProviderDialog from './components/ProviderDialog.vue'
import ModelDialog from './components/ModelDialog.vue'
import ModelTestDialog from './components/ModelTestDialog.vue'
import ProviderModelsPanel from './components/ProviderModelsPanel.vue'

// ---------------- 供应商列表 ----------------

const loading = ref(false)
const providers = ref<ModelProvider[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const expandedModels = reactive<Record<number, ModelInfo[]>>({})
const expandedLoading = reactive<Record<number, boolean>>({})
/** 当前展开的供应商行 id（与 el-table 的 expand-row-keys 受控同步） */
const expandKeys = ref<number[]>([])

/** 拉取某供应商下的模型并写入展开缓存 */
async function loadModels(providerId: number) {
  expandedLoading[providerId] = true
  try {
    expandedModels[providerId] = await modelApi.modelsOf(providerId)
  } catch {
    expandedModels[providerId] = []
  } finally {
    expandedLoading[providerId] = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await modelApi.providerPage({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined
    })
    providers.value = data.records
    total.value = data.total

    // 只保留仍存在于当前页的展开行，避免过期 id 残留
    const liveIds = new Set(data.records.map((p) => p.id))
    expandKeys.value = expandKeys.value.filter((id) => liveIds.has(id))

    // 清理已不在当前页的模型缓存
    for (const k of Object.keys(expandedModels)) {
      if (!liveIds.has(Number(k))) delete expandedModels[Number(k)]
    }
    for (const k of Object.keys(expandedLoading)) {
      if (!liveIds.has(Number(k))) delete expandedLoading[Number(k)]
    }

    // 对仍处于展开状态的供应商重新拉取模型，避免刷新后模型列表丢失
    for (const id of expandKeys.value) {
      loadModels(id)
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onProviderExpand(row: ModelProvider, expandedRows: ModelProvider[]) {
  // 同步受控展开状态
  expandKeys.value = expandedRows.map((r) => r.id)
  const expanded = expandedRows.some((r) => r.id === row.id)
  if (expanded && !expandedModels[row.id]) {
    loadModels(row.id)
  }
}

async function refreshModels(providerId: number) {
  expandedModels[providerId] = await modelApi.modelsOf(providerId)
}

// 供应商状态开关（乐观更新，失败回滚）
async function onProviderStatusChange(row: ModelProvider) {
  try {
    await modelApi.updateProvider(row.id, { status: row.status })
  } catch {
    load()
  }
}

function removeProvider(row: ModelProvider) {
  ElMessageBox.confirm(`删除供应商「${row.name}」将级联删除其下全部模型，确认？`, '删除确认', {
    type: 'error',
    confirmButtonText: '删除'
  })
    .then(async () => {
      await modelApi.removeProvider(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

// ---------------- 模型行操作 ----------------

// 模型状态开关（乐观更新，失败回滚）
async function onModelStatusChange(m: ModelInfo) {
  try {
    await modelApi.updateModel(m.id, { status: m.status })
  } catch {
    refreshModels(m.providerId)
  }
}

function removeModel(m: ModelInfo) {
  ElMessageBox.confirm(`删除模型「${m.name}」？`, '删除确认', {
    type: 'error',
    confirmButtonText: '删除'
  })
    .then(async () => {
      await modelApi.removeModel(m.id)
      ElMessage.success('删除成功')
      if (expandedModels[m.providerId]) {
        expandedModels[m.providerId] = expandedModels[m.providerId].filter((x) => x.id !== m.id)
      }
    })
    .catch(() => {})
}

// ---------------- 子组件引用 ----------------

const providerDialogRef = ref<InstanceType<typeof ProviderDialog> | null>(null)
const modelDialogRef = ref<InstanceType<typeof ModelDialog> | null>(null)
const testDialogRef = ref<InstanceType<typeof ModelTestDialog> | null>(null)

onMounted(load)
</script>

<template>
  <div class="page-container models-page">
    <!-- 头部 -->
    <div class="models-head">
      <div>
        <h2 class="head-title">模型管理</h2>
        <p class="head-desc">配置 LLM / 向量 / 重排等模型供应商，共 {{ total }} 个供应商</p>
      </div>
      <div class="head-actions">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索名称 / 类型 / API 地址"
          clearable
          @keyup.enter="onSearch"
          @clear="onSearch"
        >
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <el-tooltip content="刷新">
          <el-button circle @click="load"
            ><el-icon><Refresh /></el-icon
          ></el-button>
        </el-tooltip>
        <el-button type="primary" class="btn-gradient" @click="providerDialogRef?.openCreate()">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>添加供应商
        </el-button>
      </div>
    </div>

    <!-- 供应商列表 -->
    <el-card shadow="never" class="models-card">
      <el-table
        v-loading="loading"
        :data="providers"
        row-key="id"
        :expand-row-keys="expandKeys"
        class="providers-table"
        @expand-change="onProviderExpand"
      >
        <el-table-column type="expand" width="44">
          <template #default="{ row }">
            <ProviderModelsPanel
              :provider="row"
              :models="expandedModels[row.id] || []"
              :loading="expandedLoading[row.id]"
              @create-model="modelDialogRef?.openCreate($event)"
              @edit-model="(m, p) => modelDialogRef?.openEdit(m, p)"
              @remove-model="removeModel"
              @test-model="(m, p, mode) => testDialogRef?.open(m, p, mode)"
              @status-change="onModelStatusChange"
            />
          </template>
        </el-table-column>

        <el-table-column label="供应商" min-width="230">
          <template #default="{ row }">
            <div class="provider-cell">
              <div class="provider-badge">{{ (row.name || '?').charAt(0).toUpperCase() }}</div>
              <div class="provider-text">
                <div class="provider-name-row">
                  <span class="provider-name" :title="row.name">{{ row.name }}</span>
                  <el-tag
                    size="small"
                    :type="row.type === 'openai-compatible' ? 'success' : 'info'"
                    effect="light"
                  >
                    {{ providerTypeLabel(row.type) }}
                  </el-tag>
                </div>
                <div class="provider-id">ID {{ row.id }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="API 地址" min-width="240">
          <template #default="{ row }">
            <div class="kv-cell">
              <span
                class="mono kv-value"
                :class="{ dim: !row.baseUrl }"
                :title="row.baseUrl || ''"
                >{{ row.baseUrl || '未配置' }}</span
              >
              <el-button
                v-if="row.baseUrl"
                link
                type="primary"
                @click="copyText(row.baseUrl, '已复制')"
                >复制</el-button
              >
            </div>
          </template>
        </el-table-column>

        <el-table-column label="API Key" min-width="200">
          <template #default="{ row }">
            <div class="kv-cell">
              <span class="mono kv-value" :class="{ dim: !row.apiKey }">{{
                maskKey(row.apiKey) || '未配置'
              }}</span>
              <el-button
                v-if="row.apiKey"
                link
                type="primary"
                @click="copyText(row.apiKey, '已复制')"
                >复制</el-button
              >
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="86" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="onProviderStatusChange(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">
            <span class="muted">{{ fmtTime(row.updateTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="110" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="providerDialogRef?.openEdit(row)"
              >编辑</el-button
            >
            <el-button link type="danger" @click="removeProvider(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="load"
      />
    </el-card>

    <ProviderDialog ref="providerDialogRef" @saved="load" />
    <ModelDialog ref="modelDialogRef" @saved="refreshModels" />
    <ModelTestDialog ref="testDialogRef" />
  </div>
</template>

<style scoped>
.models-page {
  max-width: 1280px;
  margin: 0 auto;
}
.models-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
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
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.search-input {
  width: 240px;
}
.models-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

/* 供应商单元格 */
.provider-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.provider-cell > .provider-text {
  min-width: 0;
}
.provider-badge {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-gradient);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.25) inset,
    0 2px 6px rgba(31, 35, 45, 0.15);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}
.provider-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.provider-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.provider-name-row .el-tag {
  flex: none;
}
.provider-id {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kv-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.kv-cell .el-button {
  flex: none;
}
.kv-value {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kv-value.dim {
  color: var(--text-tertiary);
}

/* 展开面板 */
:deep(.providers-table .el-table__expanded-cell) {
  padding: 0;
}

.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
</style>
