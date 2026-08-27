<script setup lang="ts">
import { computed, markRaw, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, CircleCheck, CopyDocument, Cpu, Delete, Document,
  Link as LinkIcon, Promotion, Share, VideoPlay
} from '@element-plus/icons-vue'
import { VueFlow, useVueFlow, Handle, Position } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { appApi } from '@/api/app'
import { knowledgeApi } from '@/api/knowledge'
import { llmApi } from '@/api/llm'
import { toolApi } from '@/api/tool'
import type { AgentTool, ChatModelInfo, KnowledgeDataset, WorkflowNodeType } from '@/api/types'
import {
  NODE_TYPE_LIST, NODE_TYPE_META, dslToFlow, flowToDsl, genNodeId
} from '@/utils/flow'

const route = useRoute()
const router = useRouter()
const appId = Number(route.params.id)

// ---------- 画布 ----------
const nodes = ref<Array<any>>([])
const edges = ref<Array<any>>([])
const { screenToFlowCoordinate, addNodes, removeNodes, onConnect } = useVueFlow()

// ---------- 数据 ----------
const appName = ref('')
const appType = ref('chatflow')
const selectedNodeId = ref<string | null>(null)
const chatModels = ref<ChatModelInfo[]>([])
const datasets = ref<KnowledgeDataset[]>([])
const allTools = ref<AgentTool[]>([])
const boundToolIds = ref<number[]>([])
const boundDatasetIds = ref<number[]>([])
const welcomeMessage = ref('')
const openingQuestionsText = ref('')
const saving = ref(false)
const urlPlaceholder = 'https://api.example.com/path（支持 {{input}} 变量）'
const headersPlaceholder = 'JSON 格式，如 X-Api-Key: xxx，支持 {{input}} 变量'
const codePlaceholder = '例如：{{input.length}} > 10'
const publishing = ref(false)
const dragType = ref<WorkflowNodeType | null>(null)

const selectedNode = computed(() =>
  nodes.value.find((n) => n.id === selectedNodeId.value) ?? null
)
const selectedData = computed(() => selectedNode.value?.data ?? null)
const isStartNode = computed(() => selectedData.value?.nodeType === 'start')

/** HTTP 节点 Headers 文本（JSON 字符串 <-> config.headers 对象） */
const headersText = ref('')

function syncHeadersText() {
  const h = selectedData.value?.config?.headers
  headersText.value = h && typeof h === 'object' ? JSON.stringify(h, null, 2) : ''
}

function parseHeaders() {
  const t = headersText.value.trim()
  if (!t) {
    if (selectedData.value?.config) delete selectedData.value.config.headers
    return
  }
  try {
    selectedData.value.config.headers = JSON.parse(t)
  } catch {
    ElMessage.warning('Headers 必须是合法 JSON 对象')
  }
}

// 动态图标组件缓存
const iconMap: Record<string, any> = {}
function iconOf(name: string) {
  if (!iconMap[name]) {
    const map: Record<string, any> = {
      Promotion, CircleCheck, Cpu, Share, Document, Link: LinkIcon
    }
    iconMap[name] = markRaw(map[name] || Cpu)
  }
  return iconMap[name]
}

function metaOf(type: WorkflowNodeType) {
  return NODE_TYPE_META[type]
}

/** 节点类型色 + 透明度后缀（用于浅色底） */
function tintOf(type: WorkflowNodeType) {
  return metaOf(type).color + '1a'
}

// ---------- 加载 ----------
async function loadApp() {
  const app = await appApi.get(appId)
  appName.value = app.name
  appType.value = app.type || 'chatflow'
  if (app.toolIds) {
    try {
      boundToolIds.value = JSON.parse(app.toolIds) as number[]
    } catch {
      boundToolIds.value = []
    }
  }
  if (app.datasetIds) {
    try {
      boundDatasetIds.value = JSON.parse(app.datasetIds) as number[]
    } catch {
      boundDatasetIds.value = []
    }
  }
  welcomeMessage.value = app.welcomeMessage || ''
  if (app.openingQuestions) {
    try {
      openingQuestionsText.value = (JSON.parse(app.openingQuestions) as string[]).join('\n')
    } catch {
      openingQuestionsText.value = ''
    }
  }
  const { nodes: ns, edges: es } = dslToFlow(app.workflowJson)
  nodes.value = ns
  edges.value = es
}

