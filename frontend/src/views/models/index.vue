<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '@/api/model'
import type { ModelInfo, ModelProvider, ModelType, Usage } from '@/api/types'

// ---------------- 常量与映射 ----------------

/** 模型类型元信息：值 -> 中文名 / tag 颜色 */
const MODEL_TYPES: { value: ModelType; label: string; tag: '' | 'success' | 'warning' | 'info' | 'danger' | 'primary' }[] = [
  { value: 'llm', label: '对话 LLM', tag: 'primary' },
  { value: 'embedding', label: '向量 Embedding', tag: 'success' },
  { value: 'rerank', label: '重排 Rerank', tag: 'warning' },
  { value: 'tts', label: '语音合成 TTS', tag: 'danger' },
  { value: 'asr', label: '语音识别 ASR', tag: 'info' },
  { value: 'image', label: '图像 Image', tag: '' }
]
const MODEL_TYPE_MAP = Object.fromEntries(MODEL_TYPES.map((t) => [t.value, t]))

/** 能力标签候选 */
const CAP_OPTIONS: { value: string; label: string }[] = [
  { value: 'function_call', label: '函数调用' },
  { value: 'vision', label: '视觉理解' },
  { value: 'stream', label: '流式输出' },
  { value: 'json_mode', label: 'JSON 输出' },
  { value: 'reasoning', label: '深度推理' },
  { value: 'image', label: '图像生成' }
]
const CAP_LABELS = Object.fromEntries(CAP_OPTIONS.map((c) => [c.value, c.label]))

function modelTypeLabel(t?: string) {
  if (!t) return '-'
  return MODEL_TYPE_MAP[t]?.label ?? t
}
function modelTypeTag(t?: string) {
  if (!t) return 'info'
  return (MODEL_TYPE_MAP[t]?.tag || 'info') as 'info' | 'success' | 'warning' | 'danger' | 'primary'
}
function providerTypeLabel(t?: string) {
  if (t === 'openai-compatible') return 'OpenAI 兼容'
  return t || '-'
}
function capLabel(c: string) {
  return CAP_LABELS[c] ?? c
}

// ---------------- 工具函数 ----------------

