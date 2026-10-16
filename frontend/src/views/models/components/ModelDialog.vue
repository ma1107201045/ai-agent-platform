<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { modelApi } from '@/api/model'
import type { ModelInfo, ModelProvider, ModelType } from '@/api/types'
import { CAP_OPTIONS, MODEL_TYPES, parseCaps } from '../modelMeta'

const emit = defineEmits<{ saved: [providerId: number] }>()

const modelDialog = ref(false)
const modelEditId = ref<number | null>(null)
const modelProviderName = ref('')
const modelForm = reactive<Partial<ModelInfo>>({})
const modelCapArr = ref<string[]>([])

function openCreate(provider: ModelProvider) {
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

function openEdit(m: ModelInfo, provider: ModelProvider) {
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
    emit('saved', modelForm.providerId!)
  } catch {
    /* 拦截器已统一提示 */
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog v-model="modelDialog" :title="modelEditId ? '编辑模型' : '添加模型'" width="560px">
    <el-form label-width="110px">
      <el-form-item label="所属供应商">
        <el-input :model-value="modelProviderName" disabled />
      </el-form-item>
      <el-form-item label="模型名" required>
        <el-input
          v-model="modelForm.name"
          class="mono"
          placeholder="调用 API 时使用的名称，如 deepseek-chat / gpt-4o / bge-m3"
          maxlength="80"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="模型类型" required>
        <el-select v-model="modelForm.modelType" style="width: 100%">
          <el-option
            v-for="t in MODEL_TYPES"
            :key="t.value"
            :label="`${t.label}（${t.value}）`"
            :value="t.value"
          />
        </el-select>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="上下文窗口">
            <el-input-number
              v-model="modelForm.contextWindow"
              :min="0"
              :step="1024"
              controls-position="right"
              style="width: 100%"
              placeholder="tokens"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最大输出">
            <el-input-number
              v-model="modelForm.maxTokens"
              :min="0"
              :step="512"
              controls-position="right"
              style="width: 100%"
              placeholder="tokens"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="能力标签">
        <el-select
          v-model="modelCapArr"
          multiple
          filterable
          collapse-tags
          style="width: 100%"
          placeholder="选择模型支持的能力，如函数调用 / 视觉 / 流式"
        >
          <el-option v-for="c in CAP_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-switch
          v-model="modelForm.status"
          :active-value="1"
          :inactive-value="0"
          active-text="启用"
          inactive-text="禁用"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="modelDialog = false">取消</el-button>
      <el-button type="primary" class="btn-gradient" @click="saveModel">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
</style>
