<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue'
import VarChips from '../VarChips.vue'
import type { VarItem } from '@/utils/flow'

/** 通用执行策略（重试 / 超时 / 错误处理 / 输出别名），起止节点不渲染本组件 */
defineProps<{ cfg: Record<string, any>; vars: VarItem[] }>()

const TIP_OUTPUT_VAR = '填写后下游可用 {{别名}} 引用本节点输出'
</script>

<template>
  <el-divider content-position="left">
    <span class="divider-with-tip">
      执行策略
      <el-tooltip content="节点级的重试、超时与错误处理，由执行引擎统一实施" placement="top">
        <el-icon class="divider-tip-icon"><QuestionFilled /></el-icon>
      </el-tooltip>
    </span>
  </el-divider>
  <el-form-item label="失败重试次数">
    <el-input-number v-model="cfg.retries" :min="0" :max="5" />
  </el-form-item>
  <el-form-item label="超时时间">
    <el-input-number v-model="cfg.timeoutSeconds" :min="0" :max="300" />
    <span class="field-tip inline">秒，0 表示不限制</span>
  </el-form-item>
  <el-form-item label="出错时">
    <el-select v-model="cfg.onError" style="width: 100%">
      <el-option label="中断流程（下游跳过）" value="fail" />
      <el-option label="忽略错误继续执行" value="continue" />
      <el-option label="使用兜底输出继续" value="fallback" />
    </el-select>
  </el-form-item>
  <el-form-item v-if="cfg.onError === 'fallback'" label="兜底输出">
    <el-input v-model="cfg.errorFallback" placeholder="出错时作为本节点输出继续流程" />
    <VarChips :vars="vars" :target="cfg" field="errorFallback" />
  </el-form-item>
  <el-form-item label="输出变量别名">
    <el-input v-model="cfg.outputVar" placeholder="留空则用节点 ID 引用输出" />
    <span class="field-tip">{{ TIP_OUTPUT_VAR }}</span>
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
.divider-with-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.divider-tip-icon {
  color: var(--text-tertiary);
}
</style>
