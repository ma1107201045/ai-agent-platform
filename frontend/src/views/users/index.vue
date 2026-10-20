<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/sys-user.ts'
import type { SysUser } from '@/api/types'

const loading = ref(false)
const list = ref<SysUser[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

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

// ---------- 创建 / 编辑 ----------

interface UserForm {
  id?: number
  username: string
  password: string
  nickname: string
  email: string
  role: string
  status: number
}

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<UserForm>({
  username: '',
  password: '',
  nickname: '',
  email: '',
  role: 'user',
  status: 1
})

const isEdit = ref(false)

const rules: FormRules<UserForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 64, message: '长度 2-64 个字符', trigger: 'blur' }
  ],
  password: [
    {
      validator: (_r, v: string, cb) => {
        if (!isEdit.value && !v) return cb(new Error('请输入初始密码'))
        if (v && v.length < 6) return cb(new Error('密码至少 6 位'))
        cb()
      },
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    username: '',
    password: '',
    nickname: '',
    email: '',
    role: 'user',
    status: 1
  })
  dialogVisible.value = true
}

function openEdit(row: SysUser) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    nickname: row.nickname ?? '',
    email: row.email ?? '',
    role: row.role ?? 'user',
    status: row.status
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value && form.id) {
      await userApi.update(form.id, {
        username: form.username,
        nickname: form.nickname,
        email: form.email,
        role: form.role,
        status: form.status,
        // 留空则不修改密码
        ...(form.password ? { password: form.password } : {})
      })
      ElMessage.success('已保存')
    } else {
      await userApi.create({
        username: form.username,
        password: form.password,
        nickname: form.nickname || form.username,
        email: form.email,
        role: form.role,
        status: form.status
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------- 启停 / 删除 ----------

const toggling = ref<Set<number>>(new Set())

async function toggleStatus(row: SysUser) {
  if (toggling.value.has(row.id)) return
  toggling.value.add(row.id)
  const target = row.status === 1 ? 0 : 1
  try {
    await userApi.update(row.id, { status: target })
    row.status = target
    ElMessage.success(target === 1 ? '已启用' : '已禁用')
  } catch {
    // 失败不改本地状态
  } finally {
    toggling.value.delete(row.id)
  }
}

async function remove(row: SysUser) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.username}」？该用户将无法登录，操作不可恢复。`,
      '删除用户',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'error' }
    )
  } catch {
    return // 用户取消
  }
  await userApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container users-page">
    <div class="users-head">
      <div>
        <h2 class="head-title">用户管理</h2>
        <p class="head-desc">共 {{ total }} 个用户 · 管理系统账号与角色</p>
      </div>
      <el-button type="primary" class="btn-gradient" @click="openCreate">创建用户</el-button>
    </div>

    <el-card shadow="never" class="users-card">
      <div class="users-toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名 / 昵称 / 邮箱"
          clearable
          class="toolbar-search"
        />
      </div>
      <el-table
        v-loading="loading"
        :data="
          list.filter(
            (u) =>
              !keyword ||
              u.username.includes(keyword) ||
              (u.nickname ?? '').includes(keyword) ||
              (u.email ?? '').includes(keyword)
          )
        "
      >
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar-ring">
                <el-avatar :size="30" class="user-avatar">{{
                  (row.nickname || row.username).charAt(0)
                }}</el-avatar>
              </div>
              <div>
                <div class="user-name">{{ row.nickname || row.username }}</div>
                <div class="user-username">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role === 'admin' ? 'warning' : 'info'" effect="light">
              {{ row.role === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="toggling.has(row.id)"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="toggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '创建用户'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录账号，2-64 位" />
        </el-form-item>
        <el-form-item :label="isEdit ? '重置密码' : '初始密码'" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则不修改密码' : '至少 6 位'"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="展示名称，默认同用户名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio value="user">普通用户</el-radio>
            <el-radio value="admin">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
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
.users-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.toolbar-search {
  width: 260px;
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
