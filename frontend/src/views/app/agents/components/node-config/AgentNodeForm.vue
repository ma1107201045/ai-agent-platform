<script setup lang="ts">
import VarChips from '../VarChips.vue'
import type { VarItem } from '@/utils/flow'
import type { AppAgentTool, ChatModelInfo, KnowledgeDataset } from '@/api/types'

/** Agent 节点：模型 / 提示词 / 工具与数据集 / 循环策略 */
defineProps<{
  cfg: Record<string, any>
  vars: VarItem[]
  chatModels: ChatModelInfo[]
  tools: AppAgentTool[]
  datasets: KnowledgeDataset[]
}>()

const emit = defineEmits<{ pickTemplate: [] }>()
</script>

<template>
  <el-form-item label="模型">
    <el-select v-model="cfg.modelId" placeholder="选择对话模型" style="width: 100%">
      <el-option
        v-for="m in chatModels"
        :key="m.id"
        :label="`${m.providerName} / ${m.modelName}`"
        :value="m.id"
      />
    </el-select>
  </el-form-item>
  <el-form-item>
    <template #label>
      <span class="sys-prompt-label">系统提示词</span>
      <el-button
        link
        type="primary"
        size="small"
        class="pick-template-btn"
        @click="emit('pickTemplate')"
      >
        从提示词库选择
      </el-button>
    </template>
    <el-input
      v-model="cfg.systemPrompt"
      type="textarea"
      :rows="4"
      placeholder="设定 Agent 角色与工具使用策略…"
    />
    <VarChips :vars="vars" :target="cfg" field="systemPrompt" />
  </el-form-item>
  <el-form-item label="用户提示词">
    <el-input v-model="cfg.userPrompt" type="textarea" :rows="3" placeholder="默认 {{input}}" />
    <VarChips :vars="vars" :target="cfg" field="userPrompt" />
  </el-form-item>
  <el-form-item label="可用工具">
    <el-select
      v-model="cfg.toolIds"
      multiple
      filterable
      placeholder="不选则使用应用绑定工具"
      style="width: 100%"
    >
      <el-option v-for="t in tools" :key="t.id" :label="t.name" :value="t.id" />
    </el-select>
  </el-form-item>
  <el-form-item label="关联数据集">
    <el-select
      v-model="cfg.datasetIds"
      multiple
      filterable
      placeholder="不选则使用应用绑定数据集"
      style="width: 100%"
    >
      <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
    </el-select>
  </el-form-item>
  <el-form-item label="最大循环轮数">
    <el-input-number v-model="cfg.maxIterations" :min="1" :max="20" />
  </el-form-item>
  <el-form-item label="输出附带工具调用过程">
    <el-switch v-model="cfg.includeSteps" />
    <span class="field-tip inline">开启后节点输出末尾追加每步工具调用与结果</span>
  </el-form-item>
</template>

<style scoped>
.field-tip {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
.field-tip.inline {
  margin-left: 8px;
}
.sys-prompt-label {
  display: inline-flex;
  align-items: center;
}
.pick-template-btn {
  margin-left: 10px;
  padding: 0;
  font-size: 12px;
}
</style>
