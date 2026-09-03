<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Refresh, Search, View } from '@element-plus/icons-vue'
import { toolMarketplaceApi } from '@/api/tool-marketplace'
import type { ToolTemplate } from '@/api/types'

const loading = ref(false)
const list = ref<ToolTemplate[]>([])
const query = reactive({ keyword: '', category: '' })

/** 分类筛选（全部 + 服务端分类） */
const categories = [
  { value: '', label: '全部' },
  { value: 'basic', label: '通用' },
  { value: 'text', label: '文本处理' },
  { value: 'web', label: '网络数据' }
]
const categoryLabels: Record<string, string> = { basic: '通用', text: '文本处理', web: '网络数据' }
const typeLabels: Record<string, string> = { http: 'HTTP 请求', code: '代码脚本' }

const installedCount = computed(() => list.value.filter((t) => t.installed).length)

async function load() {
  loading.value = true
  try {
    list.value = await toolMarketplaceApi.templates({
      keyword: query.keyword || undefined,
      category: query.category || undefined
    })
  } finally {
    loading.value = false
  }
}

function search() {
  load()
}

/* ---------------- 模板详情 ---------------- */

interface ParamField {
  name: string
  type?: string
  desc?: string
  required: boolean
}

function parseParams(tpl: ToolTemplate): ParamField[] {
  if (!tpl.parameters) return []
  try {
    const schema = JSON.parse(tpl.parameters)
    const props = schema?.properties || {}
    const required: string[] = schema?.required || []
    return Object.keys(props).map((name) => ({
      name,
      type: props[name]?.type || 'string',
      desc: props[name]?.description || '',
      required: required.includes(name)
    }))
  } catch {
    return []
  }
}

const detailVisible = ref(false)
const detailTpl = ref<ToolTemplate | null>(null)

function openDetail(tpl: ToolTemplate) {
  detailTpl.value = tpl
  detailVisible.value = true
}

/* ---------------- 一键安装 ---------------- */

const installing = ref<string | null>(null)

