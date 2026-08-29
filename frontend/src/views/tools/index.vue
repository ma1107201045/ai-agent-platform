<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus, VideoPlay } from '@element-plus/icons-vue'
import { toolApi } from '@/api/tool'
import type { AppTool } from '@/api/types'

const loading = ref(false)
const list = ref<AppTool[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const dialogVisible = ref(false)
const dialogTitle = ref('')
const saving = ref(false)
const form = ref<Partial<AppTool>>({})
const headersText = ref('')

const testDialogVisible = ref(false)
const testTool = ref<AppTool | null>(null)
const testArgs = ref('')
const testResult = ref('')
const testing = ref(false)

const typeLabels: Record<string, string> = { http: 'HTTP 请求', code: '代码脚本' }
const authLabels: Record<string, string> = { none: '无', bearer: 'Bearer', basic: 'Basic' }

async function load() {
  loading.value = true
  try {
    const data = await toolApi.page({ page: page.value, size: size.value })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogTitle.value = '新建工具'
  form.value = { name: '', description: '', type: 'http', method: 'GET', authType: 'none', status: 1 }
  headersText.value = ''
  dialogVisible.value = true
}

function openEdit(tool: AppTool) {
  dialogTitle.value = '编辑工具'
  form.value = { ...tool }
  headersText.value = tool.headers || ''
  dialogVisible.value = true
}

function onTypeChange() {
  if (form.value.type === 'code' && form.value.code === undefined) form.value.code = ''
}

function save() {
  if (!form.value.name) return ElMessage.warning('请输入工具名称')
  if (!form.value.description) return ElMessage.warning('请输入工具描述')
  if (form.value.type === 'http' && !form.value.url) return ElMessage.warning('请输入请求地址')
  if (form.value.type === 'code' && !form.value.code) return ElMessage.warning('请输入代码脚本')
  // Headers JSON 校验
  const t = headersText.value.trim()
  if (t) {
    try {
      form.value.headers = JSON.stringify(JSON.parse(t))
    } catch {
      return ElMessage.warning('Headers 必须是合法 JSON 对象')
    }
  } else {
    form.value.headers = ''
  }
  saving.value = true
  try {
    if (form.value.id) {
      toolApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      toolApi.create(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(tool: AppTool) {
  try {
    await ElMessageBox.confirm(`确认删除工具「${tool.name}」？`, '删除确认', {
      type: 'error', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await toolApi.remove(tool.id)
  ElMessage.success('已删除')
  load()
}

function openTest(tool: AppTool) {
  testTool.value = tool
  testArgs.value = ''
  testResult.value = ''
  testDialogVisible.value = true
}

async function doTest() {
  if (!testTool.value) return
  testing.value = true
  try {
    testResult.value = await toolApi.test(testTool.value.id, testArgs.value)
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="table-toolbar">
      <span style="color: #909399">共 {{ total }} 个工具</span>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建工具</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="150">
          <template #default="{ row }">
            <code class="tool-name">{{ row.name }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'http' ? 'warning' : 'info'">
              {{ typeLabels[row.type] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="鉴权" width="90">
          <template #default="{ row }">
            <span v-if="row.type === 'http'">{{ authLabels[row.authType] || '无' }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
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

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="工具名称" required>
          <el-input v-model="form.name" placeholder="英文标识符，如 get_weather" />
        </el-form-item>
        <el-form-item label="工具描述" required>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="描述工具的用途与参数，模型据此决定何时调用（如：根据城市名称查询天气）"
          />
        </el-form-item>
        <el-form-item label="工具类型">
          <el-radio-group v-model="form.type" @change="onTypeChange">
            <el-radio-button value="http">HTTP 请求</el-radio-button>
            <el-radio-button value="code">代码脚本</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="form.type === 'http'">
          <el-form-item label="请求地址" required>
            <el-input v-model="form.url" placeholder="https://api.example.com/weather" />
          </el-form-item>
          <el-form-item label="请求方式">
            <el-select v-model="form.method" style="width: 100%">
              <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="自定义 Headers">
            <el-input
              v-model="headersText"
              type="textarea"
              :rows="2"
              placeholder='JSON 格式，如 {"X-Api-Key":"xxx"}'
            />
          </el-form-item>
          <el-form-item label="鉴权方式">
            <el-select v-model="form.authType" style="width: 100%">
              <el-option label="无" value="none" />
              <el-option label="Bearer Token" value="bearer" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.authType === 'bearer'" label="Token">
            <el-input v-model="form.authToken" placeholder="Bearer Token" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="代码脚本" required>
            <el-input
              v-model="form.code"
              type="textarea"
              :rows="10"
              placeholder="MVEL 脚本，可用各参数变量与 input（参数整体），return 返回结果&#10;示例：&#10;return city + ' 的天气晴朗，25℃'"
            />
          </el-form-item>
        </template>

        <el-form-item label="参数 Schema">
          <el-input
            v-model="form.parameters"
            type="textarea"
            :rows="4"
            placeholder='JSON Schema，如 {"type":"object","properties":{"city":{"type":"string","description":"城市名"}},"required":["city"]}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试对话框 -->
    <el-dialog v-model="testDialogVisible" :title="`工具测试 - ${testTool?.name || ''}`" width="600px">
      <el-form label-width="80px">
        <el-form-item label="参数(JSON)">
          <el-input
            v-model="testArgs"
            type="textarea"
            :rows="4"
            placeholder='如 {"city":"北京"}；留空表示无参数'
          />
        </el-form-item>
        <el-form-item label="结果">
          <el-input v-model="testResult" type="textarea" :rows="6" readonly placeholder="点击测试后显示结果" />
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
.tool-name {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: #409eff;
}
</style>
