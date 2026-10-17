<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, UploadFilled } from '@element-plus/icons-vue'
import { appSkillApi } from '@/api/app-skill'
import { appAgentToolApi } from '@/api/app-tool-info.ts'
import { knowledgeApi } from '@/api/knowledge'
import type { Skill } from '@/api/types'
import { categoryOptions, typeMeta } from './skillMeta'
import SkillCard from './components/SkillCard.vue'
import SkillEditDialog from './components/SkillEditDialog.vue'
import SkillVersionsDrawer from './components/SkillVersionsDrawer.vue'

const loading = ref(false)
const list = ref<Skill[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const categoryFilter = ref('')
const typeFilter = ref('')

/* ---------------- 工具 / 数据集名称映射（卡片标签展示与表单下拉） ---------------- */
const toolOptions = ref<{ id: number; name: string }[]>([])
const datasetOptions = ref<{ id: number; name: string }[]>([])

async function loadMaps() {
  try {
    toolOptions.value = (await appAgentToolApi.enabled())
      .filter((t) => t.status === 1)
      .map((t) => ({ id: t.id, name: t.name }))
  } catch {
    toolOptions.value = []
  }
  try {
    const pageData = await knowledgeApi.datasetPage({ page: 1, size: 500 })
    datasetOptions.value = pageData.records
      .filter((d) => d.status === 1)
      .map((d) => ({ id: d.id, name: d.name }))
  } catch {
    datasetOptions.value = []
  }
}

/* ---------------- 列表 ---------------- */
async function load() {
  loading.value = true
  try {
    const data = await appSkillApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      category: categoryFilter.value || undefined,
      type: typeFilter.value || undefined
    })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 切换分类筛选并重查 */
function setCategoryFilter(v: string) {
  categoryFilter.value = v
  search()
}

function search() {
  page.value = 1
  load()
}

/* ---------------- 删除 / 启停用 ---------------- */
async function remove(row: Skill) {
  const refs = (await appSkillApi.apps(row.id).catch(() => [])) as { id: number; name: string }[]
  if (refs.length) {
    const names = refs
      .slice(0, 5)
      .map((a) => `「${a.name}」`)
      .join('、')
    ElMessage.warning(
      `技能已被 ${refs.length} 个应用引用（${names}${refs.length > 5 ? ' 等' : ''}），请先解除绑定`
    )
    return
  }
  ElMessageBox.confirm(`确认删除技能「${row.name}」？历史版本将一并清除，不可恢复。`, '删除确认', {
    type: 'error'
  })
    .then(async () => {
      await appSkillApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

async function toggleStatus(row: Skill) {
  const next = row.status === 1 ? 0 : 1
  await appSkillApi.update(row.id, { status: next })
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  load()
}

/* ---------------- 导出 / 导入 ---------------- */
async function exportSkill(row: Skill) {
  const data = await appSkillApi.exportSkill(row.id)
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `skill-${data.code || row.id}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('技能已导出')
}

const importInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
function pickImportFile() {
  importInput.value?.click()
}
async function onImportFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  const instance = ElLoading.service({ text: '正在解析并导入…', lock: true })
  try {
    const text = await file.text()
    const data = JSON.parse(text) as Partial<Skill>
    if (!data.name || !data.content)
      throw new Error('文件缺少 name / content 字段，不是有效的技能 JSON')
    const skill = await appSkillApi.importSkill(data)
    ElMessage.success(`技能「${skill.name}」导入成功，当前版本 v${skill.version}`)
    load()
  } catch (err) {
    ElMessage.error((err as Error).message || '导入失败，请检查文件内容')
  } finally {
    importing.value = false
    instance.close()
  }
}

/* ---------------- 弹窗引用 ---------------- */
const editDialog = ref<InstanceType<typeof SkillEditDialog> | null>(null)
const versionsDrawer = ref<InstanceType<typeof SkillVersionsDrawer> | null>(null)

onMounted(() => {
  load()
  loadMaps()
})
</script>

<template>
  <div class="page-container skill-page">
    <div class="skill-head">
      <div>
        <h2 class="head-title">技能</h2>
        <p class="head-desc">
          将指令、变量与工具/数据集封装为可复用能力包，绑定到应用后自动注入运行循环
        </p>
      </div>
      <div class="head-actions">
        <input
          ref="importInput"
          type="file"
          accept=".json,application/json"
          class="hidden-file"
          @change="onImportFile"
        />
        <el-button :icon="UploadFilled" :loading="importing" @click="pickImportFile"
          >导入</el-button
        >
        <el-button
          type="primary"
          class="btn-gradient"
          :icon="Plus"
          @click="editDialog?.openCreate()"
          >新建技能</el-button
        >
      </div>
    </div>

    <div class="skill-toolbar hover-card">
      <div class="filter-chips">
        <button
          class="chip"
          :class="{ active: categoryFilter === '' }"
          @click="setCategoryFilter('')"
        >
          全部
        </button>
        <button
          v-for="c in categoryOptions"
          :key="c.value"
          class="chip"
          :class="{ active: categoryFilter === c.value }"
          @click="setCategoryFilter(c.value)"
        >
          {{ c.label }}
        </button>
      </div>
      <div class="toolbar-right">
        <el-select
          v-model="typeFilter"
          placeholder="类型"
          clearable
          style="width: 130px"
          @change="search"
        >
          <el-option v-for="(m, key) in typeMeta" :key="key" :label="m.label" :value="key" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索名称 / 标识 / 描述"
          clearable
          style="width: 230px"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <el-button @click="search">搜索</el-button>
      </div>
    </div>

    <div v-loading="loading" class="skill-grid">
      <SkillCard
        v-for="row in list"
        :key="row.id"
        :row="row"
        :tool-options="toolOptions"
        :dataset-options="datasetOptions"
        @edit="editDialog?.openEdit(row)"
        @remove="remove"
        @versions="versionsDrawer?.open(row)"
        @export="exportSkill"
        @toggle="toggleStatus"
      />
    </div>

    <div v-if="!loading && list.length === 0" class="empty-card hover-card">
      <el-empty description="暂无匹配的技能，点击右上角新建技能" :image-size="90">
        <el-button
          type="primary"
          class="btn-gradient"
          :icon="Plus"
          @click="editDialog?.openCreate()"
          >新建技能</el-button
        >
      </el-empty>
    </div>

    <el-pagination
      v-if="total > size"
      v-model:current-page="page"
      style="margin-top: 16px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      @current-change="load"
    />

    <SkillEditDialog
      ref="editDialog"
      :tool-options="toolOptions"
      :dataset-options="datasetOptions"
      :ensure-maps="loadMaps"
      @saved="load"
    />
    <SkillVersionsDrawer ref="versionsDrawer" @rolled-back="load" />
  </div>
</template>

<style scoped>
.skill-page {
  max-width: 1440px;
  margin: 0 auto;
}
.skill-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
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
  align-items: center;
  gap: 10px;
}
.hidden-file {
  display: none;
}

.skill-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 18px;
}
.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1 1 auto;
  min-width: 0;
}
.chip {
  border: none;
  padding: 5px 12px;
  border-radius: 16px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition:
    background-color 0.15s,
    color 0.15s;
}
.chip:hover {
  background: var(--hover-bg);
  color: var(--text-primary);
}
.chip.active {
  background: var(--brand-1);
  color: #fff;
  font-weight: 600;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.empty-card {
  padding: 30px;
  display: flex;
  justify-content: center;
}
</style>
