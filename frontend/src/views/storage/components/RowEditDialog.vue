<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dataStoreApi } from '@/api/data-store'
import type { DataColumn, DataRecordRow } from '@/api/types'
import { blankRow, type AnyRow } from '../storageMeta'

/** 行记录 新增/编辑 弹窗：expose open(row?)；保存成功 emit saved */
const props = defineProps<{ tableId: number | null; cols: DataColumn[] }>()
const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const rowForm = ref<{ id?: number; data: AnyRow }>({ data: {} })

function open(row?: DataRecordRow) {
  if (row) {
    const data: AnyRow = {}
    for (const c of props.cols) {
      const v = row.data?.[c.key]
      data[c.key] = v === undefined || v === null ? (c.type === 'boolean' ? false : '') : v
    }
    rowForm.value = { id: row.id, data }
  } else {
    rowForm.value = { data: blankRow(props.cols) }
  }
  visible.value = true
}

async function submit() {
  if (props.tableId == null) return
  submitting.value = true
  try {
    const data: AnyRow = {}
    for (const c of props.cols) {
      const v = rowForm.value.data[c.key]
      if (c.type === 'text') {
        data[c.key] = (v ?? '').toString().trim()
      } else {
        data[c.key] = v
      }
    }
    if (rowForm.value.id) {
      await dataStoreApi.updateRecord(rowForm.value.id, data)
      ElMessage.success('记录已更新')
    } else {
      await dataStoreApi.createRecord(props.tableId, data)
      ElMessage.success('记录已新增')
    }
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
    :title="rowForm.id ? '编辑记录' : '新增记录'"
    width="640px"
    destroy-on-close
  >
    <el-form :model="rowForm.data" label-width="110px" class="row-form">
      <el-form-item v-for="c in cols" :key="c.key" :label="c.label || c.key">
        <el-input
          v-if="c.type === 'text'"
          v-model="rowForm.data[c.key]"
          :placeholder="`请输入${c.label || c.key}`"
        />
        <el-input-number
          v-else-if="c.type === 'number'"
          v-model="rowForm.data[c.key]"
          :controls="false"
          class="full-width"
        />
        <el-switch
          v-else-if="c.type === 'boolean'"
          v-model="rowForm.data[c.key]"
          active-text="是"
          inactive-text="否"
        />
        <el-date-picker
          v-else-if="c.type === 'date'"
          v-model="rowForm.data[c.key]"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          class="full-width"
        />
        <el-select
          v-else-if="c.type === 'select'"
          v-model="rowForm.data[c.key]"
          clearable
          placeholder="请选择"
          class="full-width"
        >
          <el-option v-for="opt in c.options || []" :key="opt" :value="opt" :label="opt" />
        </el-select>
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
</style>
