<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { modelApi } from '@/api/model'
import type { ModelProvider } from '@/api/types'

const emit = defineEmits<{ saved: [] }>()

const providerDialog = ref(false)
const providerEditId = ref<number | null>(null)
const providerForm = reactive<Partial<ModelProvider>>({})

const apiKeyPlaceholder = computed(() =>
  providerEditId.value ? '已配置（留空则不修改）' : 'sk-...'
)

function openCreate() {
  providerEditId.value = null
  Object.assign(providerForm, {
    name: '',
    type: 'openai-compatible',
    baseUrl: '',
    apiKey: '',
    status: 1
  })
  providerDialog.value = true
}

function openEdit(row: ModelProvider) {
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
    emit('saved')
  } catch {
    /* 拦截器已统一提示 */
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog
    v-model="providerDialog"
    :title="providerEditId ? '编辑供应商' : '添加供应商'"
    width="560px"
  >
    <el-form label-width="110px">
      <el-form-item label="名称" required>
        <el-input
          v-model="providerForm.name"
          placeholder="如 DeepSeek / OpenAI / 硅基流动"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="类型" required>
        <el-select
          v-model="providerForm.type"
          filterable
          allow-create
          default-first-option
          style="width: 100%"
        >
          <el-option
            label="OpenAI 兼容（DeepSeek / Qwen / Kimi / Ollama 等）"
            value="openai-compatible"
          />
        </el-select>
        <div class="form-tip">
          内置支持 openai-compatible；自定义类型需平台注册对应 LLMProvider 才能调用
        </div>
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
        <el-switch
          v-model="providerForm.status"
          :active-value="1"
          :inactive-value="0"
          active-text="启用"
          inactive-text="禁用"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="providerDialog = false">取消</el-button>
      <el-button type="primary" class="btn-gradient" @click="saveProvider">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
}
</style>
