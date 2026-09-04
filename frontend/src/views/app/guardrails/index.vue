<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { guardApi } from '@/api/guard'
import type { GuardAppBindVO, GuardRule, GuardTestResult } from '@/api/guard'

// ---------------- 常量与映射 ----------------

const DIRS: { value: string; label: string; tag: 'danger' | 'primary' }[] = [
  { value: 'input', label: '输入侧', tag: 'danger' },
  { value: 'output', label: '输出侧', tag: 'primary' }
]
const MATCH_TYPES: { value: string; label: string; tag: 'warning' | 'danger' | 'info' }[] = [
  { value: 'keyword', label: '关键词', tag: 'warning' },
  { value: 'regex', label: '正则表达式', tag: 'danger' },
  { value: 'prompt_injection', label: '注入检测', tag: 'info' }
]
const ACTIONS: { value: string; label: string; desc: string; tag: 'danger' | 'warning' | 'info' }[] = [
  { value: 'block', label: '拦截', desc: '命中后直接拒绝本次请求', tag: 'danger' },
  { value: 'mask', label: '打码', desc: '将命中的内容替换为占位符', tag: 'warning' },
  { value: 'replace', label: '替换', desc: '替换为自定义文本继续处理', tag: 'info' }
]
const META = {
  dir: Object.fromEntries(DIRS.map((d) => [d.value, d])),
  match: Object.fromEntries(MATCH_TYPES.map((m) => [m.value, m])),
  action: Object.fromEntries(ACTIONS.map((a) => [a.value, a]))
}
const RISK_LEVELS = [
  { min: 1, max: 2, label: '低风险', color: '#67c23a' },
  { min: 3, max: 3, label: '中风险', color: '#e6a23c' },
  { min: 4, max: 5, label: '高风险', color: '#f56c6c' }
]
function riskInfo(level?: number) {
  const lv = level ?? 3
  const hit = RISK_LEVELS.find((r) => lv >= r.min && lv <= r.max) || RISK_LEVELS[1]
  return { ...hit, lv }
}
function fmtTime(t?: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}
function dirLabel(v?: string) {
  return v ? META.dir[v]?.label ?? v : '-'
}
function dirTag(v?: string) {
  return (v ? META.dir[v]?.tag : undefined) || 'info'
}
function matchLabel(v?: string) {
  return v ? META.match[v]?.label ?? v : '-'
}
function actionLabel(v?: string) {
  return v ? META.action[v]?.label ?? v : '-'
}
function actionTag(v?: string) {
  return (v ? META.action[v]?.tag : undefined) || 'info'
}
function actionDesc(v?: string) {
  return v ? META.action[v]?.desc ?? '' : ''
}

// ---------------- 规则列表 ----------------