async function loadModels() {
  chatModels.value = await llmApi.chatModels()
}

async function loadTools() {
  try {
    allTools.value = await toolApi.enabled()
  } catch {
    allTools.value = []
  }
}

async function loadDatasets() {
  try {
    datasets.value = (await knowledgeApi.datasetPage({ size: 100 })).records
  } catch {
    datasets.value = []
  }
}

// ---------- 节点操作 ----------
function onDragStart(type: WorkflowNodeType) {
  dragType.value = type
}

function onDrop(e: DragEvent) {
  if (!dragType.value) return
  const pos = screenToFlowCoordinate({ x: e.clientX, y: e.clientY })
  const type = dragType.value
  addNodes([
    {
      id: genNodeId(),
      type: 'flow-node',
      position: pos,
      data: {
        label: NODE_TYPE_META[type].label,
        nodeType: type,
        config: {}
      }
    }
  ])
  dragType.value = null
}

function onNodeClick({ node }: any) {
  selectedNodeId.value = node.id
  nextTick(syncHeadersText)
}

function onPaneClick() {
  selectedNodeId.value = null
  headersText.value = ''
}

onConnect((params: any) => {
  edges.value.push({
    id: genNodeId('edge'),
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    targetHandle: params.targetHandle
  })
})

function removeSelected() {
  if (!selectedNodeId.value) return
  const id = selectedNodeId.value
  removeNodes(id)
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  selectedNodeId.value = null
}

function handleKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement
  if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return
  if (e.key === 'Delete' || e.key === 'Backspace') {
    removeSelected()
  }
}

// ---------- 保存 / 发布 ----------
async function saveDraft() {
  saving.value = true
  try {
    if (appType.value === 'agent') {
      await appApi.update(appId, {
        toolIds: JSON.stringify(boundToolIds.value),
        datasetIds: JSON.stringify(boundDatasetIds.value),
        welcomeMessage: welcomeMessage.value,
        openingQuestions: JSON.stringify(
          openingQuestionsText.value.split('\n').map((q) => q.trim()).filter(Boolean)
        )
      })
    } else {
      await appApi.update(appId, { workflowJson: flowToDsl(nodes.value, edges.value) })
    }
    ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

async function publish() {
  await ElMessageBox.confirm('发布后将生成新版本快照并设为线上版本，确定发布？', '发布确认', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    type: 'warning'
  })
  publishing.value = true
  try {
    const workflowJson =
      appType.value === 'agent' ? JSON.stringify({ nodes: [], edges: [] }) : flowToDsl(nodes.value, edges.value)
    await appApi.publish(appId, { workflowJson, promptConfig: '' })
    ElMessage.success('发布成功')
  } finally {
    publishing.value = false
  }
}

function goChat() {
  router.push(`/apps/${appId}/chat`)
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  loadApp()
  loadModels()
  loadDatasets()
  loadTools()
})
</script>

