<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Plus, Search } from '@element-plus/icons-vue'
import { appPromptApi } from '@/api/app-prompt'
import type { PromptTemplate } from '@/api/types'
import { categoryOptions } from './promptMeta'
import PromptCard from './components/PromptCard.vue'
import PromptEditDialog from './components/PromptEditDialog.vue'
import PromptVersionsDrawer from './components/PromptVersionsDrawer.vue'
import PromptDebugDialog from './components/PromptDebugDialog.vue'

const loading = ref(false)
const list = ref<PromptTemplate[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const categoryFilter = ref('')

async function load() {
  loading.value = true
  try {
    const data = await appPromptApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      category: categoryFilter.value || undefined
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

/* ---------------- 删除 / 启停用 ---------------- */
function remove(row: PromptTemplate) {
  ElMessageBox.confirm(`确认删除「${row.name}」？历史版本将一并清除，不可恢复。`, '删除确认', {
    type: 'error'
  })
    .then(async () => {
      await appPromptApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

async function toggleStatus(row: PromptTemplate) {
  const next = row.status === 1 ? 0 : 1
  await appPromptApi.update(row.id, { ...row, status: next })
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  load()
}

/* ---------------- 子组件引用 ---------------- */
const editDialogRef = ref<InstanceType<typeof PromptEditDialog> | null>(null)
const versionsDrawerRef = ref<InstanceType<typeof PromptVersionsDrawer> | null>(null)
const debugDialogRef = ref<InstanceType<typeof PromptDebugDialog> | null>(null)

onMounted(load)
</script>

<template>
  <div class="page-container prompts-page">
    <div class="prompts-head">
      <div>
        <h2 class="head-title">提示词库</h2>
        <p class="head-desc">共 {{ total }} 个模板 · 复用提示词模板，版本留痕与在线调试</p>
      </div>
      <el-button
        type="primary"
        class="btn-gradient create-btn"
        @click="editDialogRef?.openCreate()"
      >
        <el-icon><Plus /></el-icon>&nbsp;新建模板
      </el-button>
    </div>

    <!-- 筛选工具栏 -->
    <div class="prompts-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索模板名称 / 描述"
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
        v-model="categoryFilter"
        placeholder="全部分类"
        clearable
        class="toolbar-category"
        @change="search"
      >
        <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
    </div>

    <!-- 模板卡片 -->
    <div v-loading="loading" class="prompt-grid">
      <PromptCard
        v-for="row in list"
        :key="row.id"
        :row="row"
        @edit="editDialogRef?.openEdit($event)"
        @versions="versionsDrawerRef?.open($event)"
        @debug="debugDialogRef?.open($event)"
        @toggle="toggleStatus"
        @remove="remove"
      />

      <!-- 空状态 -->
      <div v-if="!loading && list.length === 0" class="empty-state">
        <div class="empty-icon">
          <el-icon :size="40"><MagicStick /></el-icon>
        </div>
        <p>
          {{
            keyword || categoryFilter
              ? '没有匹配的模板，换个条件试试'
              : '还没有提示词模板，创建第一个吧'
          }}
        </p>
        <el-button type="primary" class="btn-gradient" @click="editDialogRef?.openCreate()"
          >立即创建</el-button
        >
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > size" class="prompts-pagination">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>

    <PromptEditDialog ref="editDialogRef" @saved="load" />
    <PromptVersionsDrawer ref="versionsDrawerRef" @changed="load" />
    <PromptDebugDialog ref="debugDialogRef" />
  </div>
</template>

<style scoped>
.prompts-page {
  max-width: 1280px;
  margin: 0 auto;
}
.prompts-head {
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

/* ---------- 工具栏 ---------- */
.prompts-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}
.toolbar-search {
  width: 280px;
}
.toolbar-category {
  width: 160px;
}

/* ---------- 卡片 ---------- */
.prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(288px, 1fr));
  gap: 16px;
  min-height: 200px;
}

/* ---------- 空状态 / 分页 ---------- */
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
.prompts-pagination {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}
</style>
