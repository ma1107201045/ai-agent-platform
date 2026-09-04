<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Link, Plus, ShoppingCart, VideoPlay } from '@element-plus/icons-vue'
import { appAgentToolApi } from '@/api/tool-info.ts'
import type { AppAgentTool } from '@/api/types'
import ParamsSchemaEditor from '@/components/tool/ParamsSchemaEditor.vue'
import HeadersEditor from '@/components/tool/HeadersEditor.vue'

const NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/
const typeLabels: Record<string, string> = { http: 'HTTP 请求', code: '代码脚本' }

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<AppAgentTool[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

/** 表格参数个数展示：由 parameters JSON 解析 */
function paramCount(tool: AppAgentTool) {
  if (!tool.parameters || !tool.parameters.trim()) return 0
  try {
    const props = JSON.parse(tool.parameters)?.properties
    return props ? Object.keys(props).length : 0
  } catch {
    return 0
  }
}

async function load() {
  loading.value = true
  try {
    const data = await appAgentToolApi.page({ page: page.value, size: size.value })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const dialogTitle = ref('新建工具')
const saving = ref(false)
const isEdit = ref(false)
const form = ref<Partial<AppAgentTool>>({})

function openCreate() {
  isEdit.value = false
  dialogTitle.value = '新建工具'
  form.value = { name: '', description: '', type: 'http', method: 'GET', authType: 'none', status: 1 }
  dialogVisible.value = true
}

function openEdit(tool: AppAgentTool) {
  isEdit.value = true
  dialogTitle.value = '编辑工具'
  form.value = { ...tool }
  dialogVisible.value = true
}

function onTypeChange() {
  if (form.value.type === 'code' && form.value.code === undefined) form.value.code = ''
  if (form.value.type === 'http') {
    if (!form.value.method) form.value.method = 'GET'
    if (!form.value.authType) form.value.authType = 'none'
  }
}

async function save() {
  if (!form.value.name) return ElMessage.warning('请填写工具名称')
  if (!NAME_RE.test(form.value.name)) {
    return ElMessage.warning('工具名称须为英文标识符（字母/数字/下划线，首字符非数字），如 get_weather')
  }
  if (!form.value.description?.trim()) return ElMessage.warning('请填写工具描述')
  if (form.value.type === 'http') {
    if (!form.value.url?.trim()) return ElMessage.warning('请填写请求地址')
    if (!/^https?:\/\//i.test(form.value.url!.trim())) {
      return ElMessage.warning('请求地址须以 http:// 或 https:// 开头')
    }
  } else if (form.value.type === 'code') {
    if (!form.value.code?.trim()) return ElMessage.warning('请填写代码脚本')
  }
  const p = (form.value.parameters || '').trim()
  if (p) {
    try {
      const parsed = JSON.parse(p)
      if (!parsed || typeof parsed !== 'object' || parsed.type !== 'object') throw new Error()
    } catch {
      return ElMessage.warning('参数 Schema 必须是合法 JSON 对象（可点预览 JSON 修正）')
    }
  } else {
    form.value.parameters = ''
  }
  saving.value = true
  try {
    if (isEdit.value && form.value.id) {
      await appAgentToolApi.update(form.value.id, form.value)
      ElMessage.success('保存成功')
    } else {
      await appAgentToolApi.create(form.value)
      ElMessage.success('创建成功，可在列表中「测试」验证效果')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/* ---------------- 启停 / 删除 ---------------- */
async function toggleStatus(row: AppAgentTool, val: string | number | boolean) {
  const next = val === true || val === 1 ? 1 : 0
  try {
    // updateById 会整体校验：全量提交当前行内容
    await appAgentToolApi.update(row.id, { ...row, status: next })
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
    load()
  } catch {
    load()
  }
}

async function remove(tool: AppAgentTool) {
  try {
    await ElMessageBox.confirm(`确认删除工具「${tool.name}」？删除后使用该工具的应用将无法再调用。`, '删除确认', {
      type: 'error',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await appAgentToolApi.remove(tool.id)
  ElMessage.success('已删除')
  load()
}

/* ---------------- 工具测试 ---------------- */
const testDialogVisible = ref(false)
const testTool = ref<AppAgentTool | null>(null)
const testing = ref(false)
const testResult = ref('')
/** schema 解析出的参数行 */
const testFields = ref<{ key: string; type: string; required: boolean; desc: string }[]>([])
const testValues = ref<Record<string, unknown>>({})
const useRawArgs = ref(false)
const rawArgs = ref('')

function parseToolParams(tool: AppAgentTool) {
  const fields: { key: string; type: string; required: boolean; desc: string }[] = []
  const src = (tool.parameters || '').trim()
  if (!src) return fields
  try {
    const schema = JSON.parse(src)
    const props = schema?.properties || {}
    const req: string[] = Array.isArray(schema?.required) ? schema.required : []
    Object.keys(props).forEach((k) => {
      fields.push({
        key: k,
        type: typeof props[k]?.type === 'string' ? props[k].type : 'string',
        required: req.includes(k),
        desc: typeof props[k]?.description === 'string' ? props[k].description : ''
      })
    })
  } catch {
    /* 非法 schema 忽略，走原始 JSON 输入 */
  }
  return fields
}

function openTest(tool: AppAgentTool) {
  testTool.value = tool
  testFields.value = parseToolParams(tool)
  testValues.value = {}
  rawArgs.value = ''
  useRawArgs.value = testFields.value.length === 0
  testResult.value = ''
  testDialogVisible.value = true
}

function buildTestArgs(): string {
  if (useRawArgs.value || !testFields.value.length) {
    return rawArgs.value.trim()
  }
  const obj: Record<string, unknown> = {}
  for (const f of testFields.value) {
    const v = testValues.value[f.key]
    if (v === undefined || v === null || v === '') {
      if (f.required) ElMessage.warning(`参数「${f.key}」为必填`)
      continue
    }
    if (f.type === 'integer' || f.type === 'number') obj[f.key] = Number(v)
    else if (f.type === 'boolean') obj[f.key] = v === true || v === 'true'
    else obj[f.key] = String(v)
  }
  return JSON.stringify(obj)
}

async function doTest() {
  if (!testTool.value) return
  testing.value = true
  try {
    const args = buildTestArgs()
    testResult.value = await appAgentToolApi.test(testTool.value.id, args)
  } finally {
    testing.value = false
  }
}

/* ---------------- 跨页引导 ---------------- */
const route = useRoute()
const router = useRouter()

function goMarket() {
  router.push('/tool/marketplace')
}
function goIntegrations() {
  router.push('/tool/integrations')
}

onMounted(async () => {
  await load()
  const q = route.query
  if (q.create === '1') {
    openCreate()
    router.replace({ query: {} }).catch(() => {})
    return
  }
  const id = Number(q.edit || q.test)
  if (!id) return
  try {
    const tool = await appAgentToolApi.get(id)
    if (q.edit) openEdit(tool)
    if (q.test) openTest(tool)
    router.replace({ query: {} }).catch(() => {})
  } catch {
    /* 工具可能已被删除 */
  }
})

const paramsSummary = computed(() => {
  const n = paramCount(form.value as AppAgentTool)
  return n ? `已配置 ${n} 个参数` : '暂无参数（模型调用时不传参）'
})
</script>

<template>
  <div class="page-container info-page">
    <!-- 引导条 -->
    <el-alert type="info" :closable="false" class="info-banner">
      <div class="banner-inner">
        <span class="banner-text">
          想最快接入一个工具？内置模板零配置 → <el-link type="primary" @click="goMarket">插件市场一键安装</el-link>；
          已有现成的 API / 数据库 → <el-link type="primary" @click="goIntegrations">数据集成自动生成</el-link>；
          以下表单适合手动创建。
        </span>
      </div>
    </el-alert>

    <div class="table-toolbar">
      <span class="total-text">共 {{ total }} 个工具</span>
      <div class="toolbar-actions">
        <el-button :icon="ShoppingCart" @click="goMarket">从插件市场安装</el-button>
        <el-button :icon="Link" @click="goIntegrations">去数据集成</el-button>
        <el-button type="primary" class="btn-gradient" :icon="Plus" @click="openCreate">新建工具</el-button>
      </div>
    </div>

    <!-- 空状态：引导用户走最省事的两条路 -->
    <el-card v-if="!loading && total === 0" shadow="never" class="empty-card">
      <el-empty description="还没有工具，智能体无法调用外部能力">
        <div class="empty-actions">
          <el-button type="primary" class="btn-gradient" :icon="ShoppingCart" @click="goMarket">
            去插件市场一键安装（推荐）
          </el-button>
          <el-button :icon="Link" @click="goIntegrations">已有 API？去数据集成生成</el-button>
          <el-button :icon="Plus" @click="openCreate">手动新建工具</el-button>
        </div>
      </el-empty>
    </el-card>

    <!-- 工具列表 -->
    <el-card v-else shadow="never">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="150">
          <template #default="{ row }">
            <code class="tool-name">{{ row.name }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="105">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'http' ? 'warning' : 'info'">{{ typeLabels[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参数" width="90" align="center">
          <template #default="{ row }">
            <span class="param-count">{{ paramCount(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="toggleStatus(row, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="VideoPlay" @click="openTest(row)">测试</el-button>
            <el-button link type="warning" :icon="EditPen" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 16px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="load"
      />
    </el-card>

    <!-- 新建 / 编辑工具 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" :close-on-click-modal="false" destroy-on-close>
      <div class="form-body">
        <!-- 基本信息 -->
        <div class="field-group">
          <div class="field-label">工具名称 <span class="req">*</span></div>
          <el-input v-model="form.name" :placeholder="isEdit ? '工具名称' : '英文标识符，如 get_weather'" />
          <p class="field-tip">
            智能体通过名称识别并调用该工具。可先用拼音或英文直译，如「查天气 → get_weather」；创建后仍可修改。
          </p>
        </div>
        <div class="field-group">
          <div class="field-label">工具描述 <span class="req">*</span></div>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="用大白话说明它做什么、什么时候调用。如：根据城市名称查询实时天气"
          />
          <p class="field-tip">描述写得越清楚，智能体越知道何时该调用它。</p>
        </div>

        <!-- 类型 -->
        <div class="field-group">
          <div class="field-label">工具类型</div>
          <el-radio-group v-model="form.type" @change="onTypeChange">
            <el-radio-button value="http">HTTP 请求（调用某个网址/接口）</el-radio-button>
            <el-radio-button value="code">代码脚本（处理数据 / 自定义计算）</el-radio-button>
          </el-radio-group>
        </div>

        <!-- HTTP -->
        <template v-if="form.type === 'http'">
          <div class="field-group">
            <div class="field-label">请求地址 <span class="req">*</span></div>
            <el-input v-model="form.url" placeholder="https://api.example.com/weather" />
          </div>
          <div class="field-row">
            <div class="field-group grow">
              <div class="field-label">请求方式</div>
              <el-select v-model="form.method" style="width: 100%">
                <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
              </el-select>
            </div>
            <div class="field-group grow">
              <div class="field-label">鉴权方式</div>
              <el-select v-model="form.authType" style="width: 100%">
                <el-option label="无" value="none" />
                <el-option label="Bearer Token" value="bearer" />
              </el-select>
            </div>
          </div>
          <div v-if="form.authType === 'bearer'" class="field-group">
            <div class="field-label">Token</div>
            <el-input v-model="form.authToken" type="password" show-password placeholder="访问该接口所需的 Bearer Token" />
          </div>

          <el-collapse class="adv-collapse">
            <el-collapse-item title="高级：请求头 Headers（可选）">
              <HeadersEditor v-model="form.headers" />
            </el-collapse-item>
          </el-collapse>
        </template>

        <!-- 代码 -->
        <template v-else>
          <div class="field-group">
            <div class="field-label">代码脚本 <span class="req">*</span></div>
            <el-input
              v-model="form.code"
              type="textarea"
              :rows="8"
              placeholder="MVEL 脚本：可直接使用参数名与 input（参数整体），return 返回结果。&#10;示例：&#10;return '城市 ' + city + ' 今天晴朗，25℃'"
            />
            <p class="field-tip">适合在智能体调用时对入参做加工 / 返回固定结果，无需请求外部服务。</p>
          </div>
        </template>

        <!-- 参数 -->
        <div class="field-group">
          <div class="field-label params-label">
            参数说明
            <span class="field-tip-inline">（{{ paramsSummary }}）</span>
          </div>
          <ParamsSchemaEditor v-model="form.parameters" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 工具测试 -->
    <el-dialog v-model="testDialogVisible" :title="`工具测试 - ${testTool?.name || ''}`" width="620px" destroy-on-close>
      <el-alert type="info" :closable="false" class="test-tip">
        模拟智能体调用：按下方参数填入示例值（形如模型会生成的真实调用）。
      </el-alert>

      <template v-if="testFields.length && !useRawArgs">
        <div class="test-field-row" v-for="f in testFields" :key="f.key">
          <div class="test-field-label">
            <code class="mono-chip">{{ f.key }}</code>
            <el-tag v-if="f.required" size="small" type="danger" effect="light">必填</el-tag>
            <span class="test-field-desc">{{ f.desc }}</span>
          </div>
          <el-input-number
            v-if="f.type === 'integer' || f.type === 'number'"
            v-model="testValues[f.key]"
            controls-position="right"
            style="width: 100%"
            :placeholder="f.desc || f.key"
          />
          <el-select v-else-if="f.type === 'boolean'" v-model="testValues[f.key]" style="width: 100%">
            <el-option label="是 / true" :value="true" />
            <el-option label="否 / false" :value="false" />
          </el-select>
          <el-input v-else v-model="testValues[f.key]" :placeholder="`示例：${f.desc || f.key}`" />
        </div>
        <el-button link type="primary" class="raw-toggle" @click="useRawArgs = true">改用原始 JSON 输入（高级）</el-button>
      </template>

      <template v-else>
        <el-input
          v-model="rawArgs"
          type="textarea"
          :rows="4"
          spellcheck="false"
          placeholder='JSON 对象，如 {"city":"北京"}；留空表示无参数'
        />
        <div v-if="testFields.length" class="raw-back">
          <el-button link type="primary" @click="useRawArgs = false">返回表单填写</el-button>
        </div>
      </template>

      <el-form label-width="80px" style="margin-top: 12px">
        <el-form-item label="执行结果">
          <el-input v-model="testResult" type="textarea" :rows="6" readonly placeholder="点击「执行测试」后展示结果" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="testDialogVisible = false">关闭</el-button>
        <el-button type="primary" :icon="VideoPlay" :loading="testing" @click="doTest">执行测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.info-page {
  max-width: 1400px;
  margin: 0 auto;
}
.info-banner {
  margin-bottom: 16px;
  border-radius: var(--radius-md);
}
.banner-inner {
  display: flex;
  align-items: center;
  gap: 4px;
  line-height: 1.7;
}
.banner-text {
  font-size: 13px;
}
.total-text {
  color: var(--text-tertiary);
  font-size: 13px;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.tool-name {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  background: var(--fill-light);
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--brand-1);
}
.param-count {
  font-size: 13px;
  color: var(--text-secondary);
}
.empty-card {
  padding: 12px 0;
}
.empty-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 6px;
}
/* 表单 */
.form-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}
.field-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field-row {
  display: flex;
  gap: 16px;
}
.grow {
  flex: 1;
}
.field-label {
  font-size: 13px;
  font-weight: 600;
}
.req {
  color: #f56c6c;
}
.field-tip {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
.field-tip-inline {
  font-weight: 400;
  color: var(--text-tertiary);
}
.params-label {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.adv-collapse {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
/* 测试对话框 */
.test-tip {
  margin-bottom: 14px;
  border-radius: var(--radius-sm);
}
.test-field-row {
  margin-bottom: 12px;
}
.test-field-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.test-field-desc {
  font-size: 12px;
  color: var(--text-tertiary);
}
.mono-chip {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--brand-1);
}
.raw-toggle {
  font-size: 12px;
}
.raw-back {
  margin-top: 8px;
}
</style>
