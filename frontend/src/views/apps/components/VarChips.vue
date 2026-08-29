<script setup lang="ts">
import type { VarItem } from '@/utils/flow'

/** 可点击插入的变量块：把变量追加到目标配置字段末尾 */
const props = defineProps<{
  vars: VarItem[]
  /** 目标配置对象（节点 config 引用） */
  target: Record<string, any>
  /** 目标字段名 */
  field: string
}>()

function insert(item: VarItem) {
  const cur = props.target[props.field]
  const base = cur == null ? '' : String(cur)
  const sep = base && !/\s$/.test(base) ? ' ' : ''
  props.target[props.field] = base + sep + item.text
}
</script>

<template>
  <div v-if="vars.length" class="var-chips">
    <span class="var-chips-title">可用变量</span>
    <span
      v-for="v in vars"
      :key="v.text"
      class="var-chip"
      :title="v.desc"
      @click="insert(v)"
      >{{ v.text }}</span
    >
  </div>
</template>

<style scoped>
.var-chips {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
.var-chips-title {
  font-size: 11px;
  color: var(--text-tertiary, #8f9bb3);
}
.var-chip {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
  color: #5b6cff;
  background: #f1f2ff;
  border: 1px dashed #b9c1ff;
  border-radius: 5px;
  padding: 1px 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.var-chip:hover {
  background: #5b6cff;
  color: #fff;
  border-color: #5b6cff;
}
</style>