<template>
  <div class="editor">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button link @click="router.push('/apps')">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <span class="app-name">
        {{ appName }}
        <el-tag size="small" :type="appType === 'agent' ? 'success' : 'info'">
          {{ appType === 'agent' ? '智能体' : '编排' }}
        </el-tag>
      </span>
      <div class="toolbar-actions">
        <el-button :icon="VideoPlay" type="primary" plain class="debug-btn" @click="goChat">对话调试</el-button>
        <el-button :icon="CopyDocument" :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button :icon="CircleCheck" class="btn-gradient" :loading="publishing" @click="publish">发布</el-button>
        <el-button :icon="Delete" type="danger" plain :disabled="!selectedNodeId" @click="removeSelected">
          删除选中
        </el-button>
      </div>
    </div>

    <!-- 智能体应用：工具/知识库绑定配置 -->
    <div v-if="appType === 'agent'" class="agent-config">
      <el-card shadow="never" class="agent-card">
        <template #header>
          <div class="agent-card-head">
            <span>绑定工具</span>
            <el-button link type="primary" @click="router.push('/tools')">前往工具管理</el-button>
          </div>
        </template>
        <p class="agent-tip">
          智能体会根据对话内容自主决定调用哪些工具。请从可用工具中选择本应用可以使用的工具。
        </p>
        <el-checkbox-group v-model="boundToolIds" class="tool-check-list">
          <el-checkbox v-for="t in allTools" :key="t.id" :value="t.id" class="tool-check">
            <div class="tool-check-body">
              <div class="tool-check-name">
                <code class="tool-code">{{ t.name }}</code>
                <el-tag size="small" :type="t.type === 'http' ? 'warning' : 'info'" effect="plain">
                  {{ t.type === 'http' ? 'HTTP' : '代码' }}
                </el-tag>
              </div>
              <div class="tool-check-desc">{{ t.description }}</div>
            </div>
          </el-checkbox>
        </el-checkbox-group>
        <el-empty
          v-if="allTools.length === 0"
          description="暂无可用工具，请先在「工具管理」中创建"
          :image-size="60"
        />
      </el-card>

      <el-card shadow="never" class="agent-card">
        <template #header>
          <div class="agent-card-head">
            <span>绑定知识库</span>
            <el-button link type="primary" @click="router.push('/knowledge')">前往知识库管理</el-button>
          </div>
        </template>
        <p class="agent-tip">
          智能体会基于用户提问检索绑定数据集中的内容，并将命中的资料作为上下文辅助回答（RAG）。
        </p>
        <el-checkbox-group v-model="boundDatasetIds" class="tool-check-list">
          <el-checkbox v-for="d in datasets" :key="d.id" :value="d.id" class="tool-check">
            <div class="tool-check-body">
              <div class="tool-check-name">
                <code class="tool-code">{{ d.name }}</code>
                <el-tag size="small" type="success" effect="plain">知识库</el-tag>
              </div>
              <div class="tool-check-desc">{{ d.description || '暂无描述' }}</div>
            </div>
          </el-checkbox>
        </el-checkbox-group>
        <el-empty
          v-if="datasets.length === 0"
          description="暂无数据集，请先在「知识库」中创建"
          :image-size="60"
        />
      </el-card>

      <el-card shadow="never" class="agent-card">
        <template #header>
          <div class="agent-card-head">
            <span>应用设置</span>
          </div>
        </template>
        <el-form label-position="top" size="small">
          <el-form-item label="开场白">
            <el-input
              v-model="welcomeMessage"
              type="textarea"
              :rows="3"
              placeholder="对话开始时的欢迎语，留空则使用默认文案"
            />
          </el-form-item>
          <el-form-item label="推荐问题">
            <el-input
              v-model="openingQuestionsText"
              type="textarea"
              :rows="4"
              placeholder="每行一个推荐问题，展示在聊天页欢迎区，点击即可提问"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <div class="agent-actions">
        <el-button :icon="CopyDocument" :loading="saving" @click="saveDraft">保存配置</el-button>
        <el-button :icon="CircleCheck" class="btn-gradient" :loading="publishing" @click="publish">发布</el-button>
      </div>
    </div>

    <template v-else>
    <div class="editor-body">
      <!-- 左侧节点面板 -->
      <aside class="node-palette">
        <h4>节点</h4>
        <div
          v-for="type in NODE_TYPE_LIST"
          :key="type"
          class="palette-item"
          draggable="true"
          @dragstart="onDragStart(type)"
          @dragend="dragType = null"
        >
          <el-icon :size="16" :style="{ color: metaOf(type).color }">
            <component :is="iconOf(metaOf(type).icon)" />
          </el-icon>
          <div>
            <div class="item-label">{{ metaOf(type).label }}</div>
            <div class="item-desc">{{ metaOf(type).desc }}</div>
          </div>
        </div>
        <p class="palette-tip">拖拽节点到画布创建</p>
      </aside>

      <!-- 中间画布 -->
      <div class="canvas-wrap" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-viewport="{ zoom: 0.85 }"
          :min-zoom="0.2"
          :max-zoom="2"
          class="flow"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
        >
          <template #node-flow-node="{ data, selected }">
            <div class="flow-node" :class="[data.nodeType, { selected }]">
              <div class="node-accent" :style="{ background: metaOf(data.nodeType).color }"></div>
              <div class="node-head">
                <div
                  class="node-icon"
                  :style="{ background: tintOf(data.nodeType), color: metaOf(data.nodeType).color }"
                >
                  <el-icon :size="15">
                    <component :is="iconOf(metaOf(data.nodeType).icon)" />
                  </el-icon>
                </div>
                <span class="node-title">{{ data.label }}</span>
              </div>
              <div class="node-desc">{{ metaOf(data.nodeType).desc }}</div>
              <Handle type="target" :position="Position.Left" class="node-handle"></Handle>
              <Handle type="source" :position="Position.Right" class="node-handle"></Handle>
            </div>
          </template>
          <Background :gap="24" pattern-color="#e6e9f5" :line-width="1" />
          <Controls position="bottom-left" class="flow-controls" />
          <MiniMap pannable zoomable class="minimap" />
        </VueFlow>
      </div>

      <!-- 右侧配置面板 -->
      <aside class="config-panel">
        <template v-if="selectedData">
          <h4>节点配置</h4>
          <el-form label-position="top" size="small">
            <el-form-item label="节点名称">
              <el-input v-model="selectedData.label" />
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'llm'" label="模型">
              <el-select v-model="selectedData.config.modelId" placeholder="选择对话模型" style="width: 100%">
                <el-option
                  v-for="m in chatModels"
                  :key="m.id"
                  :label="`${m.providerName} / ${m.modelName}`"
                  :value="m.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'llm'" label="系统提示词">
              <el-input
                v-model="selectedData.config.systemPrompt"
                type="textarea"
                :rows="5"
                placeholder="设定模型角色与行为…"
              />
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'llm'" label="温度">
              <el-slider v-model="selectedData.config.temperature" :min="0" :max="2" :step="0.1" />
            </el-form-item>

            <el-divider v-if="selectedData.nodeType === 'llm'" content-position="left">知识库检索</el-divider>
            <el-form-item v-if="selectedData.nodeType === 'llm'" label="关联数据集">
              <el-select
                v-model="selectedData.config.datasetId"
                placeholder="不使用知识库"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="d in datasets"
                  :key="d.id"
                  :label="d.name"
                  :value="d.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'llm' && selectedData.config.datasetId" label="召回数量">
              <el-input-number v-model="selectedData.config.topK" :min="1" :max="10" />
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'http'" label="请求地址">
              <el-input v-model="selectedData.config.url" :placeholder="urlPlaceholder" />
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="请求方式">
              <el-select v-model="selectedData.config.method" style="width: 100%">
                <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="自定义 Headers">
              <el-input
                v-model="headersText"
                type="textarea"
                :rows="3"
                :placeholder="headersPlaceholder"
                @blur="parseHeaders"
              />
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="鉴权方式">
              <el-select v-model="selectedData.config.authType" style="width: 100%">
                <el-option label="无" value="none" />
                <el-option label="Bearer Token" value="bearer" />
                <el-option label="Basic" value="basic" />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="selectedData.nodeType === 'http' && selectedData.config.authType === 'bearer'"
              label="Token"
            >
              <el-input v-model="selectedData.config.authToken" placeholder="Bearer Token" />
            </el-form-item>
            <template v-if="selectedData.nodeType === 'http' && selectedData.config.authType === 'basic'">
              <el-form-item label="用户名">
                <el-input v-model="selectedData.config.authUsername" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="selectedData.config.authPassword" type="password" show-password />
              </el-form-item>
            </template>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="失败重试次数">
              <el-input-number v-model="selectedData.config.retries" :min="0" :max="5" />
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'code'" label="代码脚本">
              <el-input
                v-model="selectedData.config.code"
                type="textarea"
                :rows="10"
                placeholder="MVEL 表达式脚本，可用 input 与各节点输出，最后 return 结果&#10;示例：&#10;return input.trim().toUpperCase()&#10;示例：&#10;return input.length() > 10 ? '长文本' : '短文本'"
              />
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'condition'" label="判断条件">
              <el-input
                v-model="selectedData.config.expression"
                :placeholder="codePlaceholder"
              />
            </el-form-item>

            <el-form-item v-if="isStartNode" label="开场白">
              <el-input
                v-model="selectedData.config.welcome"
                type="textarea"
                :rows="3"
                placeholder="对话开始时的欢迎语"
              />
            </el-form-item>
          </el-form>
        </template>
        <template v-else>
          <el-empty description="选中节点后在此配置" :image-size="80" />
        </template>
      </aside>
    </div>
    </template>
  </div>
