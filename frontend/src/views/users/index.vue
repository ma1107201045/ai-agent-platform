<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api/user'
import type { SysUser } from '@/api/types'

const loading = ref(false)
const list = ref<SysUser[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

async function load() {
  loading.value = true
  try {
    const data = await userApi.page({ page: page.value, size: size.value })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function create() {
  ElMessageBox.prompt('请输入用户名', '创建用户', {
    confirmButtonText: '创建',
    inputPattern: /\S+/,
    inputErrorMessage: '用户名不能为空'
  })
    .then(async ({ value }) => {
      await userApi.create({ username: value, nickname: value, status: 1 })
      ElMessage.success('创建成功')
      load()
    })
    .catch(() => {})
}

function remove(row: SysUser) {
  ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '删除确认', {
    type: 'error'
  })
    .then(async () => {
      await userApi.remove(row.id)
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
      <span style="color: #909399">共 {{ total }} 个用户</span>
      <el-button type="primary" @click="create">创建用户</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
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
