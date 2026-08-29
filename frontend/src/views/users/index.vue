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
  <div class="page-container users-page">
    <div class="users-head">
      <div>
        <h2 class="head-title">用户管理</h2>
        <p class="head-desc">共 {{ total }} 个用户 · 管理系统账号</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="create">创建用户</el-button>
    </div>

    <el-card shadow="never" class="users-card">
      <el-table v-loading="loading" :data="list">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar-ring">
                <el-avatar :size="30" class="user-avatar">{{ (row.nickname || row.username).charAt(0) }}</el-avatar>
              </div>
              <div>
                <div class="user-name">{{ row.nickname || row.username }}</div>
                <div class="user-username">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">
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

<style scoped>
.users-page {
  max-width: 1280px;
  margin: 0 auto;
}
.users-head {
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
.users-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-avatar-ring {
  padding: 2px;
  border-radius: 50%;
  background: var(--brand-gradient);
}
.user-avatar {
  background: var(--bg-card);
  color: var(--brand-1);
  font-size: 13px;
  font-weight: 600;
}
.user-name {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.2;
}
.user-username {
  font-size: 11.5px;
  color: var(--text-tertiary);
}
</style>