async function install(tpl: ToolTemplate) {
  try {
    await ElMessageBox.confirm(
      `确认安装「${tpl.name}」？安装后将作为真实工具加入工具管理，智能体对话/工作流中即可调用。`,
      '安装确认',
      { type: 'info', confirmButtonText: '安装', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  installing.value = tpl.key
  try {
    await toolMarketplaceApi.install(tpl.key)
    ElMessage.success(`「${tpl.name}」安装成功，已加入工具管理`)
    await load()
  } finally {
    installing.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container mp-page">
    <div class="mp-head">
      <div>
        <h2 class="head-title">插件市场</h2>
        <p class="head-desc">
          内置常用工具模板，一键安装即成为可被智能体调用的真实工具 ·
          当前展示 {{ list.length }} 个模板，已安装 {{ installedCount }} 个
        </p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 筛选工具栏 -->
    <div class="mp-toolbar">
      <div class="category-tabs">
        <el-radio-group v-model="query.category" @change="search">
          <el-radio-button v-for="c in categories" :key="c.value" :value="c.value">{{ c.label }}</el-radio-button>
        </el-radio-group>
      </div>
      <el-input
        v-model="query.keyword"
        placeholder="搜索模板名称 / 描述"
        clearable
        class="toolbar-search"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!list.length" description="没有符合条件的模板" />
      <div v-else class="tpl-grid">
        <div v-for="tpl in list" :key="tpl.key" class="tpl-card">
          <div class="tpl-head">
            <div class="tpl-title">
              <code class="mono-chip">{{ tpl.name }}</code>
              <el-tag size="small" :type="tpl.type === 'http' ? 'warning' : 'info'" effect="light">
                {{ typeLabels[tpl.type] }}
              </el-tag>
            </div>
            <span class="tpl-cat">{{ categoryLabels[tpl.category] || tpl.category }}</span>
          </div>

          <p class="tpl-desc">{{ tpl.description }}</p>

          <div v-if="tpl.type === 'http'" class="tpl-meta">
            <span class="meta-method">{{ tpl.method }}</span>
            <span class="meta-url mono-text">{{ tpl.url }}</span>
          </div>
          <div v-else class="tpl-meta">
            <span class="meta-label">参数</span>
            <span class="meta-params">
              <template v-if="parseParams(tpl).length">
                <code v-for="p in parseParams(tpl)" :key="p.name" class="param-chip">{{ p.name }}</code>
              </template>
              <span v-else class="dim-text">无参数</span>
            </span>
          </div>

          <div class="tpl-foot">
            <el-button link type="primary" :icon="View" size="small" @click="openDetail(tpl)">查看配置</el-button>
            <el-button
              v-if="tpl.installed"
              type="success"
              size="small"
              plain
              disabled
              class="installed-btn"
            >
              <el-icon><Download /></el-icon>&nbsp;已安装
            </el-button>
            <el-button
              v-else
              type="primary"
              size="small"
              class="btn-gradient"
              :icon="Download"
              :loading="installing === tpl.key"
              @click="install(tpl)"
            >
              一键安装
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 模板详情对话框 -->
    <el-dialog v-model="detailVisible" :title="`模板详情 - ${detailTpl?.name || ''}`" width="680px">
      <template v-if="detailTpl">
        <div class="detail-block">
          <div class="detail-label">说明</div>
          <p class="detail-text">{{ detailTpl.description }}</p>
        </div>

        <div class="detail-block">
          <div class="detail-label">基础信息</div>
          <div class="info-grid">
            <div class="info-item"><span class="info-key">类型</span>{{ typeLabels[detailTpl.type] }}</div>
            <div class="info-item"><span class="info-key">分类</span>{{ categoryLabels[detailTpl.category] }}</div>
            <div class="info-item" v-if="detailTpl.type === 'http'"><span class="info-key">方式</span>{{ detailTpl.method }}</div>
          </div>
        </div>

        <!-- HTTP 模板：地址 + 参数 -->
        <div v-if="detailTpl.type === 'http'">
          <div class="detail-block">
            <div class="detail-label">请求地址</div>
            <code class="code-inline">{{ detailTpl.url }}</code>
          </div>
          <div class="detail-block">
            <div class="detail-label">参数说明</div>
            <el-table v-if="parseParams(detailTpl).length" :data="parseParams(detailTpl)" size="small" border>
              <el-table-column prop="name" label="参数" width="140">
                <template #default="{ row }"><code>{{ row.name }}</code></template>
              </el-table-column>
              <el-table-column prop="type" label="类型" width="90" />
              <el-table-column prop="desc" label="说明" min-width="160" />
              <el-table-column label="必填" width="60" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.required" size="small" type="danger" effect="light">是</el-tag>
                  <el-tag v-else size="small" type="info" effect="plain">否</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <p v-else class="dim-text" style="margin: 0">该模板无需参数</p>
          </div>
        </div>

        <!-- 代码模板：脚本预览 -->
        <div v-else class="detail-block">
          <div class="detail-label">执行脚本（MVEL）</div>
          <pre class="code-pre">{{ detailTpl.code }}</pre>
        </div>

        <el-alert v-if="!detailTpl.installed" type="info" :closable="false" show-icon class="detail-tip">
          该模板尚未安装，安装后可前往「工具管理」查看或修改配置。
        </el-alert>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detailTpl && !detailTpl.installed"
          type="primary"
          class="btn-gradient"
          :icon="Download"
          :loading="installing === detailTpl.key"
          @click="detailVisible = false; install(detailTpl)"
        >
          一键安装
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mp-page {
  max-width: 1400px;
  margin: 0 auto;
}
.mp-head {
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
.mp-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.toolbar-search {
  width: 280px;
}
.tpl-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}
.tpl-card {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 16px 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
  background: var(--fill-lighter);
}
.tpl-card:hover {
  box-shadow: var(--shadow-card-hover);
  border-color: var(--brand-1);
}
.tpl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.tpl-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.mono-chip {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tpl-cat {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  padding: 1px 8px;
}
.tpl-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  min-height: 42px;
}
.tpl-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
}
.meta-method {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  color: #b45309;
  background: #fef3c7;
  border-radius: 4px;
  padding: 1px 6px;
}
.meta-url {
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-tertiary);
}
.meta-params {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}
.param-chip {
  font-size: 11px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  background: var(--fill-light);
  border-radius: 4px;
  padding: 1px 6px;
  color: var(--text-secondary);
}
.mono-text {
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.tpl-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--border-color);
  padding-top: 10px;
}
.installed-btn {
  color: var(--el-color-success);
}
/* 详情对话框 */
.detail-block {
  margin-bottom: 16px;
}
.detail-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.detail-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}
.info-grid {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}
.info-item {
  font-size: 13px;
  color: var(--text-primary);
}
.info-key {
  color: var(--text-tertiary);
  margin-right: 6px;
}
.code-inline {
  font-size: 12.5px;
  word-break: break-all;
  background: var(--fill-light);
  padding: 6px 10px;
  border-radius: 6px;
  display: block;
}
.code-pre {
  margin: 0;
  padding: 12px 14px;
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.7;
  background: var(--fill-lighter);
  border-radius: var(--radius-md);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
}
.detail-tip {
  margin-top: 8px;
}
.dim-text {
  color: var(--text-tertiary);
  font-size: 12px;
}
</style>
