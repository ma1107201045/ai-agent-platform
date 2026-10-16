<script setup lang="ts">
import VarChips from '../VarChips.vue'
import type { VarItem } from '@/utils/flow'
import type { ChatModelInfo, Skill } from '@/api/types'

/** 技能执行节点 */
defineProps<{
  cfg: Record<string, any>
  vars: VarItem[]
  chatModels: ChatModelInfo[]
  skills: Skill[]
}>()
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
  <el-form-item label="执行技能">
    <el-select v-model="cfg.skillId" filterable placeholder="选择要执行的技能" style="width: 100%">
      <el-option v-for="s in skills" :key="s.id" :value="s.id" :label="`${s.name}（${s.code}）`">
        <div class="skill-option">
          <span>{{ s.name }}</span>
          <el-tag
            size="small"
            effect="plain"
            :type="s.type === 'tool' ? 'warning' : s.type === 'knowledge' ? 'success' : 'info'"
          >
            {{ s.type === 'tool' ? '工具复合' : s.type === 'knowledge' ? '知识' : '指令' }}
          </el-tag>
        </div>
      </el-option>
    </el-select>
    <span class="field-tip inline">技能指令与依赖工具/知识库在运行期自动注入</span>
  </el-form-item>
  <el-form-item label="用户提示词">
    <el-input v-model="cfg.userPrompt" type="textarea" :rows="3" placeholder="默认 {{input}}" />
    <VarChips :vars="vars" :target="cfg" field="userPrompt" />
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
</style>
