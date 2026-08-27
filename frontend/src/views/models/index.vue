<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '@/api/model'
import type { ModelInfo, ModelProvider } from '@/api/types'

const loading = ref(false)
const providers = ref<ModelProvider[]>([])
const dialogVisible = ref(false)
const editing = ref<Partial<ModelProvider>>({})
const isEdit = ref(false)

const modelTypes = [
  { value: 'llm', label: 'LLM 对话' },
  { value: 'embedding', label: '向量 Embedding' },
  { value: 'rerank', label: '重排序 Rerank' },
  { value: 'tts', label: '语音合成' },
  { value: 'asr', label: '语音识别' },
  { value: 'image', label: '图像生成' }
]

// 展开的供应商及其模型
const expandedModels = reactive<Record<number, ModelInfo[]>>({})

async function load() {
  loading.value = true
  try {
    const data = await modelApi.providerPage({ page: 1, size: 100 })
    providers.value = data.records
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = { type: 'openai-compatible', status: 1 }
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row: ModelProvider) {
  editing.value = { ...row }
  isEdit.value = true
  dialogVisible.value = true
}

async function save() {
  if (!editing.value.name) {
    ElMessage.warning('请输入供应商名称')
    return
  }
  if (isEdit.value && editing.value.id) {
    await modelApi.updateProvider(editing.value.id, editing.value)
  } else {
    await modelApi.createProvider(editing.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

function remove(row: ModelProvider) {
  ElMessageBox.confirm(`确认删除「${row.name}」及其全部模型？`, '删除确认', {
    type: 'error'
  })
    .then(async () => {
      await modelApi.removeProvider(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

async function toggleModels(row: ModelProvider, expanded: boolean) {
  if (expanded && !expandedModels[row.id]) {
    expandedModels[row.id] = await modelApi.modelsOf(row.id)
  }
}

function addModel(provider: ModelProvider) {
  ElMessageBox.prompt(
    `为「${provider.name}」添加模型，格式: 模型名|类型，如 deepseek-chat|llm`,
    '添加模型',
    {
      confirmButtonText: '添加',
      inputPattern: /.+\|.+/,
      inputErrorMessage: '格式应为: 模型名|类型'
    }
  )
    .then(async ({ value }) => {
      const [name, type] = value.split('|')
      await modelApi.createModel(provider.id, {
        name: name.trim(),
        modelType: type.trim() as ModelInfo['modelType'],
        status: 1
      })
      ElMessage.success('添加成功')
      expandedModels[provider.id] = await modelApi.modelsOf(provider.id)
    })
    .catch(() => {})
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="table-toolbar">
      <span style="color: #909399">共 {{ providers.length }} 个供应商</span>
      <el-button type="primary" @click="openCreate">添加供应商</el-button>
    </div>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="providers"
        row-key="id"
        @expand-change="(row, expanded) => toggleModels(row, expanded)"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 24px">
              <el-table :data="expandedModels[row.id] || []" size="small">
                <el-table-column prop="name" label="模型名" min-width="180" />
                <el-table-column label="类型" width="140">
                  <template #default="{ row: m }">
                    <el-tag size="small">{{ m.modelType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100">
                  <template #default="{ row: m }">
                    <el-button
                      link
                      type="danger"
                      @click="
                        modelApi.removeModel(m.id).then(() => {
                          expandedModels[row.id] = expandedModels[row.id].filter((x) => x.id !== m.id)
                          ElMessage.success('删除成功')
                        })
                      "
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button size="small" style="margin-top: 8px" @click="addModel(row)">
                添加模型
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="type" label="类型" width="160" />
        <el-table-column prop="baseUrl" label="API 地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑供应商' : '添加供应商'"
      width="520px"
    >
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="editing.name" placeholder="如 DeepSeek / OpenAI" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editing.type" style="width: 100%">
            <el-option label="OpenAI 兼容" value="openai-compatible" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 地址">
          <el-input v-model="editing.baseUrl" placeholder="如 https://api.deepseek.com/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="editing.apiKey"
            type="password"
            show-password
            placeholder="sk-..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
