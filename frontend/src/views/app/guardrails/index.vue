<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, InfoFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { guardApi } from '@/api/guard'
import type { GuardAppBindVO, GuardRule } from '@/api/guard'
import { formatTime } from '@/utils/format'
import {
  DIRS,
  ACTIONS,
  META,
  riskInfo,
  dirLabel,
  dirTag,
  matchLabel,
  actionLabel,
  actionTag,
  actionDesc
} from './guardMeta'
import GuardRuleDialog from './components/GuardRuleDialog.vue'
import GuardTestDialog from './components/GuardTestDialog.vue'
import GuardBindDrawer from './components/GuardBindDrawer.vue'

// ---------------- 规则列表 ----------------

const tab = ref<'rules' | 'binds'>('rules')
const loading = ref(false)
const rows = ref<GuardRule[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const dirFilter = ref('')
const actionFilter = ref('')

async function load() {
  loading.value = true
  try {
    const data = await guardApi.rulePage({
      page: page.value,
      size: size.value,
      direction: dirFilter.value || undefined,
      action: actionFilter.value || undefined,
      keyword: keyword.value.trim() || undefined
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function onSearch() {
  page.value = 1
  load()
}

async function onEnableChange(row: GuardRule) {
  try {
    await guardApi.updateRule(row.id, { enabled: row.enabled })
  } catch {
    row.enabled = row.enabled === 1 ? 0 : 1
    ElMessage.error('更新失败')
  }
}

function removeRule(row: GuardRule) {
  ElMessageBox.confirm(`删除规则「${row.name}」？`, '删除确认', {
    type: 'error',
    confirmButtonText: '删除'
  })
    .then(async () => {
      await guardApi.removeRule(row.id)
      ElMessage.success('删除成功')
      load()
    })
    .catch(() => {})
}

// ---------------- 应用绑定列表 ----------------

const bindLoading = ref(false)
const bindRows = ref<GuardAppBindVO[]>([])
const allRules = ref<GuardRule[]>([])

async function loadAllRules() {
  try {
    const data = await guardApi.rulePage({ page: 1, size: 500 })
    allRules.value = data.records
  } catch {
    allRules.value = []
  }
}
async function loadBinds() {
  bindLoading.value = true
  try {
    bindRows.value = await guardApi.binds()
  } finally {
    bindLoading.value = false
  }
}

function unbind(row: GuardAppBindVO) {
  if (row.bindId == null) return
  ElMessageBox.confirm(`解除应用「${row.appName}」的内容安全绑定？`, '解绑确认', {
    type: 'warning'
  })
    .then(async () => {
      await guardApi.removeBind(row.appId)
      ElMessage.success('已解绑')
      loadBinds()
    })
    .catch(() => {})
}

function onTabChange(v: string | number) {
  if (v === 'binds') {
    loadBinds()
  }
}

// ---------------- 弹窗引用 ----------------
const ruleDialog = ref<InstanceType<typeof GuardRuleDialog> | null>(null)
const testDialog = ref<InstanceType<typeof GuardTestDialog> | null>(null)
const bindDrawerRef = ref<InstanceType<typeof GuardBindDrawer> | null>(null)

/** 规则保存后同时刷新列表与绑定抽屉的规则池 */
function onRuleSaved() {
  load()
  loadAllRules()
}

onMounted(() => {
  load()
  loadAllRules()
})
</script>

<template>
  <div class="page-container guard-page">
    <div class="guard-head">
      <div>
        <h2 class="head-title">内容安全</h2>
        <p class="head-desc">配置敏感词 / 正则 / 注入检测规则，在输入输出链路上守护应用合规安全</p>
      </div>
      <div class="head-actions" v-if="tab === 'rules'">
        <el-button @click="testDialog?.open()">
          <el-icon style="margin-right: 4px"><Aim /></el-icon>命中测试
        </el-button>
        <el-button type="primary" class="btn-gradient" @click="ruleDialog?.openCreate()">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>新建规则
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="guard-card">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 规则库 -->
        <el-tab-pane label="规则库" name="rules">
          <div class="toolbar">
            <el-input
              v-model="keyword"
              class="search-input"
              placeholder="搜索规则名称 / 说明 / 内容"
              clearable
              @keyup.enter="onSearch"
              @clear="onSearch"
            >
              <template #prefix
                ><el-icon><Search /></el-icon
              ></template>
            </el-input>
            <el-select
              v-model="dirFilter"
              placeholder="作用方向"
              clearable
              style="width: 130px"
              @change="onSearch"
            >
              <el-option v-for="d in DIRS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
            <el-select
              v-model="actionFilter"
              placeholder="处置动作"
              clearable
              style="width: 130px"
              @change="onSearch"
            >
              <el-option v-for="a in ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
            <el-tooltip content="刷新">
              <el-button circle @click="load"
                ><el-icon><Refresh /></el-icon
              ></el-button>
            </el-tooltip>
          </div>

          <el-table v-loading="loading" :data="rows">
            <el-table-column label="规则" min-width="200">
              <template #default="{ row }">
                <div class="rule-name">
                  {{ row.name }}
                  <span
                    class="risk-dot"
                    :style="{ background: riskInfo(row.riskLevel).color }"
                    :title="`${riskInfo(row.riskLevel).label}(${row.riskLevel})`"
                  ></span>
                </div>
                <div v-if="row.description" class="rule-desc" :title="row.description">
                  {{ row.description }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="作用方向" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="dirTag(row.direction)" size="small" effect="light">{{
                  dirLabel(row.direction)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="匹配方式" width="110" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="META.match[row.matchType]?.tag || 'info'"
                  size="small"
                  effect="plain"
                >
                  {{ matchLabel(row.matchType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处置动作" width="130">
              <template #default="{ row }">
                <el-tag :type="actionTag(row.action)" size="small" effect="light">{{
                  actionLabel(row.action)
                }}</el-tag>
                <div class="muted">{{ actionDesc(row.action) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="匹配内容" min-width="220">
              <template #default="{ row }">
                <span class="mono content" :title="row.ruleContent">{{ row.ruleContent }}</span>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="80" align="center">
              <template #default="{ row }">
                <span class="mono">{{ row.priority }}</span>
              </template>
            </el-table-column>
            <el-table-column label="命中" width="80" align="right">
              <template #default="{ row }">
                <span class="mono">{{ row.hitCount ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-switch
                  v-model="row.enabled"
                  :active-value="1"
                  :inactive-value="0"
                  @change="onEnableChange(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="140">
              <template #default="{ row }">
                <span class="muted">{{ formatTime(row.updateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right" align="right">
              <template #default="{ row }">
                <el-button link type="success" @click="testDialog?.open(row)">测试</el-button>
                <el-button link type="primary" @click="ruleDialog?.openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removeRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="page"
            class="pager"
            layout="total, prev, pager, next"
            :total="total"
            :page-size="size"
            @current-change="load"
          />
        </el-tab-pane>

        <!-- 应用绑定 -->
        <el-tab-pane :label="`应用绑定（${bindRows.length}）`" name="binds">
          <el-table v-loading="bindLoading" :data="bindRows">
            <el-table-column label="应用" min-width="200">
              <template #default="{ row }">
                <div class="app-name">{{ row.appName }}</div>
                <div class="muted">{{ row.appType }}</div>
              </template>
            </el-table-column>
            <el-table-column label="绑定状态" width="140" align="center">
              <template #default="{ row }">
                <el-tag
                  v-if="row.bindId != null"
                  :type="row.bindEnabled === 1 ? 'success' : 'info'"
                  size="small"
                  effect="light"
                >
                  {{ row.bindEnabled === 1 ? '已启用' : '已停用' }}
                </el-tag>
                <el-tag v-else type="info" size="small" effect="plain">未绑定</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="模式" width="120" align="center">
              <template #default="{ row }">
                <span v-if="row.bindMode" class="mono">
                  <el-tag :type="row.bindMode === 'enforce' ? 'danger' : 'warning'" size="small">
                    {{ row.bindMode === 'enforce' ? '强制拦截' : '仅记录' }}
                  </el-tag>
                </span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="绑定规则数" width="110" align="center">
              <template #default="{ row }">
                <span v-if="row.ruleCount != null" class="mono">{{ row.ruleCount }} 条</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="bindDrawerRef?.open(row)">配置</el-button>
                <el-button v-if="row.bindId != null" link type="danger" @click="unbind(row)"
                  >解绑</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <div class="bind-tip">
            <el-icon><InfoFilled /></el-icon>
            <span
              >绑定规则后，命中「拦截」规则的请求将被拒绝；命中「打码/替换」规则的内容将按规则处理。正式接入需在模型调用链路上启用护栏（当前为配置层）。</span
            >
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <GuardRuleDialog ref="ruleDialog" @saved="onRuleSaved" />
    <GuardTestDialog ref="testDialog" />
    <GuardBindDrawer ref="bindDrawerRef" :all-rules="allRules" @saved="loadBinds" />
  </div>
</template>

<style scoped>
.guard-page {
  max-width: 1400px;
  margin: 0 auto;
}
.guard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
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
  gap: 8px;
}
.guard-card {
  border-radius: var(--radius-lg);
  padding: 4px 12px 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.search-input {
  width: 240px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
.rule-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}
.rule-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.risk-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.content {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.app-name {
  font-weight: 600;
}
.bind-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--fill-lighter);
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
