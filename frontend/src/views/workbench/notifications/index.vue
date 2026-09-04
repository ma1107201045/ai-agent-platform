<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Check,
  Delete,
  Finished,
  InfoFilled,
  View,
  WarningFilled
} from '@element-plus/icons-vue'
import { notificationApi, type SysNotification } from '@/api/sys-notification'
import { useNotificationStore } from '@/stores/notification'

const notificationStore = useNotificationStore()

const loading = ref(false)
const list = ref<SysNotification[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(8)

/** -1 全部 / 0 未读 / 1 已读 */
const readFilter = ref<number>(-1)
const typeFilter = ref('')
/** 未读数与顶栏徽标共用同一个 store */
const unread = computed(() => notificationStore.unread)

const typeMeta: Record<string, { label: string; color: string; icon: unknown }> = {
  system: { label: '系统', color: 'info', icon: InfoFilled },
  announcement: { label: '公告', color: 'primary', icon: Bell },
  run: { label: '任务', color: 'success', icon: Finished },
  alert: { label: '告警', color: 'danger', icon: WarningFilled }
}

function typeInfo(t?: string) {
  return typeMeta[t || ''] || typeMeta.system
}

function fmt(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      type: typeFilter.value || undefined,
      read: readFilter.value === -1 ? undefined : readFilter.value
    }
    const data = await notificationApi.page(params)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
async function refreshUnread() {
  await notificationStore.refresh()
}
async function search() {
  page.value = 1
  await Promise.all([load(), refreshUnread()])
}

/* ---------- 交互 ---------- */
const detailVisible = ref(false)
const detail = ref<SysNotification | null>(null)

async function markRead(row: SysNotification) {
  if (row.read === 1) return
  await notificationApi.markRead(row.id).catch(() => {})
  row.read = 1
  row.readTime = new Date().toISOString()
  refreshUnread()
}

async function openDetail(row: SysNotification) {
  detail.value = row
  detailVisible.value = true
  if (row.read !== 1) {
    await markRead(row)
  }
}

function allRead() {
  notificationApi.readAll().then(() => {
    ElMessage.success('已全部标为已读')
    Promise.all([load(), refreshUnread()])
  })
}

function remove(row: SysNotification) {
  notificationApi.remove(row.id).then(() => {
    ElMessage.success('已删除')
    Promise.all([load(), refreshUnread()])
  })
}

onMounted(() => {
  load()
  refreshUnread()
})
</script>

<template>
  <div class="page-container notif-page">
    <div class="notif-head">
      <div>
        <h2 class="head-title">通知中心</h2>
        <p class="head-desc">系统消息、任务结果与平台公告，一处查看</p>
      </div>
      <div class="head-actions">
        <el-button :disabled="unread === 0" @click="allRead">
          <el-icon><Check /></el-icon>全部已读
        </el-button>
      </div>
    </div>

    <div class="notif-body">
      <div class="notif-aside">
        <div class="stat-card">
          <div class="stat-num">{{ unread }}</div>
          <div class="stat-label">未读消息</div>
          <div class="stat-tip">共 {{ total }} 条{{ readFilter === -1 ? '' : readFilter === 0 ? '未读' : '已读' }}</div>
        </div>
        <div class="filter-card">
          <div class="filter-title">消息类型</div>
          <div class="filter-item" :class="{ active: typeFilter === '' }" @click="typeFilter = ''; search()">
            全部类型
          </div>
          <div
            v-for="(meta, key) in typeMeta"
            :key="key"
            class="filter-item"
            :class="{ active: typeFilter === key }"
            @click="typeFilter = key; search()"
          >
            <span class="dot" :class="meta.color"></span>{{ meta.label }}
          </div>
          <div class="filter-divider"></div>
          <div class="filter-item" :class="{ active: readFilter === -1 }" @click="readFilter = -1; search()">
            全部消息
          </div>
          <div class="filter-item" :class="{ active: readFilter === 0 }" @click="readFilter = 0; search()">
            未读消息
          </div>
          <div class="filter-item" :class="{ active: readFilter === 1 }" @click="readFilter = 1; search()">
            已读消息
          </div>
        </div>
      </div>

      <div class="notif-main">
        <div v-loading="loading" class="notif-list">
          <div
            v-for="row in list"
            :key="row.id"
            class="notif-item"
            :class="{ unread: row.read !== 1 }"
            @click="openDetail(row)"
          >
            <div class="notif-icon" :class="typeInfo(row.type).color">
              <el-icon :size="17"><component :is="typeInfo(row.type).icon" /></el-icon>
            </div>
            <div class="notif-content">
              <div class="notif-top">
                <span class="notif-title">{{ row.title }}</span>
                <el-tag size="small" effect="plain" :type="(typeInfo(row.type).color as any)" class="notif-type">
                  {{ typeInfo(row.type).label }}
                </el-tag>
                <span v-if="row.read !== 1" class="unread-dot"></span>
              </div>
              <div class="notif-text">{{ row.content || row.title }}</div>
              <div class="notif-meta">
                <span>{{ fmt(row.createTime) }}</span>
                <span v-if="row.read === 1 && row.readTime" class="read-time">已读于 {{ fmt(row.readTime) }}</span>
              </div>
            </div>
            <div class="notif-actions" @click.stop>
              <el-button
                v-if="row.read !== 1"
                link
                type="primary"
                :icon="Check"
                @click="markRead(row)"
              >标为已读</el-button>
              <el-button link type="info" :icon="View" @click="openDetail(row)">查看</el-button>
              <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
            </div>
          </div>

          <div v-if="!loading && list.length === 0" class="empty-box">
            <el-empty :description="unread === 0 && readFilter !== -1 ? '暂无该状态消息' : '暂无通知消息'" :image-size="90" />
          </div>
        </div>

        <el-pagination
          v-if="total > size"
          style="margin-top: 16px; justify-content: flex-end"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          v-model:current-page="page"
          @current-change="load"
        />
      </div>
    </div>

    <!-- 消息详情 -->
    <el-dialog v-model="detailVisible" :title="detail?.title" width="560px">
      <div class="detail-head">
        <el-tag size="small" effect="plain" :type="(typeInfo(detail?.type).color as any)">
          {{ typeInfo(detail?.type).label }}
        </el-tag>
        <span class="dim-text">{{ fmt(detail?.createTime) }}</span>
      </div>
      <pre class="detail-content">{{ detail?.content || detail?.title }}</pre>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notif-page {
  max-width: 1200px;
  margin: 0 auto;
}
.notif-head {
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

.notif-body {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

/* ---------- 左侧统计与筛选 ---------- */
.notif-aside {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 8px;
}
.stat-card {
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-num {
  font-size: 30px;
  font-weight: 800;
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  line-height: 1.1;
}
.stat-label {
  font-size: 13px;
  font-weight: 600;
}
.stat-tip {
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.filter-card {
  padding: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
}
.filter-title {
  font-size: 12px;
  color: var(--text-tertiary);
  padding: 6px 10px 4px;
  letter-spacing: 0.3px;
}
.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
  user-select: none;
}
.filter-item:hover {
  background: var(--hover-bg);
  color: var(--text-primary);
}
.filter-item.active {
  background: rgba(91, 108, 255, 0.12);
  color: var(--brand-1);
  font-weight: 600;
}
.filter-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot.info { background: #909399; }
.dot.primary { background: var(--brand-1); }
.dot.success { background: #67c23a; }
.dot.danger { background: #f56c6c; }
.filter-divider {
  height: 1px;
  background: var(--border-color);
  margin: 6px 8px;
}

/* ---------- 消息列表 ---------- */
.notif-main {
  min-width: 0;
}
.notif-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.notif-item {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}
.notif-item:hover {
  border-color: rgba(91, 108, 255, 0.4);
  box-shadow: var(--shadow-card-hover);
}
.notif-item.unread {
  border-left: 3px solid var(--brand-1);
  background: linear-gradient(90deg, rgba(91, 108, 255, 0.06), transparent 60%), var(--bg-card);
}
.notif-icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}
.notif-icon.info { background: rgba(144, 147, 153, 0.15); color: #909399; }
.notif-icon.primary { background: rgba(91, 108, 255, 0.14); color: var(--brand-1); }
.notif-icon.success { background: rgba(103, 194, 58, 0.14); color: #67c23a; }
.notif-icon.danger { background: rgba(245, 108, 108, 0.14); color: #f56c6c; }

.notif-content {
  flex: 1;
  min-width: 0;
}
.notif-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.notif-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notif-item.unread .notif-title {
  font-weight: 700;
}
.notif-type {
  flex-shrink: 0;
}
.unread-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand-1);
  flex-shrink: 0;
}
.notif-text {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.notif-meta {
  margin-top: 6px;
  display: flex;
  gap: 14px;
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.notif-actions {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.18s;
}
.notif-item:hover .notif-actions {
  opacity: 1;
}
.notif-actions .el-button + .el-button {
  margin-left: 0;
}
.empty-box {
  padding: 30px 0;
  background: var(--bg-card);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-lg);
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.detail-content {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.9;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  max-height: 60vh;
  overflow-y: auto;
}
.dim-text {
  font-size: 12px;
  color: var(--text-tertiary);
}

@media (max-width: 900px) {
  .notif-body {
    grid-template-columns: 1fr;
  }
  .notif-aside {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
