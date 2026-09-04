<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { operLogApi, type OperLog } from '@/api/sys-oper-log'

const moduleOptions = [
  '认证', '应用管理', '智能体', '提示词库', '应用模板', '定时任务', '应用市场',
  '知识库', '工具', '模型', '发布管理', '评测', '素材管理', '记忆管理',
  '数据存储', '内容安全', '系统管理', '对话记录', '观测', '其他'
]

const loading = ref(false)
const list = ref<OperLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const moduleFilter = ref('')
const resultFilter = ref(-1)
const dateRange = ref<[string, string] | null>(null)

async function load() {
  loading.value = true
  try {
    const data = await operLogApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      module: moduleFilter.value || undefined,
      success: resultFilter.value === -1 ? undefined : resultFilter.value,
      startTime: dateRange.value?.[0],
      endTime: dateRange.value?.[1]
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
function reset() {
  keyword.value = ''
  moduleFilter.value = ''
  resultFilter.value = -1
  dateRange.value = null
  page.value = 1
  load()
}

function fmt(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 19)
}

function methodType(m?: string): 'success' | 'warning' | 'danger' | 'primary' {
  if (m === 'DELETE') return 'danger'
  if (m === 'PUT') return 'warning'
  if (m === 'POST') return 'primary'
  return 'success'
}

onMounted(load)
</script>

<template>
  <div class="page-container audit-page">
    <div class="audit-head">
      <div>
        <h2 class="head-title">操作日志</h2>
        <p class="head-desc">自动记录登录与关键管理操作，支持按模块、操作人与时间追溯</p>
      </div>
      <div class="stat-pills">
        <div class="pill"><span class="num">{{ total }}</span>条操作</div>
      </div>
    </div>

    <div class="filter-bar hover-card">
      <el-input v-model="keyword" placeholder="搜索操作人 / 模块 / 请求路径" clearable style="width: 260px"
        @keyup.enter="search" @clear="search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="moduleFilter" placeholder="所属模块" clearable style="width: 150px" @change="search">
        <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
      </el-select>
      <el-radio-group v-model="resultFilter" size="default" @change="search">
        <el-radio-button :value="-1">全部</el-radio-button>
        <el-radio-button :value="1">成功</el-radio-button>
        <el-radio-button :value="0">失败</el-radio-button>
      </el-radio-group>
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" @change="search" />
      <el-button type="primary" class="btn-gradient" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
    </div>

    <el-card shadow="never" class="audit-card">
      <el-table v-loading="loading" :data="list" size="default">
        <el-table-column label="操作时间" width="165">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="110">
          <template #default="{ row }">
            <span class="username">{{ row.username || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.module || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">{{ row.operation || '-' }}</template>
        </el-table-column>
        <el-table-column label="请求" min-width="230">
          <template #default="{ row }">
            <div class="req-cell">
              <el-tag size="small" :type="methodType(row.method)" effect="dark" class="method-tag">{{ row.method }}</el-tag>
              <span class="req-uri">{{ row.uri }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源IP" width="120">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ row.costMs != null ? `${row.costMs}ms` : '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.success !== 1" :content="row.errorMsg || '操作失败'" placement="top">
              <el-tag size="small" type="danger" effect="light">失败</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" type="success" effect="light">成功</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > size"
        style="margin-top: 16px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<style scoped>
.audit-page {
  max-width: 1400px;
  margin: 0 auto;
}
.audit-head {
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
.stat-pills {
  display: flex;
  gap: 10px;
}
.pill {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 12px;
  background: var(--brand-1);
  color: #fff;
  font-size: 13px;
}
.pill .num {
  font-size: 20px;
  font-weight: 700;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 16px;
}
.audit-card {
  border-radius: var(--radius-lg);
}
.username {
  font-weight: 600;
}
.req-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.method-tag {
  flex-shrink: 0;
}
.req-uri {
  font-size: 12.5px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