const tab = ref<'rules' | 'binds'>('rules')
const loading = ref(false)
const rows = ref<GuardRule[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const dirFilter = ref('')
const actionFilter = ref('')

async function load() {
  loading.value = true
  try {
    const data = await guardApi.rulePage({
      page: page.value,
      size: size.value,
      direction: dirFilter.value || undefined,
      action: actionFilter.value || undefined,
      keyword: keyword.value.trim() || undefined
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function onSearch() {
  page.value = 1
  load()
}

async function onEnableChange(row: GuardRule) {
  try {
    await guardApi.updateRule(row.id, { enabled: row.enabled })
  } catch {
    row.enabled = row.enabled === 1 ? 0 : 1
    ElMessage.error('更新失败')
  }
}

function removeRule(row: GuardRule) {
  ElMessageBox.confirm(`删除规则「${row.name}」？`, '删除确认', { type: 'error', confirmButtonText: '删除' })
    .then(async () => {
      await guardApi.removeRule(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

// ---------------- 规则弹窗 ----------------

const dialogVisible = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<Partial<GuardRule>>({})
function resetForm() {
  Object.assign(form, {
    name: '',
    description: '',
    direction: 'input',
    matchType: 'keyword',
    ruleContent: '',
    action: 'block',
    replaceText: '****',
    riskLevel: 3,
    enabled: 1,
    priority: 1
  })
}
function openCreate() {
  resetForm()
  editId.value = null
  dialogVisible.value = true
}
function openEdit(row: GuardRule) {
  editId.value = row.id
  Object.assign(form, {
    name: row.name,
    description: row.description || '',
    direction: row.direction,
    matchType: row.matchType,
    ruleContent: row.ruleContent,
    action: row.action,
    replaceText: row.replaceText || '****',
    riskLevel: row.riskLevel,
    enabled: row.enabled,
    priority: row.priority
  })
  dialogVisible.value = true
}
async function saveRule() {
  if (!form.name?.trim()) return ElMessage.warning('请输入规则名称')
  if (!form.ruleContent?.trim()) return ElMessage.warning('请输入匹配内容')
  if ((form.action === 'mask' || form.action === 'replace') && !form.replaceText?.trim()) {
    return ElMessage.warning('打码/替换需指定替换文本')
  }
  saving.value = true
  const payload: Partial<GuardRule> = {
    name: form.name.trim(),
    description: form.description?.trim() || undefined,
    direction: form.direction as 'input' | 'output',
    matchType: form.matchType!,
    ruleContent: form.ruleContent.trim(),
    action: form.action!,
    replaceText: form.replaceText?.trim() || '****',
    riskLevel: form.riskLevel ?? 3,
    enabled: form.enabled ?? 1,
    priority: form.priority ?? 1
  }
  try {
    if (editId.value != null) {
      await guardApi.updateRule(editId.value, payload)
    } else {
      await guardApi.createRule(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
    loadAllRules()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ---------------- 命中测试 ----------------

const testVisible = ref(false)
const testLoading = ref(false)
const testRuleId = ref<number | null>(null)
const testText = ref('')
const testResult = ref<GuardTestResult | null>(null)
const testError = ref('')

function openTest(row?: GuardRule) {
  testRuleId.value = row ? row.id : null
  testText.value = ''
  testResult.value = null
  testError.value = ''
  testVisible.value = true
}
async function runTest() {
  if (!testText.value.trim()) return ElMessage.warning('请输入待检测文本')
  testLoading.value = true
  testError.value = ''
  testResult.value = null
  try {
    testResult.value = await guardApi.test({
      ruleIds: testRuleId.value ? [testRuleId.value] : undefined,
      text: testText.value
    })
  } catch (e) {
    testError.value = e instanceof Error ? e.message : String(e)
  } finally {
    testLoading.value = false
  }
}

// ---------------- 应用绑定 ----------------

const bindLoading = ref(false)
const bindRows = ref<GuardAppBindVO[]>([])
const allRules = ref<GuardRule[]>([])
const bindDrawer = ref(false)
const bindSaving = ref(false)
const currentApp = ref<GuardAppBindVO | null>(null)
const bindForm = reactive<{ checked: number[]; mode: string; enabled: number }>({
  checked: [],
  mode: 'enforce',
  enabled: 1
})

async function loadAllRules() {
  try {
    const data = await guardApi.rulePage({ page: 1, size: 500 })
    allRules.value = data.records
  } catch {
    allRules.value = []
  }
}
async function loadBinds() {
  bindLoading.value = true
  try {
    bindRows.value = await guardApi.binds()
  } finally {
    bindLoading.value = false
  }
}

function openBind(row: GuardAppBindVO) {
  currentApp.value = row
  bindForm.mode = row.bindMode || 'enforce'
  bindForm.enabled = row.bindEnabled ?? 1
  bindForm.checked = parseRuleIds(row.ruleIds)
  bindDrawer.value = true
}
function parseRuleIds(json?: string): number[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.map(Number).filter((n: number) => !Number.isNaN(n)) : []
  } catch {
    return []
  }
}
async function saveBind() {
  if (!currentApp.value) return
  bindSaving.value = true
  try {
    await guardApi.saveBind(currentApp.value.appId, {
      ruleIds: bindForm.checked,
      mode: bindForm.mode,
      enabled: bindForm.enabled
    })
    ElMessage.success('绑定已保存')
    bindDrawer.value = false
    loadBinds()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    bindSaving.value = false
  }
}

function unbind(row: GuardAppBindVO) {
  if (row.bindId == null) return
  ElMessageBox.confirm(`解除应用「${row.appName}」的内容安全绑定？`, '解绑确认', { type: 'warning' })
    .then(async () => {
      await guardApi.removeBind(row.appId)
      ElMessage.success('已解绑')
      loadBinds()
    })
    .catch(() => {})
}

function onTabChange(v: string | number) {
  if (v === 'binds') {
    loadBinds()
  }
}

onMounted(() => {
  load()
  loadAllRules()
})
</script>

<template>
  <div class="page-container guard-page">
    <div class="guard-head">
      <div>
        <h2 class="head-title">内容安全</h2>
        <p class="head-desc">配置敏感词 / 正则 / 注入检测规则，在输入输出链路上守护应用合规安全</p>
      </div>
      <div class="head-actions" v-if="tab === 'rules'">
        <el-button @click="openTest()">
          <el-icon style="margin-right: 4px"><Aim /></el-icon>命中测试
        </el-button>
        <el-button type="primary" class="btn-gradient" @click="openCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>新建规则
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="guard-card">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 规则库 -->
        <el-tab-pane label="规则库" name="rules">
          <div class="toolbar">
            <el-input
              v-model="keyword"
              class="search-input"
              placeholder="搜索规则名称 / 说明 / 内容"
              clearable
              @keyup.enter="onSearch"
              @clear="onSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="dirFilter" placeholder="作用方向" clearable style="width: 130px" @change="onSearch">
              <el-option v-for="d in DIRS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
            <el-select v-model="actionFilter" placeholder="处置动作" clearable style="width: 130px" @change="onSearch">
              <el-option v-for="a in ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
            <el-tooltip content="刷新">
              <el-button circle @click="load"><el-icon><Refresh /></el-icon></el-button>
            </el-tooltip>
          </div>

          <el-table v-loading="loading" :data="rows">
            <el-table-column label="规则" min-width="200">
              <template #default="{ row }">
                <div class="rule-name">
                  {{ row.name }}
                  <span class="risk-dot" :style="{ background: riskInfo(row.riskLevel).color }"
                    :title="`${riskInfo(row.riskLevel).label}(${row.riskLevel})`"></span>
                </div>
                <div v-if="row.description" class="rule-desc" :title="row.description">{{ row.description }}</div>
              </template>
            </el-table-column>
            <el-table-column label="作用方向" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="dirTag(row.direction)" size="small" effect="light">{{ dirLabel(row.direction) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="匹配方式" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="META.match[row.matchType]?.tag || 'info'" size="small" effect="plain">
                  {{ matchLabel(row.matchType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处置动作" width="130">
              <template #default="{ row }">
                <el-tag :type="actionTag(row.action)" size="small" effect="light">{{ actionLabel(row.action) }}</el-tag>
                <div class="muted">{{ actionDesc(row.action) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="匹配内容" min-width="220">
              <template #default="{ row }">
                <span class="mono content" :title="row.ruleContent">{{ row.ruleContent }}</span>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="80" align="center">
              <template #default="{ row }">
                <span class="mono">{{ row.priority }}</span>
              </template>
            </el-table-column>
            <el-table-column label="命中" width="80" align="right">
              <template #default="{ row }">
                <span class="mono">{{ row.hitCount ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="onEnableChange(row)" />
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="140">
              <template #default="{ row }">
                <span class="muted">{{ fmtTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right" align="right">
              <template #default="{ row }">
                <el-button link type="success" @click="openTest(row)">测试</el-button>
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeRule(row)">删除</el-button>
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
        </el-tab-pane>

        <!-- 应用绑定 -->
        <el-tab-pane :label="`应用绑定（${bindRows.length}）`" name="binds">
          <el-table v-loading="bindLoading" :data="bindRows">
            <el-table-column label="应用" min-width="200">
              <template #default="{ row }">
                <div class="app-name">{{ row.appName }}</div>
                <div class="muted">{{ row.appType }}</div>
              </template>
            </el-table-column>
            <el-table-column label="绑定状态" width="140" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.bindId != null" :type="row.bindEnabled === 1 ? 'success' : 'info'" size="small" effect="light">
                  {{ row.bindEnabled === 1 ? '已启用' : '已停用' }}
                </el-tag>
                <el-tag v-else type="info" size="small" effect="plain">未绑定</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="模式" width="120" align="center">
              <template #default="{ row }">
                <span v-if="row.bindMode" class="mono">
                  <el-tag :type="row.bindMode === 'enforce' ? 'danger' : 'warning'" size="small">
                    {{ row.bindMode === 'enforce' ? '强制拦截' : '仅记录' }}
                  </el-tag>
                </span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="绑定规则数" width="110" align="center">
              <template #default="{ row }">
                <span v-if="row.ruleCount != null" class="mono">{{ row.ruleCount }} 条</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openBind(row)">配置</el-button>
                <el-button v-if="row.bindId != null" link type="danger" @click="unbind(row)">解绑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="bind-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>绑定规则后，命中「拦截」规则的请求将被拒绝；命中「打码/替换」规则的内容将按规则处理。正式接入需在模型调用链路上启用护栏（当前为配置层）。</span>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 规则编辑 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑规则' : '新建规则'" width="640px" :close-on-click-modal="false">
      <el-form label-width="92px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" placeholder="如 输入-色情低俗词拦截 / 输出-注入提示词" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="规则说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" placeholder="规则用途说明（可选）" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="作用方向" required>
              <el-radio-group v-model="form.direction">
                <el-radio-button v-for="d in DIRS" :key="d.value" :value="d.value">{{ d.label }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="风险等级">
              <el-rate v-model="form.riskLevel" :max="5" show-score style="height: 32px" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="匹配方式" required>
          <el-radio-group v-model="form.matchType">
            <el-radio-button v-for="m in MATCH_TYPES" :key="m.value" :value="m.value">{{ m.label }}</el-radio-button>
          </el-radio-group>
          <div class="form-tip">
            关键词以英文/中文逗号分隔，如 <span class="mono">赌博, 色情, 毒品</span>；正则模式直接填写表达式，如 <span class="mono">1[3-9]\d{9}</span>。
          </div>
        </el-form-item>
        <el-form-item label="匹配内容" required>
          <el-input v-model="form.ruleContent" type="textarea" :rows="2" class="mono"
            :placeholder="form.matchType === 'regex' ? '输入正则表达式' : '输入敏感关键词（逗号分隔）'" />
        </el-form-item>
        <el-form-item label="处置动作" required>
          <el-radio-group v-model="form.action">
            <el-radio-button v-for="a in ACTIONS" :key="a.value" :value="a.value">{{ a.label }}</el-radio-button>
          </el-radio-group>
          <div class="form-tip">{{ actionDesc(String(form.action)) }}</div>
        </el-form-item>
        <el-form-item v-if="form.action === 'mask' || form.action === 'replace'" label="替换文本">
          <el-input v-model="form.replaceText" maxlength="64" placeholder="如 ****" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="1" :max="99" style="width: 100%" />
              <div class="form-tip">数字越小越先执行</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 命中测试 -->
    <el-dialog v-model="testVisible" :title="testRuleId ? '规则命中测试' : '命中测试（全部启用规则）'" width="620px">
      <div class="test-field">
        <el-input
          v-model="testText"
          type="textarea"
          :rows="4"
          placeholder="输入待检测的文本，例如：请联系我微信 138xxxx8888 并可提供赌博服务"
        />
        <el-button type="primary" class="btn-gradient" style="margin-top: 10px" :loading="testLoading" @click="runTest">
          {{ testLoading ? '检测中…' : '开始检测' }}
        </el-button>
      </div>
      <div v-if="testError" class="test-block">
        <div class="test-block-title">检测失败</div>
        <pre class="mono">{{ testError }}</pre>
      </div>
      <template v-else-if="testResult">
        <div v-if="testResult.blocked" class="test-block blocked">
          <div class="test-block-title">
            <el-icon><CircleCloseFilled /></el-icon> 已拦截 · 命中 {{ testResult.hitCount }} 条规则（含拦截动作）
          </div>
        </div>
        <div v-else-if="testResult.hits.length" class="test-block">
          <div class="test-block-title">
            <el-icon><WarningFilled /></el-icon> 检测通过 · 命中 {{ testResult.hitCount }} 条（无拦截动作）
          </div>
          <div v-if="testResult.changed" class="test-output">
            处理后文本：<span class="mono">{{ testResult.output }}</span>
          </div>
        </div>
        <div v-else class="test-clean">
          <el-icon><CircleCheck /></el-icon> 未命中任何启用规则，文本通过检测
        </div>
        <div v-if="testResult.hits.length" class="hit-list">
          <div v-for="(h, i) in testResult.hits" :key="h.ruleId" class="hit-item">
            <div class="hit-head">
              <span class="hit-index">{{ i + 1 }}</span>
              <b>{{ h.name }}</b>
              <el-tag :type="dirTag(h.direction)" size="small" effect="plain">{{ dirLabel(h.direction) }}</el-tag>
              <el-tag :type="actionTag(h.action)" size="small" effect="plain">{{ actionLabel(h.action) }}</el-tag>
              <span class="risk-dot" :style="{ background: riskInfo(h.riskLevel).color }"></span>
            </div>
            <div class="hit-matched">
              <span class="muted">命中内容：</span>
              <el-tag v-for="(mm, j) in h.matched" :key="j" size="small" type="danger" effect="light">{{ mm }}</el-tag>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 应用绑定抽屉 -->
    <el-drawer v-model="bindDrawer" :title="`内容安全绑定 · ${currentApp?.appName ?? ''}`" size="520px">
      <template v-if="currentApp">
        <el-form label-width="80px">
          <el-form-item label="运行模式">
            <el-radio-group v-model="bindForm.mode">
              <el-radio value="enforce">强制（命中拦截即拒绝）</el-radio>
              <el-radio value="log">仅记录（不拦截，日志留痕）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="启用绑定">
            <el-switch v-model="bindForm.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="选择规则">
            <div class="rule-picker">
              <div class="picker-group">
                <div class="picker-group-title">输入侧</div>
                <el-checkbox-group v-model="bindForm.checked">
                  <el-checkbox
                    v-for="r in allRules.filter((x) => x.direction === 'input')"
                    :key="r.id"
                    :value="r.id"
                    :disabled="r.enabled !== 1"
                  >
                    <span>{{ r.name }}</span>
                    <span class="muted">（{{ actionLabel(r.action) }}）</span>
                  </el-checkbox>
                </el-checkbox-group>
                <div v-if="!allRules.filter((x) => x.direction === 'input').length" class="picker-empty">暂无输入侧规则</div>
              </div>
              <div class="picker-group">
                <div class="picker-group-title">输出侧</div>
                <el-checkbox-group v-model="bindForm.checked">
                  <el-checkbox
                    v-for="r in allRules.filter((x) => x.direction === 'output')"
                    :key="r.id"
                    :value="r.id"
                    :disabled="r.enabled !== 1"
                  >
                    <span>{{ r.name }}</span>
                    <span class="muted">（{{ actionLabel(r.action) }}）</span>
                  </el-checkbox>
                </el-checkbox-group>
                <div v-if="!allRules.filter((x) => x.direction === 'output').length" class="picker-empty">暂无输出侧规则</div>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <div class="form-tip">已选择 {{ bindForm.checked.length }} 条规则。停用状态的规则不可选。</div>
      </template>
      <template #footer>
        <el-button @click="bindDrawer = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="bindSaving" @click="saveBind">保存绑定</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.guard-page {
  max-width: 1400px;
  margin: 0 auto;
}
.guard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
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
.head-actions {
  display: flex;
  gap: 8px;
}
.guard-card {
  border-radius: var(--radius-lg);
  padding: 4px 12px 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.search-input {
  width: 240px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
.form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
}
.rule-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}
.rule-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.risk-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.content {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.app-name {
  font-weight: 600;
}
.bind-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--fill-lighter);
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}
.test-field {
  display: flex;
  flex-direction: column;
}
.test-clean {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  border-radius: var(--radius-md);
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  font-weight: 600;
}
.test-block {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
}
.test-block.blocked {
  background: var(--el-color-danger-light-9);
}
.test-block-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: var(--el-color-danger);
  font-size: 13px;
}
.test-block pre {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-color-danger);
}
.test-output {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
.hit-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hit-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 8px 10px;
}
.hit-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hit-index {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.hit-matched {
  margin-top: 6px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.rule-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px;
  background: var(--fill-lighter);
}
.picker-group {
  display: flex;
  flex-direction: column;
}
.picker-group-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.el-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.el-checkbox {
  height: auto;
  margin-right: 0;
}
.picker-empty {
  font-size: 12px;
  color: var(--text-tertiary);
  padding: 4px 0;
}
</style>
