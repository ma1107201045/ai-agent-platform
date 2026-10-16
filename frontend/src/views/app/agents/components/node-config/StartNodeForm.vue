<script setup lang="ts">
import { computed } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

/** 开始节点：流程变量 + 开场白 */
const props = defineProps<{ cfg: Record<string, any> }>()

const TIP_VAR_USAGE = '变量渲染后写入全局，下游节点可用 {{变量名}} 引用；常用于抽取、改写用户输入。'

const variables = computed<Array<{ name: string; value: string }>>(() => {
  const v = props.cfg.variables
  return Array.isArray(v) ? v : []
})

function addVariable() {
  const list = variables.value.slice()
  list.push({ name: '', value: '' })
  props.cfg.variables = list
}

function removeVariable(i: number) {
  props.cfg.variables = variables.value.filter((_, idx) => idx !== i)
}
</script>

<template>
  <el-divider content-position="left">流程变量</el-divider>
  <div class="kv-list">
    <div v-for="(v, i) in variables" :key="i" class="kv-row">
      <el-input v-model="v.name" placeholder="变量名" class="kv-name" />
      <el-input v-model="v.value" placeholder="变量值，支持 {{input}}" class="kv-value" />
      <el-button text circle :icon="Delete" @click="removeVariable(i)" />
    </div>
    <el-button size="small" plain :icon="Plus" @click="addVariable">添加变量</el-button>
  </div>
  <p class="field-tip">{{ TIP_VAR_USAGE }}</p>
  <el-form-item label="开场白">
    <el-input
      v-model="cfg.welcome"
      type="textarea"
      :rows="3"
      placeholder="对话开始时的欢迎语（前端展示用）"
    />
  </el-form-item>
</template>

<style scoped>
.field-tip {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
.kv-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}
.kv-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.kv-name {
  width: 88px;
  flex-shrink: 0;
}
.kv-value {
  flex: 1;
  min-width: 0;
}
</style>
