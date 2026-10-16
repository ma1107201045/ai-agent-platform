<script setup lang="ts">
import VarChips from '../VarChips.vue'
import type { VarItem } from '@/utils/flow'

/** 表达式计算节点（MVEL 脚本） */
defineProps<{ cfg: Record<string, any>; vars: VarItem[] }>()

const TIP_SCRIPT_VARS =
  '脚本内可直接使用 input（用户输入）、outputs（全部节点输出集合）以及各上游节点 id 变量。'
</script>

<template>
  <el-form-item label="表达式脚本">
    <el-input
      v-model="cfg.code"
      type="textarea"
      :rows="10"
      placeholder="MVEL 表达式，可用 input / outputs / 各节点输出变量，最后 return 结果&#10;示例：return input.trim().toUpperCase()"
    />
    <VarChips :vars="vars" :target="cfg" field="code" />
  </el-form-item>
  <p class="field-tip">{{ TIP_SCRIPT_VARS }}</p>
</template>

<style scoped>
.field-tip {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
</style>