</template>

<style scoped>
.editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}
.toolbar {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}
.app-name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.debug-btn {
  --el-color-primary: var(--brand-1);
}
.editor-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 节点面板 */
.node-palette {
  width: 210px;
  background: #fff;
  border-right: 1px solid var(--border-color);
  padding: 14px 12px;
  overflow-y: auto;
}
.node-palette h4,
.config-panel h4 {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 600;
}
.palette-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  cursor: grab;
  background: #fff;
  transition: all 0.2s ease;
  box-shadow: var(--shadow-card);
}
.palette-item:hover {
  border-color: var(--brand-1);
  background: var(--el-color-primary-light-9);
  transform: translateX(2px);
}
.palette-item:active {
  cursor: grabbing;
}
.item-label {
  font-size: 13px;
  font-weight: 600;
}
.item-desc {
  margin-top: 1px;
  font-size: 11px;
  color: var(--text-tertiary);
}
.palette-tip {
  margin-top: 12px;
  font-size: 11px;
  color: var(--text-tertiary);
  text-align: center;
  padding: 8px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
}

/* 画布 */
.canvas-wrap {
  flex: 1;
  min-width: 0;
  position: relative;
  background-image: radial-gradient(rgba(91, 108, 255, 0.06) 1px, transparent 1px);
  background-size: 22px 22px;
}
.flow {
  width: 100%;
  height: 100%;
}

