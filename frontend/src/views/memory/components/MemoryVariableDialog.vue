<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { memoryApi } from '@/api/memory'
import type { MemVariable } from '@/api/types'

/** 会话变量 新增/编辑弹窗：expose open(row?)，保存成功 emit saved */
const props = defineProps<{ appId: number | null }>()
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = ref<Partial<MemVariable>>({})
const rules: FormRules = {
  name: [
    { required: true, message: '请输入变量名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/,
      message: '须为字母/数字/下划线，且以字母或下划线开头',
      trigger: 'blur'
    }
  ]
}

function open(row?: MemVariable) {
  form.value = row
    ? { ...row }
    : {
        appId: props.appId ?? 0,
        scope: 'global',
        valueType: 'string',
        name: '',
        value: '',
        status: 1
      }
  visible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || props.appId == null) return
  submitting.value = true
  try {
    const f = form.value
    const payload = {
      name: f.name?.trim(),
      scope: f.scope ?? 'global',
      valueType: f.valueType ?? 'string',
      value: f.value ?? '',
      remark: f.remark,
      conversationId: f.scope === 'session' && f.conversationId ? f.conversationId : null,
      status: 1
    }
    if (f.id) {
      await memoryApi.updateVariable(f.id, payload)
    } else {
      await memoryApi.createVariable(props.appId, {
        appId: props.appId,
        ...payload
      })
    }
    ElMessage.success(f.id ? '变量已更新' : '变量已新增')
    visible.value = false
    emit('saved')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="form.id ? '编辑变量' : '新增变量'"
    width="560px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="变量名" prop="name">
        <el-input v-model="form.name" placeholder="英文变量名，如 user_city，运行时按变量名引用" />
      </el-form-item>
      <el-form-item label="作用域" prop="scope">
        <el-radio-group v-model="form.scope">
          <el-radio value="global">全局</el-radio>
          <el-radio value="session">会话</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.scope === 'session'" label="会话ID">
        <el-input-number
          v-model="form.conversationId"
          :min="0"
          :controls="false"
          placeholder="空 = 全部会话"
          class="full-width"
        />
        <div class="field-tip">为空时该变量作用于此应用的全部会话</div>
      </el-form-item>
      <el-form-item label="类型" prop="valueType">
        <el-select v-model="form.valueType" class="full-width">
          <el-option value="string" label="string 字符串" />
          <el-option value="number" label="number 数字" />
          <el-option value="boolean" label="boolean 布尔" />
          <el-option value="json" label="json 对象" />
        </el-select>
      </el-form-item>
      <el-form-item label="变量值" prop="value">
        <el-input
          v-model="form.value"
          type="textarea"
          :rows="3"
          placeholder="变量内容（json 类型请输入 JSON 文本）"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="可选，说明该变量的用途" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}
.field-tip {
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
</style>
