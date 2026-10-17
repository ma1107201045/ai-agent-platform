<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { dataStoreApi } from '@/api/data-store'
import type { AnyRow } from '../storageMeta'

/** 导入数据弹窗（JSON 粘贴 / CSV 上传）：expose open()；导入成功 emit imported */
const props = defineProps<{ tableId: number | null }>()
const emit = defineEmits<{ imported: [] }>()

const visible = ref(false)
const importMode = ref<'json' | 'csv'>('json')
const importJsonText = ref('')
const importCsvFile = ref<File | null>(null)
const importing = ref(false)

function open() {
  visible.value = true
}

function onCsvFileChange(file: { raw?: File }) {
  if (file.raw) importCsvFile.value = file.raw
}

function onCsvExceed() {
  ElMessage.warning('每次仅支持一个 CSV 文件')
}

async function doImport() {
  if (props.tableId == null) return
  importing.value = true
  try {
    let count: number
    if (importMode.value === 'json') {
      let rows: AnyRow[]
      try {
        const parsed = JSON.parse(importJsonText.value)
        if (!Array.isArray(parsed)) throw new Error('JSON 顶层应为数组')
        rows = parsed
      } catch (e) {
        ElMessage.error(`JSON 解析失败：${(e as Error).message}`)
        return
      }
      if (!rows.length) {
        ElMessage.warning('没有可导入的数据')
        return
      }
      count = await dataStoreApi.importJson(props.tableId, rows)
    } else {
      if (!importCsvFile.value) {
        ElMessage.warning('请先选择 CSV 文件')
        return
      }
      count = await dataStoreApi.importCsv(props.tableId, importCsvFile.value)
    }
    ElMessage.success(`成功导入 ${count} 行记录`)
    visible.value = false
    importJsonText.value = ''
    importCsvFile.value = null
    emit('imported')
  } finally {
    importing.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="导入数据" width="620px" destroy-on-close>
    <el-radio-group v-model="importMode" class="import-mode">
      <el-radio-button value="json">粘贴 JSON</el-radio-button>
      <el-radio-button value="csv">上传 CSV</el-radio-button>
    </el-radio-group>

    <template v-if="importMode === 'json'">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="import-alert"
        title='按行粘贴 JSON 数组，键与列 key 对应，例如：[{"name": "张三", "age": 30}]'
      />
      <el-input
        v-model="importJsonText"
        type="textarea"
        :rows="8"
        placeholder='[{"name": "张三", "age": 30}, {"name": "李四", "age": 28}]'
        class="full-width"
      />
    </template>

    <template v-else>
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept=".csv"
        :on-change="onCsvFileChange"
        :on-exceed="onCsvExceed"
        :on-remove="() => (importCsvFile = null)"
        class="csv-uploader"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将 CSV 拖到此处，或<em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">
            首行需为表头（列名或列 key）；UTF-8 编码；大小不超过 5MB。
          </div>
        </template>
      </el-upload>
    </template>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="importing" @click="doImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}
.import-mode {
  margin-bottom: 14px;
}
.import-alert {
  margin-bottom: 10px;
}
.csv-uploader {
  width: 100%;
}
</style>
