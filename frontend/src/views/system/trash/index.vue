<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Delete, Refresh, RefreshLeft, Lightning, Warning} from '@element-plus/icons-vue'
import {trashApi, type TrashItem, type TrashType} from '@/api/system-trash'

const typeMeta: Record<string, { label: string; tag: 'primary' | 'success' | 'warning' | 'info' }> = {
  agent: {label: '智能体应用', tag: 'primary'},
  dataset: {label: '知识库', tag: 'success'},
  template: {label: '应用模板', tag: 'warning'},
  announcement: {label: '系统公告', tag: 'info'}
}
const typeOptions = [
  {value: '', label: '全部'},
  ...Object.entries(typeMeta).map(([value, m]) => ({value, label: m.label}))
]

const items = ref<TrashItem[]>([])
const loading = ref(false)
const filterType = ref('')
const keyword = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return items.value
  return items.value.filter((i) => (i.name || '').includes(kw) || (typeMeta[i.type]?.label || '').includes(kw))
})
const countByType = (type: string) => (type ? items.value.filter((i) => i.type === type).length : items.value.length)

async function load() {
  loading.value = true
  try {
    items.value = await trashApi.list(filterType.value || undefined)
  } finally {
    loading.value = false
  }
}

function fmt(s?: string) {
  if (!s) return '—'
  return s.replace('T', ' ').slice(0, 19)
}

async function restore(row: TrashItem) {
  await trashApi.restore(row.type as TrashType, row.id)
  ElMessage.success(`已恢复「${row.name}」，将重新出现在原列表中`)
  load()
}

async function purge(row: TrashItem) {
  const cascadeNote = row.type === 'agent' || row.type === 'dataset'
      ? '\n\n彻底删除后会连同其关联数据（版本/会话/消息 或 文档/分段）一并清理，不可恢复。'
      : '\n\n彻底删除后不可恢复。'
  await ElMessageBox.confirm(`确定彻底删除「${row.name}」吗？${cascadeNote}`, '彻底删除', {
    type: 'warning',
    confirmButtonText: '彻底删除',
    cancelButtonText: '取消'
  })
  await trashApi.purge(row.type as TrashType, row.id)
  ElMessage.success('已彻底删除')
  load()
}

async function cleanup() {
  await ElMessageBox.confirm(
      '将清理回收站中删除超过 30 天的所有数据（含关联数据），且不可恢复。是否继续？',
      '清理过期数据',
      {type: 'warning', confirmButtonText: '清理', cancelButtonText: '取消'}
  )
  const res = await trashApi.cleanup(30)
  ElMessage.success(`已清理 ${res.removed} 条超过 ${res.days} 天的数据`)
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container trash-page">
    <div class="trash-head">
      <div>
        <h2 class="head-title">回收站</h2>
        <p class="head-desc">误删的应用、知识库、模板与公告将在这里保留，可恢复或彻底删除</p>
      </div>
      <div class="head-actions">
        <el-button type="danger" plain :icon="Lightning" @click="cleanup">清理 30 天前</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon class="trash-tip">
      <template #title>
        删除智能体应用、知识库、应用模板或系统公告时仅移入回收站（应用/知识库会完整保留其关联数据，可一键恢复）。
        其余资源（发布渠道、定时任务等）仍为直接删除，删除前请确认。
      </template>
    </el-alert>

    <div class="filter-bar hover-card">
      <el-radio-group v-model="filterType" @change="load">
        <el-radio-button v-for="opt in typeOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}<span class="count">{{ countByType(opt.value) }}</span>
        </el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" placeholder="搜索资源名称" clearable style="width: 220px; margin-left: auto"/>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="filtered" empty-text="回收站空空如也，删除的上述资源会出现在这里">
        <el-table-column label="资源类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="typeMeta[row.type]?.tag" effect="light" round>
              {{ typeMeta[row.type]?.label || row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="260">
          <template #default="{ row }">
            <div class="item-name">{{ row.name || '（未命名）' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="删除时间" width="200">
          <template #default="{ row }">{{ fmt(row.deletedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <el-button link type="success" :icon="RefreshLeft" @click="restore(row)">恢复</el-button>
            <el-button link type="danger" :icon="Delete" @click="purge(row)">彻底删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="90" description="暂无回收站数据"/>
        </template>
      </el-table>
      <p v-if="filtered.length" class="trash-foot">
        <el-icon>
          <Warning/>
        </el-icon>
        回收站数据将在每次执行「清理 30 天前」时被批量彻底删除，请谨慎操作。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.trash-page {
  max-width: 1200px;
  margin: 0 auto;
}

.trash-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.head-title {
  font-size: 22px;
  font-weight: 700;
}

.head-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-tertiary);
}

.head-actions {
  display: flex;
  gap: 10px;
}

.trash-tip {
  margin-bottom: 14px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 16px;
}

.count {
  margin-left: 4px;
  font-size: 12px;
  opacity: 0.7;
}

.item-name {
  font-weight: 600;
}

.trash-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
