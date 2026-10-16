<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { DEFAULT_ADVANCED, NODE_TYPE_META } from '@/utils/flow'
import type { VarItem } from '@/utils/flow'
import { fillSchemaDefaults, schemaIssues, schemaOf } from '@/utils/nodeSchema'
import type {
  AppAgentTool,
  ChatModelInfo,
  KnowledgeDataset,
  PromptTemplate,
  Skill,
  WorkflowNodeType
} from '@/api/types'
import StartNodeForm from './node-config/StartNodeForm.vue'
import EndNodeForm from './node-config/EndNodeForm.vue'
import LlmNodeForm from './node-config/LlmNodeForm.vue'
import AgentNodeForm from './node-config/AgentNodeForm.vue'
import SkillNodeForm from './node-config/SkillNodeForm.vue'
import ConditionNodeForm from './node-config/ConditionNodeForm.vue'
import CodeNodeForm from './node-config/CodeNodeForm.vue'
import HttpNodeForm from './node-config/HttpNodeForm.vue'
import TemplateNodeForm from './node-config/TemplateNodeForm.vue'
import KnowledgeNodeForm from './node-config/KnowledgeNodeForm.vue'
import ExecutionPolicyForm from './node-config/ExecutionPolicyForm.vue'
import PromptPickerDialog from './node-config/PromptPickerDialog.vue'

/** 节点配置面板：按节点类型渲染差异化配置 + 通用执行策略 */
const props = defineProps<{
  /** Vue Flow 节点对象（直接修改其 data 完成编辑） */
  node: any
  /** 画布连线数组（可写：分支改名/删除时同步连线） */
  edges: any[]
  chatModels: ChatModelInfo[]
  rerankModels: ChatModelInfo[]
  datasets: KnowledgeDataset[]
  tools: AppAgentTool[]
  /** 可执行技能（技能节点选择） */
  skills: Skill[]
  /** 当前节点可引用的变量 */
  vars: VarItem[]
}>()

const type = computed<WorkflowNodeType>(() => props.node?.data?.nodeType)
const meta = computed(() => NODE_TYPE_META[type.value])

// ---------- 配置对象（与 node.data.config 双向绑定） ----------
const cfg = ref<Record<string, any>>({})

/** Schema 必填校验（与后端 NodeExecutionPolicy 同规则；字段与校验逻辑由后端单源声明） */
const schemaErrors = computed<string[]>(() =>
  type.value ? schemaIssues(cfg.value, type.value) : []
)

watch(
  () => props.node?.data,
  (data) => {
    if (!data) return
    if (!data.config || typeof data.config !== 'object') data.config = {}
    cfg.value = data.config
    // 与引擎一致：schema 默认值仅补齐缺失键（后端新增配置键时，老 DSL 打开即自动对齐）
    fillSchemaDefaults(cfg.value, data.nodeType)
    // 补齐执行策略默认值（起止节点无执行策略）
    if (data.nodeType !== 'start' && data.nodeType !== 'end') {
      for (const [k, v] of Object.entries(DEFAULT_ADVANCED)) {
        if (cfg.value[k] === undefined || cfg.value[k] === null || cfg.value[k] === '') {
          cfg.value[k] = v
        }
      }
    }
  },
  { immediate: true }
)

watch(
  cfg,
  (v) => {
    if (props.node?.data) props.node.data.config = v
  },
  { deep: true, immediate: true }
)

// Schema 异步加载完成后，为当前节点补齐缺失的默认键
watch(
  () => type.value && schemaOf(type.value),
  (schema) => {
    if (schema && cfg.value && type.value) {
      fillSchemaDefaults(cfg.value, type.value)
    }
  },
  { immediate: true }
)

// ---------- 从提示词库选择模板 ----------
const promptPickerRef = ref<InstanceType<typeof PromptPickerDialog> | null>(null)

function openPromptPicker() {
  promptPickerRef.value?.open()
}

/** 应用选中模板到当前节点的系统提示词 */
function applyPromptTemplate(t: PromptTemplate) {
  cfg.value.systemPrompt = t.content
  ElMessage.success(`已应用「${t.name}」；正文中的变量占位可点击下方变量按需替换`)
}
</script>