/* 节点卡片 */
.flow-node {
  position: relative;
  min-width: 170px;
  padding: 12px 14px 10px 16px;
  background: #fff;
  border: 1.5px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
  transition: all 0.22s ease;
}
.node-accent {
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3.5px;
  border-radius: 3px;
  opacity: 0.9;
}
.flow-node:hover {
  border-color: var(--brand-3);
  box-shadow: var(--shadow-card-hover);
}
.flow-node.selected {
  border-color: var(--brand-1);
  box-shadow: 0 0 0 3px rgba(91, 108, 255, 0.18), var(--shadow-card-hover);
  transform: translateY(-1px);
}
.node-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.node-icon {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.node-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-desc {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-tertiary);
  padding-left: 34px;
}
.node-handle {
  width: 10px !important;
  height: 10px !important;
  background: #fff !important;
  border: 2px solid var(--brand-1) !important;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.flow-node:hover .node-handle {
  transform: scale(1.3);
}
.flow-controls {
  --vue-flow-controls-border-radius: 10px;
}
.minimap {
  right: 12px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

/* 配置面板 */
.config-panel {
  width: 290px;
  background: #fff;
  border-left: 1px solid var(--border-color);
  padding: 16px 14px;
  overflow-y: auto;
}
.config-panel :deep(.el-form-item) {
  margin-bottom: 14px;
}
.config-panel :deep(.el-form-item__label) {
  font-size: 12.5px;
  color: var(--text-secondary);
  font-weight: 500;
}
.config-panel :deep(.el-divider__text) {
  font-size: 12px;
  color: var(--brand-1);
}

/* ---------- 智能体工具绑定 ---------- */
.agent-config {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 28px;
  display: flex;
  justify-content: center;
  background-image: radial-gradient(rgba(139, 92, 246, 0.06) 1px, transparent 1px);
  background-size: 22px 22px;
}
.agent-card {
  width: 680px;
  height: fit-content;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}
.agent-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.agent-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--brand-gradient-soft);
  border-radius: 10px;
  padding: 10px 14px;
}
.tool-check-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tool-check {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  transition: all 0.2s ease;
  white-space: normal;
}
.tool-check:hover {
  border-color: var(--brand-3);
  background: #fafbfe;
}
.tool-check-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 4px;
}
.tool-check-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tool-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  color: #409eff;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
}
.tool-check-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.5;
}
.agent-actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