function fmtTime(t?: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}
function fmtK(v?: number) {
  if (v == null) return '-'
  return v >= 1000 ? `${Math.round(v / 1000).toLocaleString('zh-CN')}K` : String(v)
}
function fmtNum(v?: number) {
  return v == null ? '-' : v.toLocaleString('zh-CN')
}
function maskKey(k?: string) {
  if (!k) return ''
  if (k.length <= 8) return '••••••••'
  return `${k.slice(0, 3)}••••••••${k.slice(-4)}`
}
function parseCaps(c?: string): string[] {
  if (!c) return []
  try {
    const arr = JSON.parse(c)
    if (Array.isArray(arr)) return arr.map(String)
  } catch {
    /* 非 JSON 则按逗号分隔兜底 */
  }
  return c.split(',').map((s) => s.trim()).filter(Boolean)
}
async function copyText(text?: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

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

// ---------------- 供应商弹窗 ----------------

const providerDialog = ref(false)
const providerEditId = ref<number | null>(null)
const providerForm = reactive<Partial<ModelProvider>>({})

function openProviderCreate() {
  providerEditId.value = null
  Object.assign(providerForm, { name: '', type: 'openai-compatible', baseUrl: '', apiKey: '', status: 1 })
  providerDialog.value = true
}
function openProviderEdit(row: ModelProvider) {
  providerEditId.value = row.id
  // 编辑时不回显明文 Key，留空表示不修改
  Object.assign(providerForm, {
    name: row.name,
    type: row.type,
    baseUrl: row.baseUrl || '',
    apiKey: '',
    status: row.status
  })
  providerDialog.value = true
}
async function saveProvider() {
  if (!providerForm.name?.trim()) {
    ElMessage.warning('请输入供应商名称')
    return
  }
  if (!providerForm.type) {
    ElMessage.warning('请选择供应商类型')
    return
  }
  if (!providerForm.baseUrl?.trim()) {
    ElMessage.warning('请输入 API 基础地址')
    return
  }
  const payload: Partial<ModelProvider> = {
    name: providerForm.name.trim(),
    type: providerForm.type.trim(),
    baseUrl: providerForm.baseUrl.trim(),
    status: providerForm.status ?? 1
  }
  if (providerForm.apiKey && providerForm.apiKey.trim()) {
    payload.apiKey = providerForm.apiKey.trim()
  }
  try {
    if (providerEditId.value != null) {
      await modelApi.updateProvider(providerEditId.value, payload)
    } else {
      await modelApi.createProvider(payload)
    }
    ElMessage.success('保存成功')
    providerDialog.value = false
    load()
  } catch {
    /* 拦截器已统一提示 */
  }
}

// ---------------- 模型弹窗 ----------------

const modelDialog = ref(false)
const modelEditId = ref<number | null>(null)
const modelProviderName = ref('')
const modelForm = reactive<Partial<ModelInfo>>({})
const modelCapArr = ref<string[]>([])

function openModelCreate(provider: ModelProvider) {
  modelEditId.value = null
  modelProviderName.value = provider.name
  Object.assign(modelForm, {
    providerId: provider.id,
    name: '',
    modelType: 'llm' as ModelType,
    contextWindow: undefined,
    maxTokens: undefined,
    status: 1
  })
  modelCapArr.value = []
  modelDialog.value = true
}
function openModelEdit(m: ModelInfo, provider: ModelProvider) {
  modelEditId.value = m.id
  modelProviderName.value = provider.name
  Object.assign(modelForm, {
    providerId: m.providerId,
    name: m.name,
    modelType: m.modelType,
    contextWindow: m.contextWindow,
    maxTokens: m.maxTokens,
    status: m.status
  })
  modelCapArr.value = parseCaps(m.capabilities)
  modelDialog.value = true
}
async function saveModel() {
  if (!modelForm.name?.trim()) {
    ElMessage.warning('请输入模型名（调用 API 时的名称）')
    return
  }
  if (!modelForm.modelType) {
    ElMessage.warning('请选择模型类型')
    return
  }
  const payload: Partial<ModelInfo> = {
    name: modelForm.name.trim(),
    modelType: modelForm.modelType,
    status: modelForm.status ?? 1,
    contextWindow: modelForm.contextWindow,
    maxTokens: modelForm.maxTokens
  }
  if (modelCapArr.value.length) {
    payload.capabilities = JSON.stringify(modelCapArr.value)
  }
  try {
    if (modelEditId.value != null) {
      await modelApi.updateModel(modelEditId.value, payload)
    } else {
      await modelApi.createModel(modelForm.providerId!, payload)
    }
    ElMessage.success('保存成功')
    modelDialog.value = false
    refreshModels(modelForm.providerId!)
  } catch {
    /* 拦截器已统一提示 */
  }
}

// 模型状态开关（乐观更新，失败回滚）
async function onModelStatusChange(m: ModelInfo) {
  try {
    await modelApi.updateModel(m.id, { status: m.status })
  } catch {
    refreshModels(m.providerId)
  }
}

function removeModel(m: ModelInfo) {
  ElMessageBox.confirm(`删除模型「${m.name}」？`, '删除确认', { type: 'error', confirmButtonText: '删除' })
    .then(async () => {
      await modelApi.removeModel(m.id)
      ElMessage.success('删除成功')
      if (expandedModels[m.providerId]) {
        expandedModels[m.providerId] = expandedModels[m.providerId].filter((x) => x.id !== m.id)
      }
    })
    .catch(() => {})
}

// ---------------- 连通性测试（LLM 对话 / Embedding 向量） ----------------

const testVisible = ref(false)
const testMode = ref<'chat' | 'embed'>('chat')
const testModel = ref<ModelInfo | null>(null)
const testProviderName = ref('')
const testPrompt = ref('')
const testLoading = ref(false)
const testOutput = ref('')
const testError = ref('')
const testUsage = ref<Usage | null>(null)
const testEmbedText = ref('')
const testEmbedVectors = ref<number[][] | null>(null)
/** LLM 测试是否使用 SSE 流式输出 */
const testStream = ref(true)
/** 流式输出进行中（用于展示「停止」按钮） */
const testStreaming = ref(false)
const testAbort = ref<AbortController | null>(null)

const testDialogTitle = computed(() => {
  const name = testModel.value ? ` · ${testModel.value.name}` : ''
  return testMode.value === 'embed' ? `向量测试${name}` : `对话测试${name}`
})
/** 向量化结果摘要：条数 / 维度 / 首个向量前 8 维 */
const embedSummary = computed(() => {
  const vectors = testEmbedVectors.value
  if (!vectors?.length) return null
  const dim = (vectors[0] || []).length
  return { count: vectors.length, dim, head: (vectors[0] || []).slice(0, 8) }
})
const apiKeyPlaceholder = computed(() => (providerEditId.value ? '已配置（留空则不修改）' : 'sk-...'))

function openTest(m: ModelInfo, provider: ModelProvider, mode: 'chat' | 'embed' = 'chat') {
  testMode.value = mode
  testModel.value = m
  testProviderName.value = provider.name
  testPrompt.value = ''
  testOutput.value = ''
  testError.value = ''
  testUsage.value = null
  testEmbedVectors.value = null
  testEmbedText.value = mode === 'embed' ? '智能体（Agent）是什么？\n请对这段话进行向量化' : ''
  testVisible.value = true
}
async function runTest() {
  testError.value = ''
  testOutput.value = ''
  testUsage.value = null
  testEmbedVectors.value = null

  // ---------- Embedding：向量化 ----------
  if (testMode.value === 'embed') {
    const texts = testEmbedText.value
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
    if (!texts.length) {
      ElMessage.warning('请输入至少一条待向量化的文本')
      return
    }
    testLoading.value = true
    try {
      const resp = await modelApi.embed({ modelId: testModel.value!.id, texts })
      testEmbedVectors.value = resp.vectors || []
      testUsage.value = resp.usage || null
    } catch (e) {
      testError.value = e instanceof Error ? e.message : String(e)
    } finally {
      testLoading.value = false
    }
    return
  }

  // ---------- LLM：对话 ----------
  const prompt = testPrompt.value.trim()
  if (!prompt) {
    ElMessage.warning('请输入测试消息')
    return
  }

  // 非流式：一次性返回
  if (!testStream.value) {
    testLoading.value = true
    try {
      const resp = await modelApi.chat({ modelId: testModel.value!.id, prompt })
      testOutput.value = resp.content || '(空回复)'
      testUsage.value = resp.usage || null
    } catch (e) {
      testError.value = e instanceof Error ? e.message : String(e)
    } finally {
      testLoading.value = false
    }
    return
  }

  // 流式：SSE 实时渲染，可随时停止（AbortController 中断请求）
  testLoading.value = true
  testStreaming.value = true
  const controller = new AbortController()
  testAbort.value = controller
  try {
    await modelApi.chatStream(
      {
        modelId: testModel.value!.id,
        messages: [
          { role: 'system', content: 'You are a helpful assistant.' },
          { role: 'user', content: prompt }
        ]
      },
      (chunk) => {
        if (chunk.delta) testOutput.value += chunk.delta
        if (chunk.usage) testUsage.value = chunk.usage
      },
      controller.signal
    )
    if (!testOutput.value.trim()) testOutput.value = '(空回复)'
  } catch (e) {
    // 用户主动停止：保留已输出内容，不视为错误
    if (!(e instanceof DOMException && e.name === 'AbortError')) {
      testError.value = e instanceof Error ? e.message : String(e)
    }
  } finally {
    testLoading.value = false
    testStreaming.value = false
    testAbort.value = null
  }
}

/** 停止流式输出（保留已生成内容） */
function stopTest() {
  testAbort.value?.abort()
}

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
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-tooltip content="刷新">
          <el-button circle @click="load"><el-icon><Refresh /></el-icon></el-button>
        </el-tooltip>
        <el-button type="primary" class="btn-gradient" @click="openProviderCreate">
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
            <div class="expand-panel">
              <div class="model-panel-head">
                <div class="model-panel-title">
                  <el-icon><Cpu /></el-icon>
                  <span>「{{ row.name }}」下的模型</span>
                  <span class="count-badge">{{ (expandedModels[row.id] || []).length }}</span>
                </div>
                <el-button size="small" type="primary" plain @click="openModelCreate(row)">
                  <el-icon style="margin-right: 4px"><Plus /></el-icon>添加模型
                </el-button>
              </div>

              <el-table
                :data="expandedModels[row.id] || []"
                size="small"
                :empty-text="expandedLoading[row.id] ? '模型加载中…' : '暂无模型，点击右上角「添加模型」'"
              >
                <el-table-column label="模型名" min-width="180">
                  <template #default="{ row: m }">
                    <div class="model-name-cell">
                      <span class="mono model-name">{{ m.name }}</span>
                      <el-button link type="primary" @click="copyText(m.name)">复制</el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="类型" width="160">
                  <template #default="{ row: m }">
                    <el-tag size="small" :type="modelTypeTag(m.modelType)" effect="light">
                      {{ modelTypeLabel(m.modelType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="上下文窗口" width="110">
                  <template #default="{ row: m }">
                    <span class="mono">{{ fmtK(m.contextWindow) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="最大输出" width="110">
                  <template #default="{ row: m }">
                    <span class="mono">{{ fmtNum(m.maxTokens) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="能力" min-width="200">
                  <template #default="{ row: m }">
                    <div v-if="parseCaps(m.capabilities).length" class="cap-list">
                      <el-tag
                        v-for="c in parseCaps(m.capabilities)"
                        :key="c"
                        size="small"
                        class="cap-tag"
                        effect="plain"
                      >
                        {{ capLabel(c) }}
                      </el-tag>
                    </div>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="80" align="center">
                  <template #default="{ row: m }">
                    <el-switch
                      v-model="m.status"
                      :active-value="1"
                      :inactive-value="0"
                      @change="onModelStatusChange(m)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="更新时间" width="140">
                  <template #default="{ row: m }">
                    <span class="muted">{{ fmtTime(m.updateTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="220" align="right">
                  <template #default="{ row: m }">
                    <el-button
                      v-if="m.modelType === 'llm'"
                      link
                      type="success"
                      @click="openTest(m, row, 'chat')"
                    >
                      对话测试
                    </el-button>
                    <el-button
                      v-else-if="m.modelType === 'embedding'"
                      link
                      type="success"
                      @click="openTest(m, row, 'embed')"
                    >
                      向量测试
                    </el-button>
                    <el-button link type="primary" @click="openModelEdit(m, row)">编辑</el-button>
                    <el-button link type="danger" @click="removeModel(m)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="供应商" min-width="230">
          <template #default="{ row }">
            <div class="provider-cell">
              <div class="provider-badge">{{ (row.name || '?').charAt(0).toUpperCase() }}</div>
              <div>
                <div class="provider-name-row">
                  <span class="provider-name">{{ row.name }}</span>
                  <el-tag size="small" :type="row.type === 'openai-compatible' ? 'success' : 'info'" effect="light">
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
              <span class="mono kv-value" :class="{ dim: !row.baseUrl }">{{ row.baseUrl || '未配置' }}</span>
              <el-button v-if="row.baseUrl" link type="primary" @click="copyText(row.baseUrl)">复制</el-button>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="API Key" min-width="200">
          <template #default="{ row }">
            <div class="kv-cell">
              <span class="mono kv-value" :class="{ dim: !row.apiKey }">{{ maskKey(row.apiKey) || '未配置' }}</span>
              <el-button v-if="row.apiKey" link type="primary" @click="copyText(row.apiKey)">复制</el-button>
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
            <el-button link type="primary" @click="openProviderEdit(row)">编辑</el-button>
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

    <!-- 供应商弹窗 -->
    <el-dialog v-model="providerDialog" :title="providerEditId ? '编辑供应商' : '添加供应商'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="providerForm.name" placeholder="如 DeepSeek / OpenAI / 硅基流动" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="providerForm.type" filterable allow-create default-first-option style="width: 100%">
            <el-option label="OpenAI 兼容（DeepSeek / Qwen / Kimi / Ollama 等）" value="openai-compatible" />
          </el-select>
          <div class="form-tip">内置支持 openai-compatible；自定义类型需平台注册对应 LLMProvider 才能调用</div>
        </el-form-item>
        <el-form-item label="API 地址" required>
          <el-input v-model="providerForm.baseUrl" placeholder="如 https://api.deepseek.com/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="providerForm.apiKey"
            type="password"
            show-password
            clearable
            :placeholder="apiKeyPlaceholder"
          />
          <div v-if="providerEditId" class="form-tip">留空表示沿用原有 Key，不修改</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="providerForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialog = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" @click="saveProvider">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模型弹窗 -->
    <el-dialog v-model="modelDialog" :title="modelEditId ? '编辑模型' : '添加模型'" width="640px">
      <el-form label-width="110px">
        <el-form-item label="所属供应商">
          <el-input :model-value="modelProviderName" disabled />
        </el-form-item>
        <el-form-item label="模型名" required>
          <el-input v-model="modelForm.name" class="mono" placeholder="调用 API 时使用的名称，如 deepseek-chat / gpt-4o / bge-m3" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="模型类型" required>
          <el-select v-model="modelForm.modelType" style="width: 100%">
            <el-option v-for="t in MODEL_TYPES" :key="t.value" :label="`${t.label}（${t.value}）`" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上下文窗口">
              <el-input-number v-model="modelForm.contextWindow" :min="0" :step="1024" controls-position="right" style="width: 100%" placeholder="tokens" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大输出">
              <el-input-number v-model="modelForm.maxTokens" :min="0" :step="512" controls-position="right" style="width: 100%" placeholder="tokens" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="能力标签">
          <el-select v-model="modelCapArr" multiple filterable collapse-tags style="width: 100%" placeholder="选择模型支持的能力，如函数调用 / 视觉 / 流式">
            <el-option v-for="c in CAP_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="modelForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialog = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>

    <!-- 连通性测试 -->
    <el-dialog v-model="testVisible" :title="testDialogTitle" width="640px">
      <div class="test-meta">
        <el-tag size="small" :type="modelTypeTag(testModel?.modelType)" effect="light">
          {{ modelTypeLabel(testModel?.modelType) }}
        </el-tag>
        <span>供应商：<b>{{ testProviderName }}</b></span>
        <span class="mono">模型：{{ testModel?.name }}</span>
      </div>
      <template v-if="testMode === 'chat'">
        <el-input
          v-model="testPrompt"
          type="textarea"
          :rows="4"
          placeholder="输入一句测试消息，例如：你好，请用一句话介绍你自己"
        />
        <div class="test-options">
          <el-switch v-model="testStream" size="small" active-text="流式输出" />
          <span class="muted">实时逐字展示回复，可随时停止</span>
        </div>
      </template>
      <template v-else>
        <div class="embed-label">待向量化文本（每行一条）</div>
        <el-input
          v-model="testEmbedText"
          type="textarea"
          :rows="4"
          placeholder="例如：智能体（Agent）是什么？&#10;请对这段话进行向量化"
        />
      </template>
      <div class="test-actions">
        <template v-if="testStreaming">
          <el-button type="danger" plain @click="stopTest">
            <el-icon style="margin-right: 4px"><VideoPause /></el-icon>停止输出
          </el-button>
          <span class="muted">已输出 {{ testOutput.length }} 字符，点击停止将保留当前内容</span>
        </template>
        <el-button
          v-else
          type="primary"
          :loading="testLoading"
          class="btn-gradient"
          @click="runTest"
        >
          {{ testLoading ? '请求中…' : '运行测试' }}
        </el-button>
      </div>

      <div v-if="testError" class="test-error">
        <div class="test-error-title">调用失败</div>
        <pre class="mono">{{ testError }}</pre>
      </div>
      <div v-else-if="testMode === 'embed' && embedSummary" class="test-result">
        <div class="test-result-title">
          向量化成功
          <span v-if="testUsage" class="muted">（共 {{ testUsage.totalTokens }} tokens）</span>
        </div>
        <div class="embed-summary">共 {{ embedSummary?.count }} 条文本，向量维度 {{ embedSummary?.dim }}</div>
        <div class="embed-head">
          首个向量前 8 维：<span class="mono">[{{ embedSummary?.head?.join(', ') }}]</span>
        </div>
      </div>
      <div v-else-if="testOutput" class="test-result">
        <div class="test-result-title">
          模型回复
          <span v-if="testStreaming" class="stream-dot"></span>
          <span v-if="testStreaming" class="muted">生成中…</span>
          <span v-if="testUsage" class="muted">
            （{{ testUsage.promptTokens }}+{{ testUsage.completionTokens }} tokens）
          </span>
        </div>
        <pre>{{ testOutput }}</pre>
      </div>
    </el-dialog>
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
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}
.provider-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.provider-name {
  font-weight: 600;
}
.provider-id {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-tertiary);
}
.kv-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kv-value {
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
.expand-panel {
  margin: 4px 8px 12px;
  padding: 12px 16px;
  background: var(--fill-lighter);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}
.model-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.model-panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}
.model-panel-title .el-icon {
  color: var(--brand-1);
}
.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 11px;
}
.model-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.model-name {
  font-weight: 500;
}
.cap-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.cap-tag {
  border-color: var(--border-color);
  color: var(--text-secondary);
}
.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}

/* 弹窗 */
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

/* 测试面板 */
.test-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
}
.embed-label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}
.test-options {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.stream-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin: 0 2px 1px 4px;
  border-radius: 50%;
  background: var(--el-color-primary);
  vertical-align: middle;
  animation: stream-pulse 1s ease-in-out infinite;
}
@keyframes stream-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.3;
    transform: scale(0.7);
  }
}
.embed-summary {
  margin-top: 8px;
  font-size: 13px;
}
.embed-head {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.test-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
  min-height: 32px;
}
.test-result,
.test-error {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
}
.test-result {
  background: var(--fill-lighter);
  border: 1px solid var(--border-color);
}
.test-result pre {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  color: var(--text-primary);
}
.test-result-title {
  font-weight: 600;
  font-size: 12px;
  color: var(--text-secondary);
}
.test-error {
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
}
.test-error-title {
  font-weight: 600;
  font-size: 12px;
  color: var(--el-color-danger);
}
.test-error pre {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-color-danger);
}
</style>
