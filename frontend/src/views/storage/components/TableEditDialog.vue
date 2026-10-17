<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { dataStoreApi } from '@/api/data-store'
import type { DataColumn, DataTable } from '@/api/types'
import { newColumn, tableColumns, type ColumnEditRow } from '../storageMeta'

/** 数据表 新建/列设置 弹窗：expose open(table?)；保存成功 emit saved(table, isEdit) */
const emit = defineEmits<{ saved: [table: DataTable, isEdit: boolean] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = ref<{
  id?: number
  name: string
  label?: string
  description?: string
  columns: ColumnEditRow[]
}>({
  name: '',
  label: '',
  description: '',
  columns: []
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入数据表名称', trigger: 'blur' }]
}

function addColumn() {
  form.value.columns.push(newColumn())
}

function removeColumn(idx: number) {
  form.value.columns.splice(idx, 1)
}

function open(table?: DataTable) {
  if (table) {
    const colsOf = tableColumns(table)
    form.value = {
      id: table.id,
      name: table.name,
      label: table.label,
      description: table.description,
      columns: colsOf.map((c) => ({ ...c, optionsText: (c.options || []).join(',') }))
    }
  } else {
    form.value = {
      name: '',
      label: '',
      description: '',
      columns: [newColumn(), newColumn()]
    }
  }
  visible.value = true
}

function buildColumnsPayload(): DataColumn[] {
  return form.value.columns
    .filter((c) => c.key.trim())
    .map((c) => {
      const base: DataColumn = {
        key: c.key.trim(),
        label: c.label?.trim() || c.key.trim(),
        type: c.type
      }
      if (c.type === 'select') {
        base.options = (c.optionsText || '')
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean)
      }
      return base
    })
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!buildColumnsPayload().length) {
    ElMessage.warning('请至少填写一个有效的列 key')
    return
  }
  submitting.value = true
  try {
    const payload = {
      name: form.value.name.trim(),
      label: form.value.label?.trim(),
      description: form.value.description,
      columns: buildColumnsPayload()
    }
    if (form.value.id) {
      const updated = await dataStoreApi.updateTable(form.value.id, payload)
      ElMessage.success('数据表已更新')
      // 编辑（列设置）场景保留弹窗，便于继续调整
      emit('saved', updated, true)
    } else {
      const created = await dataStoreApi.createTable(payload)
      ElMessage.success('数据表已创建')
      visible.value = false
      emit('saved', created, false)
    }
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="form.id ? '数据表设置' : '新建数据表'"
    width="720px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="表名" prop="name">
        <el-input v-model="form.name" placeholder="数据表名称（租户内唯一），如：客户资料" />
      </el-form-item>
      <el-form-item label="显示名">
        <el-input v-model="form.label" placeholder="可选，列表页展示的别名" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="可选，说明这张表存什么"
        />
      </el-form-item>
      <el-form-item label="列定义">
        <div class="columns-editor">
          <div v-for="(col, idx) in form.columns" :key="idx" class="column-row">
            <el-input v-model="col.key" placeholder="key（字母/下划线）" class="col-key" />
            <el-input v-model="col.label" placeholder="列名（显示用）" class="col-label" />
            <el-select v-model="col.type" class="col-type">
              <el-option value="text" label="文本 text" />
              <el-option value="number" label="数字 number" />
              <el-option value="boolean" label="布尔 boolean" />
              <el-option value="date" label="日期 date" />
              <el-option value="select" label="选项 select" />
            </el-select>
            <el-input
              v-if="col.type === 'select'"
              v-model="col.optionsText"
              placeholder="选项，用英文逗号分隔"
              class="col-options"
            />
            <el-button
              link
              type="danger"
              :icon="Delete"
              :disabled="form.columns.length <= 1"
              @click="removeColumn(idx)"
            />
          </div>
          <div class="columns-actions">
            <el-button size="small" type="primary" plain @click="addColumn">
              <el-icon><Plus /></el-icon>&nbsp;添加列
            </el-button>
            <span class="editor-tip"
              >列的 key 用于记录字段与模板引用；已有数据后再调整列名不会清空数据。</span
            >
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.columns-editor {
  width: 100%;
  border: 1px dashed var(--border-color, #d9dce1);
  border-radius: 10px;
  padding: 12px;
}
.column-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.col-key {
  width: 170px;
}
.col-label {
  width: 140px;
}
.col-type {
  width: 130px;
}
.col-options {
  flex: 1;
}
.columns-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.editor-tip {
  font-size: 12px;
  color: var(--text-tertiary, #8a8f98);
}
</style>
