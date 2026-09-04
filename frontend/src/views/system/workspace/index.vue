<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, OfficeBuilding, Promotion } from '@element-plus/icons-vue'
import { workspaceApi, type Workspace } from '@/api/sys-workspace'
import { userApi } from '@/api/sys-user'
import type { SysUser } from '@/api/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const ws = ref<Workspace | null>(null)

const planText: Record<string, string> = { free: '免费版', pro: '专业版', enterprise: '企业版' }
const planType: Record<string, 'info' | 'warning' | 'danger'> = { free: 'info', pro: 'warning', enterprise: 'danger' }

async function load() {
  loading.value = true
  try {
    ws.value = await workspaceApi.getCurrent()
    await loadMembers()
  } finally {
    loading.value = false
  }
}

/* ---------- 成员列表（只读预览，管理入口在团队与权限） ---------- */
const members = ref<SysUser[]>([])
const membersTotal = ref(0)
async function loadMembers() {
  const data = await userApi.page({ page: 1, size: 100 })
  members.value = data.records
  membersTotal.value = data.total
}

/* ---------- 编辑空间 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ name: '', plan: 'free' })

function openEdit() {
  if (!ws.value) return
  form.name = ws.value.name
  form.plan = ws.value.plan
  dialogVisible.value = true
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('空间名称不能为空')
  saving.value = true
  try {
    ws.value = await workspaceApi.update({ name: form.name.trim(), plan: form.plan })
    ElMessage.success('工作空间已更新')
    dialogVisible.value = false
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

const currentUsername = computed(() => userStore.profile?.username || '')
function fmt(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 10)
}

onMounted(load)
</script>

<template>
  <div class="page-container ws-page">
    <div class="ws-head">
      <div>
        <h2 class="head-title">工作空间</h2>
        <p class="head-desc">当前空间「{{ ws?.name || '--' }}」的组织信息与资源概况</p>
      </div>
      <el-button type="primary" class="btn-gradient" :icon="EditPen" @click="openEdit">空间设置</el-button>
    </div>

    <div v-loading="loading" class="ws-body">
      <!-- 空间信息 -->
      <div class="ws-card hover-card">
        <div class="ws-card-title"><el-icon><OfficeBuilding /></el-icon> 空间信息</div>
        <div class="ws-info">
          <div class="ws-row">
            <span class="k">空间名称</span><span class="v">{{ ws?.name || '-' }}</span>
          </div>
          <div class="ws-row">
            <span class="k">空间编码</span>
            <span class="v"><code class="code">{{ ws?.code || '-' }}</code></span>
          </div>
          <div class="ws-row">
            <span class="k">当前套餐</span>
            <el-tag v-if="ws" :type="planType[ws.plan] || 'info'" effect="light">{{ planText[ws.plan] || ws.plan }}</el-tag>
          </div>
          <div class="ws-row">
            <span class="k">空间状态</span>
            <el-tag :type="ws?.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ ws?.status === 1 ? '运行正常' : '已停用' }}
            </el-tag>
          </div>
          <div class="ws-row">
            <span class="k">创建时间</span><span class="v">{{ fmt(ws?.createTime) }}</span>
          </div>
        </div>
      </div>

      <!-- 概览统计 -->
      <div class="stats-grid">
        <div class="stat-card hover-card">
          <div class="stat-icon" style="background: #eef0ff; color: #5b6cff"><el-icon><Promotion /></el-icon></div>
          <div>
            <div class="stat-num">{{ ws?.memberCount ?? 0 }}</div>
            <div class="stat-label">空间成员</div>
          </div>
        </div>
        <div class="stat-card hover-card">
          <div class="stat-icon" style="background: #e6f6fe; color: #0ea5e9"><el-icon><OfficeBuilding /></el-icon></div>
          <div>
            <div class="stat-num">{{ ws?.appCount ?? 0 }}</div>
            <div class="stat-label">智能体应用</div>
          </div>
        </div>
      </div>

      <!-- 成员 -->
      <div class="member-card">
        <div class="member-head">
          <span class="member-title">空间成员</span>
          <span class="member-tip">共 {{ membersTotal }} 人 · 完整管理入口在「团队与权限」</span>
        </div>
        <div class="member-list">
          <div v-for="m in members" :key="m.id" class="member-item hover-card">
            <div class="avatar">{{ (m.nickname || m.username || '?').slice(0, 1) }}</div>
            <div class="member-meta">
              <div class="member-name">
                {{ m.nickname || m.username }}
                <el-tag v-if="m.username === currentUsername" size="small" type="primary" effect="dark">我</el-tag>
                <el-tag v-else size="small" effect="plain" type="info">{{ m.username }}</el-tag>
              </div>
              <div class="member-email">{{ m.email || '未绑定邮箱' }}</div>
            </div>
            <el-tag size="small" :type="m.status === 1 ? 'success' : 'danger'" effect="light">
              {{ m.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </div>
          <el-empty v-if="members.length === 0" description="暂无成员" :image-size="70" />
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" title="工作空间设置" width="460px" :close-on-click-modal="false">
      <div class="dialog-body">
        <div class="field-group">
          <label>空间名称</label>
          <el-input v-model="form.name" maxlength="64" placeholder="空间展示名称" />
        </div>
        <div class="field-group">
          <label>当前套餐</label>
          <el-select v-model="form.plan" style="width: 100%">
            <el-option label="免费版" value="free" />
            <el-option label="专业版" value="pro" />
            <el-option label="企业版" value="enterprise" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="btn-gradient" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ws-page {
  max-width: 1280px;
  margin: 0 auto;
}
.ws-head {
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
.ws-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.ws-card,
.member-card {
  padding: 18px;
}
.ws-card {
  grid-column: 1 / -1;
  min-height: 260px;
}
.ws-card-title,
.member-title {
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
}
.ws-info {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ws-row {
  display: flex;
  align-items: center;
  font-size: 13.5px;
}
.ws-row .k {
  width: 110px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.ws-row .v {
  color: var(--text-primary);
}
.code {
  background: var(--hover-bg);
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12.5px;
}
.stats-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.stat-card {
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.stat-num {
  font-size: 24px;
  font-weight: 800;
}
.stat-label {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--text-tertiary);
}
.member-card {
  grid-column: 1 / -1;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--card-bg);
}
.member-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}
.member-tip {
  font-size: 12px;
  color: var(--text-tertiary);
}
.member-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
}
.member-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.member-meta {
  flex: 1;
  min-width: 0;
}
.member-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 13.5px;
}
.member-email {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-group label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-secondary);
}
</style>
