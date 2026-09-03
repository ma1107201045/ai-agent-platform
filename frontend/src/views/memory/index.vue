<template>
  <div class="page-container memory-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head-info">
        <h2 class="head-title">记忆管理</h2>
        <p class="head-desc">
          管理智能体的长期记忆与会话变量：手动沉淀记忆、配置自动抽取策略，让智能体记住用户的偏好与上下文。
        </p>
      </div>
      <div class="head-actions">
        <span class="action-label">选择智能体</span>
        <el-select
          v-model="selectedAppId"
          placeholder="选择智能体应用"
          filterable
          class="app-select"
          :disabled="!apps.length"
        >
          <el-option
            v-for="app in apps"
            :key="app.id"
            :value="app.id"
            :label="app.name"
          />
        </el-select>
      </div>
    </div>

    <!-- 无应用引导 -->
    <div v-if="!apps.length" class="hover-card empty-guide">
      <el-empty description="还没有智能体应用，请先创建一个应用，再为其配置记忆">
        <el-button type="primary" @click="goCreateApp">去创建应用</el-button>
      </el-empty>
    </div>

    <!-- 主区域 -->
    <template v-else>
      <div class="stat-row">
        <div class="hover-card stat-item" v-loading="loading">
          <div class="stat-icon" :class="strategy?.enabled ? 'on' : 'off'">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="stat-body">
            <p class="stat-label">长期记忆</p>
            <p class="stat-value" :class="strategy?.enabled ? 'text-primary' : 'text-muted'">
              {{ strategy?.enabled ? '已开启' : '未开启' }}
            </p>
          </div>
        </div>
        <div class="hover-card stat-item" v-loading="loading">
          <div class="stat-icon purple"><el-icon><Collection /></el-icon></div>
          <div class="stat-body">
            <p class="stat-label">记忆条目</p>
            <p class="stat-value">{{ itemTotal }}</p>
          </div>
        </div>
        <div class="hover-card stat-item" v-loading="loading">
          <div class="stat-icon cyan"><el-icon><Connection /></el-icon></div>
          <div class="stat-body">
            <p class="stat-label">会话变量</p>
            <p class="stat-value">{{ variableTotal }}</p>
          </div>
        </div>
        <div class="hover-card stat-item" v-loading="loading">
          <div class="stat-icon orange"><el-icon><Files /></el-icon></div>
          <div class="stat-body">
            <p class="stat-label">每次注入条数</p>
            <p class="stat-value">{{ strategy?.topN ?? '-' }} <span class="stat-unit">条</span></p>
          </div>
        </div>
      </div>

      <el-card shadow="never" class="content-card">
        <el-tabs v-model="activeTab" class="memory-tabs">
          <!-- ==================== 长期记忆 ==================== -->
          <el-tab-pane label="长期记忆" name="items">
            <div class="tab-toolbar">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="长期记忆让智能体跨会话记住用户偏好、事实与事件。可手动沉淀，也可开启「自动抽取」让对话后自动生成记忆；运行时按重要度挑选条目注入上下文。"
              />
              <div class="toolbar-row">
                <div class="toolbar-filters">
                  <el-select v-model="itemFilters.category" placeholder="全部分类" clearable class="filter-select">
                    <el-option v-for="c in itemCategories" :key="c.value" :value="c.value" :label="c.label" />
                  </el-select>
                  <el-select v-model="itemFilters.scope" placeholder="全部作用域" clearable class="filter-select">
                    <el-option value="global" label="全局记忆" />
                    <el-option value="user" label="用户记忆" />
                  </el-select>
                  <el-input
                    v-model="itemFilters.keyword"
                    placeholder="搜索记忆内容"
                    clearable
                    class="filter-search"
                    @keyup.enter="loadItems"
                    @clear="loadItems"
                  >
                    <template #prefix><el-icon><Search /></el-icon></template>
                  </el-input>
                  <el-button @click="loadItems"><el-icon><Refresh /></el-icon></el-button>
                </div>
                <el-button type="primary" @click="openItemDialog()">
                  <el-icon><Plus /></el-icon>&nbsp;新增记忆
                </el-button>
              </div>
            </div>

            <el-table v-loading="itemsLoading" :data="items" class="data-table" empty-text="暂无记忆条目">
              <el-table-column label="记忆内容" min-width="360">
                <template #default="{ row }">
                  <div class="item-content" :title="row.content">{{ row.content }}</div>
                </template>
              </el-table-column>
              <el-table-column label="类别" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="catTag(row.category)" effect="light" size="small">
                    {{ catText(row.category) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="重要度" width="110" align="center">
                <template #default="{ row }">
                  <span class="importance" :class="'lv' + row.importance" :title="importanceText(row.importance)">
                    {{ '★'.repeat(row.importance) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="来源" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.source === 'auto' ? 'warning' : 'info'" size="small" effect="plain">
                    {{ row.source === 'auto' ? '自动' : '手动' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="命中" width="70" align="center" prop="hitCount" />
              <el-table-column label="启用" width="70" align="center">
                <template #default="{ row }">
                  <el-switch
                    :model-value="row.status === 1"
                    size="small"
                    :loading="switchingId === row.id"
                    @change="toggleItem(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="130" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openItemDialog(row)">编辑</el-button>
                  <el-button link type="danger" size="small" @click="removeItem(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- ==================== 会话变量 ==================== -->
          <el-tab-pane label="会话变量" name="variables">
            <div class="tab-toolbar">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="会话变量是键值形式的上下文，可跨会话保存。全局变量作用于该应用的全部对话；会话变量可绑定到某个会话（为空时作用于全部会话）。运行时变量会被带入提示词。"
              />
              <div class="toolbar-row">
                <div class="toolbar-filters">
                  <el-select v-model="variableFilters.scope" placeholder="全部作用域" clearable class="filter-select">
                    <el-option value="global" label="全局" />
                    <el-option value="session" label="会话" />
                  </el-select>
                  <el-input
                    v-model="variableFilters.keyword"
                    placeholder="搜索变量名 / 值"
                    clearable
                    class="filter-search"
                    @keyup.enter="loadVariables"
                    @clear="loadVariables"
                  >
                    <template #prefix><el-icon><Search /></el-icon></template>
                  </el-input>
                  <el-button @click="loadVariables"><el-icon><Refresh /></el-icon></el-button>
                </div>
                <el-button type="primary" @click="openVariableDialog()">
                  <el-icon><Plus /></el-icon>&nbsp;新增变量
                </el-button>
              </div>
            </div>

            <el-table v-loading="varLoading" :data="variables" class="data-table" empty-text="暂无会话变量">
              <el-table-column label="变量名" width="200">
                <template #default="{ row }">
                  <code class="var-name">{{ row.name }}</code>
                </template>
              </el-table-column>
              <el-table-column label="作用域" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.scope === 'global' ? 'success' : 'warning'" size="small" effect="light">
                    {{ row.scope === 'global' ? '全局' : '会话' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="100" align="center">
                <template #default="{ row }">
                  <span class="type-chip">{{ typeText(row.valueType) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="值" min-width="220">
                <template #default="{ row }">
                  <span class="var-value" :title="row.value">{{ row.value ?? '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="140">
                <template #default="{ row }">
                  <span class="muted-text">{{ row.remark || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="130" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openVariableDialog(row)">编辑</el-button>
                  <el-button link type="danger" size="small" @click="removeVariable(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- ==================== 记忆策略 ==================== -->
          <el-tab-pane label="记忆策略" name="strategy">
            <div v-if="strategy" class="strategy-wrap">
              <div class="strategy-intro">
                <h3 class="strategy-title">记忆策略</h3>
                <p class="strategy-desc">
                  配置该应用如何存储与召回长期记忆。建议在发布时同步调整；自动抽取依赖可用的对话模型完成记忆沉淀。
                </p>
              </div>
              <el-form label-width="180px" class="strategy-form" label-position="left">
                <el-form-item label="启用长期记忆">
                  <el-switch v-model="strategy.enabled" :active-value="1" :inactive-value="0" />
                  <span class="field-tip">关闭后该应用的记忆不再写入，也不会被注入到对话上下文</span>
                </el-form-item>
                <el-form-item label="对话后自动抽取">
                  <el-switch v-model="strategy.autoExtract" :active-value="1" :inactive-value="0" />
                  <span class="field-tip">每次对话结束后，由抽取模型将用户偏好/事实沉淀为记忆条目</span>
                </el-form-item>
                <el-form-item v-if="strategy.autoExtract === 1" label="抽取使用模型">
                  <el-select
                    v-model="strategy.extractModelId"
                    placeholder="请选择对话模型"
                    clearable
                    filterable
                    class="model-select"
                  >
                    <el-option
                      v-for="m in chatModels"
                      :key="m.id"
                      :value="m.id"
                      :label="`${m.providerName} · ${m.modelName}`"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="每次注入条数">
                  <el-input-number v-model="strategy.topN" :min="1" :max="20" />
                  <span class="field-tip">对话时从记忆中挑选并注入上下文的最大条数</span>
                </el-form-item>
                <el-form-item label="单应用条目上限">
                  <el-input-number v-model="strategy.maxItems" :min="10" :max="100000" :step="100" />
                  <span class="field-tip">达到上限后按“重要度优先”淘汰旧条目</span>
                </el-form-item>
                <el-form-item label="记忆保留天数">
                  <el-input-number
                    v-model="strategy.keepDays"
                    :min="0"
                    :max="3650"
                    placeholder="永久保留"
                    class="keep-days-input"
                  />
                  <span class="field-tip">留空（0）表示永久保留，非 0 时超过保留期的记忆自动失效</span>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="savingStrategy" @click="saveStrategy">
                    保存记忆策略
                  </el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <!-- 记忆条目 编辑弹窗 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="itemForm.id ? '编辑记忆' : '新增记忆'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="90px">
        <el-form-item label="类别" prop="category">
          <el-select v-model="itemForm.category" class="full-width">
            <el-option v-for="c in itemCategories" :key="c.value" :value="c.value" :label="c.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用域" prop="scope">
          <el-radio-group v-model="itemForm.scope">
            <el-radio value="global">全局记忆</el-radio>
            <el-radio value="user">用户记忆</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="itemForm.content"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="例如：用户偏好简洁直接的回答风格，喜欢先给结论再给理由"
          />
        </el-form-item>
        <el-form-item label="重要度" prop="importance">
          <el-radio-group v-model="itemForm.importance">
            <el-radio v-for="lv in [1, 2, 3, 4, 5]" :key="lv" :value="lv" class="importance-radio">
              {{ '★'.repeat(lv) }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingItem" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>

    <!-- 会话变量 编辑弹窗 -->
    <el-dialog
      v-model="varDialogVisible"
      :title="variableForm.id ? '编辑变量' : '新增变量'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="variableFormRef" :model="variableForm" :rules="variableRules" label-width="90px">
        <el-form-item label="变量名" prop="name">
          <el-input
            v-model="variableForm.name"
            placeholder="英文变量名，如 user_city，运行时按变量名引用"
          />
        </el-form-item>
        <el-form-item label="作用域" prop="scope">
          <el-radio-group v-model="variableForm.scope">
            <el-radio value="global">全局</el-radio>
            <el-radio value="session">会话</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="variableForm.scope === 'session'" label="会话ID">
          <el-input-number
            v-model="variableForm.conversationId"
            :min="0"
            :controls="false"
            placeholder="空 = 全部会话"
            class="full-width"
          />
          <div class="field-tip">为空时该变量作用于此应用的全部会话</div>
        </el-form-item>
        <el-form-item label="类型" prop="valueType">
          <el-select v-model="variableForm.valueType" class="full-width">
            <el-option value="string" label="string 字符串" />
            <el-option value="number" label="number 数字" />
            <el-option value="boolean" label="boolean 布尔" />
            <el-option value="json" label="json 对象" />
          </el-select>
        </el-form-item>
        <el-form-item label="变量值" prop="value">
          <el-input
            v-model="variableForm.value"
            type="textarea"
            :rows="3"
            placeholder="变量内容（json 类型请输入 JSON 文本）"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="variableForm.remark" placeholder="可选，说明该变量的用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="varDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingVariable" @click="submitVariable">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { appAgentApi } from '@/api/app-agent'
import { modelApi } from '@/api/model'
import { memoryApi } from '@/api/memory'
import type {
  AppAgent,
  ChatModelInfo,
  MemItem,
  MemStrategy,
  MemVariable
} from '@/api/types'

const router = useRouter()

// ---------- 应用与基础数据 ----------
const apps = ref<AppAgent[]>([])
const selectedAppId = ref<number | null>(null)
const activeTab = ref('items')
const chatModels = ref<ChatModelInfo[]>([])

const selectedApp = computed(() => apps.value.find((a) => a.id === selectedAppId.value) || null)

const itemCategories = [
  { value: 'preference', label: '用户偏好' },
  { value: 'fact', label: '事实' },
  { value: 'event', label: '事件' },
  { value: 'summary', label: '总结' },
  { value: 'custom', label: '自定义' }
]

const catText = (v: string) => itemCategories.find((c) => c.value === v)?.label || '自定义'
const catTag = (v: string): 'success' | 'primary' | 'warning' | 'danger' | 'info' =>
  (({
    preference: 'success',
    fact: 'primary',
    event: 'warning',
    summary: 'danger',
    custom: 'info'
  }) as Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'>)[v] || 'info'
const typeText = (t: string) =>
  ({ string: '字符串', number: '数字', boolean: '布尔', json: 'JSON' })[t] || t
const importanceText = (lv: number) => ['', '较低', '一般', '重要', '很重要', '极重要'][lv] || ''
const formatTime = (t?: string) => (t ? t.replace('T', ' ').slice(0, 19) : '—')

// ---------- 数据 ----------
const loading = ref(false)
const strategy = ref<MemStrategy | null>(null)
const items = ref<MemItem[]>([])
const variables = ref<MemVariable[]>([])
const itemTotal = ref(0)
const variableTotal = ref(0)

const itemFilters = reactive({ category: '', scope: '', keyword: '' })
const variableFilters = reactive({ scope: '', keyword: '' })
const itemsLoading = ref(false)
const varLoading = ref(false)
const switchingId = ref<number | null>(null)

async function loadStrategy() {
  if (selectedAppId.value == null) return
  strategy.value = await memoryApi.strategy(selectedAppId.value)
}

async function loadItems() {
  if (selectedAppId.value == null) return
  itemsLoading.value = true
  try {
    items.value = await memoryApi.items(selectedAppId.value, {
      category: itemFilters.category || undefined,
      scope: itemFilters.scope || undefined,
      keyword: itemFilters.keyword || undefined
    })
  } finally {
    itemsLoading.value = false
  }
}

async function loadVariables() {
  if (selectedAppId.value == null) return
  varLoading.value = true
  try {
    variables.value = await memoryApi.variables(selectedAppId.value, {
      scope: variableFilters.scope || undefined,
      keyword: variableFilters.keyword || undefined
    })
  } finally {
    varLoading.value = false
  }
}

/** 切换应用后全量拉取（计数不受筛选影响） */
async function loadAll() {
  if (selectedAppId.value == null) return
  loading.value = true
  try {
    const [strategyRes, itemsRes, variablesRes] = await Promise.all([
      memoryApi.strategy(selectedAppId.value),
      memoryApi.items(selectedAppId.value),
      memoryApi.variables(selectedAppId.value)
    ])
    strategy.value = strategyRes
    items.value = itemsRes
    variables.value = variablesRes
    itemTotal.value = itemsRes.length
    variableTotal.value = variablesRes.length
  } finally {
    loading.value = false
  }
}

// ---------- 应用切换 ----------
async function loadApps() {
  const res = await appAgentApi.page({ page: 1, size: 100 })
  apps.value = res.records || []
  if (apps.value.length) {
    selectedAppId.value = apps.value[0].id
    await loadAll()
  }
}

async function loadChatModels() {
  try {
    chatModels.value = await modelApi.chatModels()
  } catch {
    chatModels.value = []
  }
}

// ---------- 记忆条目操作 ----------
const itemDialogVisible = ref(false)
const submittingItem = ref(false)
const itemFormRef = ref<FormInstance>()
const itemForm = ref<Partial<MemItem>>({})
const itemRules: FormRules = {
  content: [{ required: true, message: '请输入记忆内容', trigger: 'blur' }]
}

function openItemDialog(row?: MemItem) {
  itemForm.value = row
    ? { ...row }
    : {
        appId: selectedAppId.value ?? 0,
        scope: 'global',
        source: 'manual',
        category: 'preference',
        importance: 3,
        content: '',
        status: 1,
        hitCount: 0
      }
  itemDialogVisible.value = true
}

async function submitItem() {
  const valid = await itemFormRef.value?.validate().catch(() => false)
  if (!valid || selectedAppId.value == null) return
  submittingItem.value = true
  try {
    const form = itemForm.value
    if (form.id) {
      await memoryApi.updateItem(form.id, {
        content: form.content,
        category: form.category,
        scope: form.scope,
        importance: form.importance
      })
    } else {
      await memoryApi.createItem(selectedAppId.value, {
        appId: selectedAppId.value,
        scope: form.scope ?? 'global',
        source: 'manual',
        category: form.category ?? 'preference',
        content: form.content ?? '',
        importance: form.importance ?? 3,
        status: 1
      })
    }
    ElMessage.success(form.id ? '记忆已更新' : '记忆已新增')
    itemDialogVisible.value = false
    await loadItems()
    itemTotal.value = items.value.length
  } finally {
    submittingItem.value = false
  }
}

async function toggleItem(row: MemItem) {
  switchingId.value = row.id ?? null
  try {
    const next = row.status === 1 ? 0 : 1
    await memoryApi.updateItem(row.id as number, { status: next })
    row.status = next
  } finally {
    switchingId.value = null
  }
}

async function removeItem(row: MemItem) {
  await ElMessageBox.confirm('删除后无法恢复，确定删除这条记忆吗？', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await memoryApi.removeItem(row.id as number)
  ElMessage.success('记忆已删除')
  await loadItems()
  itemTotal.value = items.value.length
}

// ---------- 会话变量操作 ----------
const varDialogVisible = ref(false)
const submittingVariable = ref(false)
const variableFormRef = ref<FormInstance>()
const variableForm = ref<Partial<MemVariable>>({})
const variableRules: FormRules = {
  name: [
    { required: true, message: '请输入变量名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/,
      message: '须为字母/数字/下划线，且以字母或下划线开头',
      trigger: 'blur'
    }
  ]
}

function openVariableDialog(row?: MemVariable) {
  variableForm.value = row
    ? { ...row }
    : {
        appId: selectedAppId.value ?? 0,
        scope: 'global',
        valueType: 'string',
        name: '',
        value: '',
        status: 1
      }
  varDialogVisible.value = true
}

async function submitVariable() {
  const valid = await variableFormRef.value?.validate().catch(() => false)
  if (!valid || selectedAppId.value == null) return
  submittingVariable.value = true
  try {
    const form = variableForm.value
    const payload = {
      name: form.name?.trim(),
      scope: form.scope ?? 'global',
      valueType: form.valueType ?? 'string',
      value: form.value ?? '',
      remark: form.remark,
      conversationId: form.scope === 'session' && form.conversationId ? form.conversationId : null,
      status: 1
    }
    if (form.id) {
      await memoryApi.updateVariable(form.id, payload)
    } else {
      await memoryApi.createVariable(selectedAppId.value, {
        appId: selectedAppId.value,
        ...payload
      })
    }
    ElMessage.success(form.id ? '变量已更新' : '变量已新增')
    varDialogVisible.value = false
    await loadVariables()
    variableTotal.value = variables.value.length
  } finally {
    submittingVariable.value = false
  }
}

async function removeVariable(row: MemVariable) {
  await ElMessageBox.confirm(`确定删除变量 ${row.name} 吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await memoryApi.removeVariable(row.id as number)
  ElMessage.success('变量已删除')
  await loadVariables()
  variableTotal.value = variables.value.length
}

// ---------- 策略保存 ----------
const savingStrategy = ref(false)

async function saveStrategy() {
  const s = strategy.value
  if (!s) return
  savingStrategy.value = true
  try {
    await memoryApi.saveStrategy({
      ...s,
      keepDays: s.keepDays || null,
      extractModelId: s.autoExtract === 1 ? s.extractModelId || null : null
    })
    ElMessage.success('记忆策略已保存')
    await loadStrategy()
  } finally {
    savingStrategy.value = false
  }
}

const goCreateApp = () => router.push('/app/agents')

onMounted(async () => {
  await loadApps()
  await loadChatModels()
})
</script>

<style scoped>
.memory-page {
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
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.action-label {
  font-size: 13px;
  color: var(--text-tertiary, #8a8f98);
}
.app-select {
  width: 240px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
  margin: 16px 0;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: var(--bg-tertiary, #f1f3f5);
  color: #6b7280;
}
.stat-icon.on {
  background: rgba(52, 199, 123, 0.14);
  color: #34c77b;
}
.stat-icon.off {
  background: rgba(245, 158, 11, 0.14);
  color: #f59e0b;
}
.stat-icon.purple {
  background: rgba(124, 93, 250, 0.12);
  color: #7c5dfa;
}
.stat-icon.cyan {
  background: rgba(0, 168, 255, 0.12);
  color: #00a8ff;
}
.stat-icon.orange {
  background: rgba(255, 152, 0, 0.14);
  color: #ff9800;
}
.stat-body .stat-label {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
.stat-value {
  margin: 2px 0 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.text-primary {
  color: #34c77b !important;
}
.text-muted {
  color: #f59e0b !important;
}
.stat-unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary, #8a8f98);
}

.content-card {
  border-radius: 14px;
}
.memory-tabs :deep(.el-tabs__header) {
  margin: 0 0 4px;
}
.memory-tabs :deep(.el-tabs__item) {
  font-size: 14px;
}
.tab-toolbar {
  padding: 4px 0 12px;
}
.tab-toolbar :deep(.el-alert) {
  margin-bottom: 12px;
  border-radius: 10px;
}
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}
.filter-select {
  width: 130px;
}
.filter-search {
  width: 240px;
}

.data-table {
  width: 100%;
}
.data-table :deep(th.el-table__cell) {
  background: var(--bg-table-head, #fafafa);
  color: var(--text-secondary, #4a4e57);
  font-size: 12px;
}
.item-content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.5;
}
.importance {
  font-size: 14px;
  letter-spacing: 1px;
}
.importance.lv1,
.importance.lv2 {
  color: #909399;
}
.importance.lv3 {
  color: #e6a23c;
}
.importance.lv4 {
  color: #f56c6c;
}
.importance.lv5 {
  color: #ff5f5f;
}
.muted-text {
  color: var(--text-tertiary, #8a8f98);
  font-size: 12px;
}
.var-name {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  color: #7c5dfa;
  background: rgba(124, 93, 250, 0.08);
  padding: 2px 6px;
  border-radius: 6px;
}
.var-value {
  font-size: 13px;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.type-chip {
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
  border: 1px solid var(--border-color, #e4e7ed);
  padding: 1px 8px;
  border-radius: 10px;
}

.strategy-wrap {
  padding: 12px 4px 20px;
  max-width: 780px;
}
.strategy-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
}
.strategy-desc {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--text-tertiary, #8a8f98);
  line-height: 1.7;
}
.field-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
.model-select {
  width: 320px;
}
.keep-days-input {
  width: 200px;
}
.full-width {
  width: 100%;
}
.importance-radio {
  margin-right: 6px;
}
.empty-guide {
  margin-top: 24px;
  padding: 40px 0;
}
</style>
