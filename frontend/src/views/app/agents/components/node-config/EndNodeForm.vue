<script setup lang="ts">
import VarChips from '../VarChips.vue'
import type { VarItem } from '@/utils/flow'

/** 结束节点：回答模板 */
defineProps<{ cfg: Record<string, any>; vars: VarItem[] }>()
</script>

<template>
  <el-form-item label="回答模板">
    <el-input
      v-model="cfg.answerTemplate"
      type="textarea"
      :rows="4"
      placeholder="留空则取上游节点输出；可引用 {{input}} 与 {{节点id}}"
    />
    <VarChips :vars="vars" :target="cfg" field="answerTemplate" />
  </el-form-item>
</template>
