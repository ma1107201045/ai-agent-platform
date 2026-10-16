<script setup lang="ts">
import VarChips from '../VarChips.vue'
import type { VarItem } from '@/utils/flow'
import type { ChatModelInfo, KnowledgeDataset } from '@/api/types'

/** LLM 节点：模型 / 提示词 / 采样参数 / 知识库增强 */
defineProps<{
  cfg: Record<string, any>
  vars: VarItem[]
  chatModels: ChatModelInfo[]
  rerankModels: ChatModelInfo[]
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
      placeholder="设定模型角色与行为…"
    />
    <VarChips :vars="vars" :target="cfg" field="systemPrompt" />
  </el-form-item>
  <el-form-item label="用户提示词">
    <el-input
      v-model="cfg.userPrompt"
      type="textarea"
      :rows="3"
      placeholder="发送给模型的用户消息，默认 {{input}}"
    />
    <VarChips :vars="vars" :target="cfg" field="userPrompt" />
  </el-form-item>

  <el-divider content-position="left">采样参数</el-divider>
  <el-form-item label="温度">
    <el-slider v-model="cfg.temperature" :min="0" :max="2" :step="0.1" />
  </el-form-item>
  <el-form-item label="Top P">
    <el-slider v-model="cfg.topP" :min="0" :max="1" :step="0.05" />
  </el-form-item>
  <el-form-item label="最大输出 Tokens">
    <el-input-number v-model="cfg.maxTokens" :min="0" :max="32768" :step="256" />
    <span class="field-tip inline">0 表示不限制</span>
  </el-form-item>
  <el-form-item label="输出格式">
    <el-select v-model="cfg.outputFormat" style="width: 100%">
      <el-option label="纯文本" value="text" />
      <el-option label="JSON 对象" value="json" />
    </el-select>
  </el-form-item>

  <el-divider content-position="left">知识库增强</el-divider>
  <el-form-item label="关联数据集">
    <el-select v-model="cfg.datasetId" placeholder="不使用知识库" clearable style="width: 100%">
      <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
    </el-select>
  </el-form-item>
  <template v-if="cfg.datasetId">
    <el-form-item label="检索词模板">
      <el-input v-model="cfg.queryTemplate" placeholder="默认 {{input}}，可引用上游节点输出" />
      <VarChips :vars="vars" :target="cfg" field="queryTemplate" />
    </el-form-item>
    <el-form-item label="召回数量">
      <el-input-number v-model="cfg.topK" :min="1" :max="20" />
    </el-form-item>
    <el-form-item label="重排模型">
      <el-select v-model="cfg.rerankModelId" placeholder="不使用重排" clearable style="width: 100%">
        <el-option
          v-for="m in rerankModels"
          :key="m.id"
          :label="`${m.providerName} / ${m.modelName}`"
          :value="m.id"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="相似度阈值">
      <el-slider v-model="cfg.scoreThreshold" :min="0" :max="1" :step="0.05" />
    </el-form-item>
    <el-form-item label="上下文模板">
      <el-input
        v-model="cfg.knowledgeTemplate"
        type="textarea"
        :rows="3"
        placeholder="留空使用默认；用 {{context}} 表示命中片段位置"
      />
    </el-form-item>
  </template>
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
