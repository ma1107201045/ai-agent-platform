<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { appApi } from '@/api/app'
import type { AgentApp } from '@/api/types'

const router = useRouter()
const loading = ref(false)
const list = ref<AgentApp[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const typeLabels: Record<string, string> = {
  chatflow: '对话流',
  workflow: '工作流',
  agent: '智能体'
}

async function load() {
  loading.value = true
  try {
    const data = await appApi.page({ page: page.value, size: size.value })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  ElMessageBox.prompt('请输入应用名称', '创建应用', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空'
  })
    .then(async ({ value }) => {
      await appApi.create({
        name: value,
        type: 'chatflow',
        description: ''
      })
      ElMessage.success('创建成功')
      load()
    })
    .catch(() => {})
}

function edit(row: AgentApp) {
  router.push(`/apps/${row.id}/edit`)
}

function publish(row: AgentApp) {
  ElMessageBox.confirm(`发布「${row.name}」为新的线上版本？`, '发布确认', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await appApi.publish(row.id, {
        workflowJson: JSON.stringify({ nodes: [], edges: [] }),
        promptConfig: ''
      })
      ElMessage.success('发布成功')
      load()
    })
    .catch(() => {})
}

function remove(row: AgentApp) {
  ElMessageBox.confirm(`确认删除「${row.name}」？该操作不可恢复。`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'error'
  })
    .then(async () => {
      await appApi.remove(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="table-toolbar">
      <span style="color: #909399">共 {{ total }} 个应用</span>
      <el-button type="primary" @click="openCreate">新建应用</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabels[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="edit(row)">编排</el-button>
            <el-button link type="success" @click="publish(row)">发布</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
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
