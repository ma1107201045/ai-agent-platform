<script setup lang="ts">
import { computed, markRaw, onMounted, ref } from 'vue'
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
import { llmApi } from '@/api/llm'
import type { ChatModelInfo, WorkflowNodeType } from '@/api/types'
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
const selectedNodeId = ref<string | null>(null)
const chatModels = ref<ChatModelInfo[]>([])
const saving = ref(false)
const publishing = ref(false)
const dragType = ref<WorkflowNodeType | null>(null)

const selectedNode = computed(() =>
  nodes.value.find((n) => n.id === selectedNodeId.value) ?? null
)
const selectedData = computed(() => selectedNode.value?.data ?? null)
const isStartNode = computed(() => selectedData.value?.nodeType === 'start')

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

// ---------- 加载 ----------
async function loadApp() {
  const app = await appApi.get(appId)
  appName.value = app.name
  const { nodes: ns, edges: es } = dslToFlow(app.workflowJson)
  nodes.value = ns
  edges.value = es
}

async function loadModels() {
  chatModels.value = await llmApi.chatModels()
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
}

function onPaneClick() {
  selectedNodeId.value = null
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
    await appApi.update(appId, { workflowJson: flowToDsl(nodes.value, edges.value) })
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
    await appApi.publish(appId, { workflowJson: flowToDsl(nodes.value, edges.value), promptConfig: '' })
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
})
</script>

<template>
  <div class="editor">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button link @click="router.push('/apps')">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <span class="app-name">{{ appName }} <el-tag size="small" type="info">编排</el-tag></span>
      <div class="toolbar-actions">
        <el-button :icon="VideoPlay" type="primary" plain @click="goChat">对话调试</el-button>
        <el-button :icon="CopyDocument" :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button :icon="CircleCheck" type="success" :loading="publishing" @click="publish">发布</el-button>
        <el-button :icon="Delete" type="danger" plain :disabled="!selectedNodeId" @click="removeSelected">
          删除选中
        </el-button>
      </div>
    </div>

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
              <div class="node-head">
                <el-icon :size="15" :style="{ color: metaOf(data.nodeType).color }">
                  <component :is="iconOf(metaOf(data.nodeType).icon)" />
                </el-icon>
                <span class="node-title">{{ data.label }}</span>
              </div>
              <div class="node-desc">{{ metaOf(data.nodeType).desc }}</div>
              <Handle type="target" :position="Position.Left" />
              <Handle type="source" :position="Position.Right" />
            </div>
          </template>
          <Background :gap="20" pattern-color="#e5e7eb" />
          <Controls position="bottom-left" />
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

            <el-form-item v-if="selectedData.nodeType === 'http'" label="请求地址">
              <el-input v-model="selectedData.config.url" placeholder="https://api.example.com/path" />
            </el-form-item>
            <el-form-item v-if="selectedData.nodeType === 'http'" label="请求方式">
              <el-select v-model="selectedData.config.method" style="width: 100%">
                <el-option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'code'" label="代码片段">
              <el-input
                v-model="selectedData.config.code"
                type="textarea"
                :rows="8"
                placeholder="def main(input): return input"
              />
            </el-form-item>

            <el-form-item v-if="selectedData.nodeType === 'condition'" label="判断条件">
              <el-input
                v-model="selectedData.config.expression"
                placeholder="例如：{{'{{'}}input.length{{'}}'}} > 10"
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
  </div>
</template>

<style scoped>
.editor {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.toolbar {
  height: 50px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
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
.editor-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 节点面板 */
.node-palette {
  width: 200px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  padding: 12px;
  overflow-y: auto;
}
.node-palette h4,
.config-panel h4 {
  margin: 0 0 12px;
  font-size: 13px;
  color: #606266;
}
.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: grab;
  background: #fafafa;
  transition: all 0.2s;
}
.palette-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
}
.item-label {
  font-size: 13px;
  font-weight: 500;
}
.item-desc {
  font-size: 11px;
  color: #909399;
}
.palette-tip {
  font-size: 11px;
  color: #c0c4cc;
  text-align: center;
}

/* 画布 */
.canvas-wrap {
  flex: 1;
  min-width: 0;
  position: relative;
}
.flow {
  width: 100%;
  height: 100%;
}
.flow-node {
  min-width: 150px;
  padding: 8px 12px;
  background: #fff;
  border: 1.5px solid #dcdfe6;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.flow-node.selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.25);
}
.flow-node.llm {
  border-left: 4px solid #409eff;
}
.flow-node.start {
  border-left: 4px solid #67c23a;
}
.flow-node.end {
  border-left: 4px solid #f56c6c;
}
.flow-node.condition {
  border-left: 4px solid #e6a23c;
}
.flow-node.code {
  border-left: 4px solid #909399;
}
.flow-node.http {
  border-left: 4px solid #8e44ad;
}
.node-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.node-title {
  font-size: 13px;
  font-weight: 600;
}
.node-desc {
  margin-top: 4px;
  font-size: 11px;
  color: #909399;
}
.minimap {
  right: 12px;
}

/* 配置面板 */
.config-panel {
  width: 280px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  padding: 12px;
  overflow-y: auto;
}
</style>
