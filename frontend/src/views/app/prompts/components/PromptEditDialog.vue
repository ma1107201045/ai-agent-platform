<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { appPromptApi } from '@/api/app-prompt'
import type { PromptTemplate } from '@/api/types'
import { VAR_L, VAR_R, VARS_PLACEHOLDER, categoryOptions, varSample } from '../promptMeta'

const emit = defineEmits<{ saved: [] }>()

const dialogVisible = ref(false)
const saving = ref(false)
const editId = ref<number | null>(null)
const form = reactive({
  name: '',
  description: '',
  category: 'general',
  content: '',
  variables: '',
  status: 1
})

function openCreate() {
  editId.value = null
  form.name = ''
  form.description = ''
  form.category = 'general'
  form.content = ''
  form.variables = ''
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: PromptTemplate) {
  editId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.category = row.category || 'general'
  form.content = row.content
  form.variables = row.variables || ''
  form.status = row.status ?? 1
  dialogVisible.value = true
}

/** 从正文提取 {{var}} 生成变量定义 JSON */
async function extractVars() {
  if (!form.content.trim()) {
    ElMessage.warning('请先填写模板正文')
    return
  }
  const names = await appPromptApi.extractVariables(form.content)
  if (names.length === 0) {
    ElMessage.info('正文中未发现变量占位符')
    return
  }
  let parsed: { name: string; desc?: string }[] = []
  try {
    parsed = form.variables ? JSON.parse(form.variables) : []
  } catch {
    parsed = []
  }
  const seen = new Set(parsed.map((v) => v.name))
  for (const n of names) {
    if (!seen.has(n)) {
      parsed.push({ name: n, desc: '' })
    }
  }
  form.variables = JSON.stringify(parsed, null, 2)
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请输入模板正文')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description,
      category: form.category,
      content: form.content,
      variables: form.variables || undefined,
      status: form.status
    }
    if (editId.value) {
      await appPromptApi.update(editId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await appPromptApi.create(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="editId ? '编辑模板' : '新建模板'"
    width="720px"
    :close-on-click-modal="false"
    class="edit-dialog"
  >
    <div class="form-body">
      <div class="field-row">
        <div class="field-group grow">
          <div class="field-label">模板名称 <span class="req">*</span></div>
          <el-input
            v-model="form.name"
            placeholder="例如：智能客服开场白"
            maxlength="128"
            show-word-limit
          />
        </div>
        <div class="field-group" style="width: 180px">
          <div class="field-label">分类</div>
          <el-select v-model="form.category" style="width: 100%">
            <el-option
              v-for="c in categoryOptions"
              :key="c.value"
              :label="c.label"
              :value="c.value"
            />
          </el-select>
        </div>
      </div>

      <div class="field-group">
        <div class="field-label">模板描述</div>
        <el-input
          v-model="form.description"
          placeholder="说明模板的用途与适用场景（可选）"
          maxlength="512"
        />
      </div>

      <div class="field-group">
        <div class="field-label">
          模板正文 <span class="req">*</span>
          <span class="tip">支持 {{ VAR_L }}变量名{{ VAR_R }} 占位，试跑时自动替换</span>
        </div>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="8"
          :placeholder="'你是' + varSample('role') + '，请帮我…'"
        />
      </div>

      <div class="field-group">
        <div class="field-label">
          变量定义
          <el-button link type="primary" size="small" @click="extractVars">从正文提取</el-button>
          <span class="tip">JSON 数组，可留空</span>
        </div>
        <el-input
          v-model="form.variables"
          type="textarea"
          :rows="4"
          :placeholder="VARS_PLACEHOLDER"
          class="var-input"
        />
      </div>

      <div class="field-group status-row">
        <span class="field-label">状态</span>
        <el-switch
          v-model="form.status"
          :active-value="1"
          :inactive-value="0"
          active-text="启用"
          inactive-text="禁用"
        />
      </div>
    </div>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" class="btn-gradient" :loading="saving" @click="save"
        >保存</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped>
.edit-dialog .form-body {
  padding: 0 4px;
}
.field-row {
  display: flex;
  gap: 12px;
}
.field-group {
  margin-bottom: 14px;
}
.field-group.grow {
  flex: 1;
}
.field-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-primary);
}
.req {
  color: #ef4444;
}
.tip {
  font-weight: 400;
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.var-input {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 0;
}
.status-row .field-label {
  margin-bottom: 0;
}
</style>
