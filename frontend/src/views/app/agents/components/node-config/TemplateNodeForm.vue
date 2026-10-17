<script setup lang="ts">
import VarChips from '../VarChips.vue'
import type { VarItem, NodeConfig } from '@/utils/flow'

/** 模板节点：变量插值文本 */
defineProps<{ cfg: NodeConfig; vars: VarItem[] }>()
</script>

<template>
  <el-form-item label="模板内容">
    <el-input
      v-model="cfg.template"
      type="textarea"
      :rows="6"
      placeholder="支持 {{input}} 与 {{节点id}} 变量插值"
    />
    <VarChips :vars="vars" :target="cfg" field="template" />
  </el-form-item>
</template>
