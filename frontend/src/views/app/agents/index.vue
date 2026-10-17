<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Plus, Search } from '@element-plus/icons-vue'
import { appAgentApi } from '@/api/app-agent.ts'
import type { AppAgent } from '@/api/types'
import { typeMeta } from './agentMeta'
import AppCard from './components/AppCard.vue'
import CreateAppDialog from './components/CreateAppDialog.vue'

const router = useRouter()
const loading = ref(false)
const list = ref<AppAgent[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const typeFilter = ref('')

async function load() {
  loading.value = true
  try {
    const data = await appAgentApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      type: typeFilter.value || undefined
    })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function edit(row: AppAgent) {
  router.push(`/app/agents/${row.id}/edit`)
}

function chat(row: AppAgent) {
  router.push(`/app/agents/${row.id}/chat`)
}

function publish(row: AppAgent) {
  // chatflow / workflow 需要有已保存的草稿工作流才能发布；agent 直接发布空 DSL 由运行时按配置执行
  if (row.type !== 'agent' && !row.workflowJson) {
    ElMessageBox.confirm(
      '该应用还没有可发布的工作流，请先到编辑器编排并保存草稿。',
      '暂无可发布内容',
      {
        confirmButtonText: '去编排',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
      .then(() => edit(row))
      .catch(() => {})
    return
  }
  ElMessageBox.confirm(`发布「${row.name}」为新的线上版本？`, '发布确认', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await appAgentApi.publish(row.id, {
        workflowJson: row.workflowJson ?? JSON.stringify({ nodes: [], edges: [] }),
        promptConfig: ''
      })
      ElMessage.success('发布成功')
      load()
    })
    .catch(() => {})
}

function remove(row: AppAgent) {
  ElMessageBox.confirm(`确认删除「${row.name}」？该操作不可恢复。`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'error'
  })
    .then(async () => {
      await appAgentApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

const createDialog = ref<InstanceType<typeof CreateAppDialog> | null>(null)

onMounted(load)
</script>

<template>
  <div class="page-container apps-page">
    <div class="apps-head">
      <div>
        <h2 class="head-title">智能体应用</h2>
        <p class="head-desc">共 {{ total }} 个应用，点击卡片快速进入编排或对话</p>
      </div>
      <el-button type="primary" class="btn-gradient create-btn" @click="createDialog?.open()">
        <el-icon><Plus /></el-icon>&nbsp;新建应用
      </el-button>
    </div>

    <!-- 筛选工具栏 -->
    <div class="apps-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索应用名称"
        clearable
        class="toolbar-search"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-select
        v-model="typeFilter"
        placeholder="全部类型"
        clearable
        class="toolbar-type"
        @change="search"
      >
        <el-option v-for="(meta, key) in typeMeta" :key="key" :label="meta.label" :value="key" />
      </el-select>
    </div>

    <!-- 卡片网格 -->
    <div v-loading="loading" class="app-grid">
      <AppCard
        v-for="row in list"
        :key="row.id"
        :row="row"
        @edit="edit"
        @chat="chat"
        @publish="publish"
        @remove="remove"
      />

      <!-- 空状态 -->
      <div v-if="!loading && list.length === 0" class="empty-state">
        <div class="empty-icon">
          <el-icon :size="40"><MagicStick /></el-icon>
        </div>
        <p>
          {{
            keyword || typeFilter
              ? '没有匹配的应用，换个条件试试'
              : '还没有应用，创建你的第一个智能体吧'
          }}
        </p>
        <el-button type="primary" class="btn-gradient" @click="createDialog?.open()"
          >立即创建</el-button
        >
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > size" class="apps-pagination">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <CreateAppDialog ref="createDialog" @created="load" />
  </div>
</template>

<style scoped>
.apps-page {
  max-width: 1280px;
  margin: 0 auto;
}

.apps-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
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
.create-btn {
  height: 38px;
}

/* ---------- 筛选工具栏 ---------- */
.apps-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}
.toolbar-search {
  width: 260px;
}
.toolbar-type {
  width: 160px;
}

/* ---------- 分页 ---------- */
.apps-pagination {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}

/* ---------- 卡片网格 ---------- */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  min-height: 200px;
}

/* ---------- 空状态 ---------- */
.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
}
.empty-icon {
  width: 84px;
  height: 84px;
  border-radius: 24px;
  background: var(--brand-gradient-soft);
  color: var(--brand-1);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-state p {
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
