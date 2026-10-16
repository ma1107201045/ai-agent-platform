<script setup lang="ts">
import VarChips from '../VarChips.vue'
import type { VarItem } from '@/utils/flow'
import type { ChatModelInfo, KnowledgeDataset } from '@/api/types'

/** 知识库检索节点 */
defineProps<{
  cfg: Record<string, any>
  vars: VarItem[]
  rerankModels: ChatModelInfo[]
  datasets: KnowledgeDataset[]
}>()

const TIP_CHUNK_PLACEHOLDERS = '可用占位：{{index}} {{content}} {{score}} {{documentId}}'
</script>

<template>
  <el-form-item label="关联数据集">
    <el-select v-model="cfg.datasetId" placeholder="请选择数据集" style="width: 100%">
      <el-option v-for="d in datasets" :key="d.id" :label="d.name" :value="d.id" />
    </el-select>
  </el-form-item>
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

  <el-divider content-position="left">输出格式</el-divider>
  <el-form-item label="输出格式">
    <el-select v-model="cfg.outputFormat" style="width: 100%">
      <el-option label="文本拼接" value="text" />
      <el-option label="JSON 数组" value="json" />
    </el-select>
  </el-form-item>
  <template v-if="cfg.outputFormat !== 'json'">
    <el-form-item label="片段模板">
      <el-input v-model="cfg.itemTemplate" placeholder="默认 【片段 {{index}}】{{content}}" />
      <span class="field-tip">{{ TIP_CHUNK_PLACEHOLDERS }}</span>
    </el-form-item>
    <el-form-item label="片段分隔符">
      <el-input v-model="cfg.separator" placeholder="默认两个换行" />
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
</style>