<template>
  <div v-if="node?.data" class="node-config">
    <div class="config-node-head">
      <div class="node-icon" :style="{ background: meta.gradient }">
        <span class="node-icon-text">{{ meta.label.slice(0, 1) }}</span>
      </div>
      <div class="config-node-head-text">
        <div class="config-node-type">{{ meta.label }} · {{ meta.desc }}</div>
        <div class="config-node-name">{{ node.data.label }}</div>
      </div>
    </div>

    <el-form label-position="top" size="small">
      <!-- Schema 必填校验提示：缺失的必填配置项将按后端声明高亮 -->
      <el-alert
        v-if="schemaErrors.length"
        :title="`缺少必填配置：${schemaErrors.join('、')}`"
        type="error"
        show-icon
        :closable="false"
        class="schema-alert"
      />
      <el-form-item label="节点名称">
        <el-input v-model="node.data.label" />
      </el-form-item>

      <StartNodeForm v-if="type === 'start'" :cfg="cfg" />
      <EndNodeForm v-else-if="type === 'end'" :cfg="cfg" :vars="vars" />
      <LlmNodeForm
        v-else-if="type === 'llm'"
        :cfg="cfg"
        :vars="vars"
        :chat-models="chatModels"
        :rerank-models="rerankModels"
        :datasets="datasets"
        @pick-template="openPromptPicker"
      />
      <AgentNodeForm
        v-else-if="type === 'agent'"
        :cfg="cfg"
        :vars="vars"
        :chat-models="chatModels"
        :tools="tools"
        :datasets="datasets"
        @pick-template="openPromptPicker"
      />
      <SkillNodeForm
        v-else-if="type === 'skill'"
        :cfg="cfg"
        :vars="vars"
        :chat-models="chatModels"
        :skills="skills"
      />
      <ConditionNodeForm
        v-else-if="type === 'condition'"
        :cfg="cfg"
        :vars="vars"
        :node="node"
        :edges="edges"
      />
      <CodeNodeForm v-else-if="type === 'code'" :cfg="cfg" :vars="vars" />
      <HttpNodeForm v-else-if="type === 'http'" :cfg="cfg" :vars="vars" />
      <TemplateNodeForm v-else-if="type === 'template'" :cfg="cfg" :vars="vars" />
      <KnowledgeNodeForm
        v-else-if="type === 'knowledge'"
        :cfg="cfg"
        :vars="vars"
        :rerank-models="rerankModels"
        :datasets="datasets"
      />

      <!-- ---------- 执行策略（通用，起止节点除外） ---------- -->
      <ExecutionPolicyForm v-if="type !== 'start' && type !== 'end'" :cfg="cfg" :vars="vars" />

      <el-form-item label="节点备注">
        <el-input
          v-model="node.data.remark"
          type="textarea"
          :rows="2"
          placeholder="记录该节点的用途、注意事项（仅编辑端展示，不影响执行）"
        />
      </el-form-item>
    </el-form>

    <!-- 从提示词库选择模板 -->
    <PromptPickerDialog ref="promptPickerRef" @apply="applyPromptTemplate" />
  </div>
</template>

<style scoped>
.schema-alert {
  margin-bottom: 12px;
}
.config-node-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 14px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--border-color);
  border-radius: 10px;
}
.node-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  box-shadow:
    inset 0 -2px 4px rgba(0, 0, 0, 0.1),
    0 1px 3px rgba(0, 0, 0, 0.12);
}
.node-icon-text {
  font-size: 14px;
  font-weight: 700;
}
.config-node-head-text {
  min-width: 0;
}
.config-node-type {
  font-size: 11px;
  color: var(--text-tertiary);
}
.config-node-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 表单通用样式：作用于各节点类型子组件内的 el-form-item / el-divider */
:deep(.el-form-item) {
  margin-bottom: 14px;
}
:deep(.el-form-item__label) {
  font-size: 12.5px;
  color: var(--text-secondary);
  font-weight: 500;
}
:deep(.el-divider__text) {
  font-size: 12px;
  color: var(--brand-1);
}
</style>
