<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { memoryApi } from '@/api/memory'
import type { MemItem } from '@/api/types'
import { itemCategories } from '../memoryMeta'

/** 记忆条目 新增/编辑弹窗：expose open(row?)，保存成功 emit saved */
const props = defineProps<{ appId: number | null }>()
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = ref<Partial<MemItem>>({})
const rules: FormRules = {
  content: [{ required: true, message: '请输入记忆内容', trigger: 'blur' }]
}

function open(row?: MemItem) {
  form.value = row
    ? { ...row }
    : {
        appId: props.appId ?? 0,
        scope: 'global',
        source: 'manual',
        category: 'preference',
        importance: 3,
        content: '',
        status: 1,
        hitCount: 0
      }
  visible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || props.appId == null) return
  submitting.value = true
  try {
    const f = form.value
    if (f.id) {
      await memoryApi.updateItem(f.id, {
        content: f.content,
        category: f.category,
        scope: f.scope,
        importance: f.importance
      })
    } else {
      await memoryApi.createItem(props.appId, {
        appId: props.appId,
        scope: f.scope ?? 'global',
        source: 'manual',
        category: f.category ?? 'preference',
        content: f.content ?? '',
        importance: f.importance ?? 3,
        status: 1
      })
    }
    ElMessage.success(f.id ? '记忆已更新' : '记忆已新增')
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
    :title="form.id ? '编辑记忆' : '新增记忆'"
    width="560px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="类别" prop="category">
        <el-select v-model="form.category" class="full-width">
          <el-option v-for="c in itemCategories" :key="c.value" :value="c.value" :label="c.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="作用域" prop="scope">
        <el-radio-group v-model="form.scope">
          <el-radio value="global">全局记忆</el-radio>
          <el-radio value="user">用户记忆</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="内容" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          placeholder="例如：用户偏好简洁直接的回答风格，喜欢先给结论再给理由"
        />
      </el-form-item>
      <el-form-item label="重要度" prop="importance">
        <el-radio-group v-model="form.importance">
          <el-radio v-for="lv in [1, 2, 3, 4, 5]" :key="lv" :value="lv" class="importance-radio">
            {{ '★'.repeat(lv) }}
          </el-radio>
        </el-radio-group>
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
.importance-radio {
  margin-right: 6px;
}
</style>
