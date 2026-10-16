<script setup lang="ts">
import { computed, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import VarChips from '../VarChips.vue'
import { initialBranches, isMultiBranch, nextBranchKey } from '@/utils/flow'
import type { VarItem } from '@/utils/flow'

/** 条件分支节点：是/否 二分 或 多分支规则（分支增删改名时同步画布连线） */
const props = defineProps<{
  cfg: Record<string, any>
  vars: VarItem[]
  /** Vue Flow 节点对象 */
  node: any
  /** 画布连线数组（可写：分支改名/删除时同步连线） */
  edges: any[]
}>()

const conditionPlaceholder =
  "支持比较表达式，字符串请加引号\n示例：{{input}} 非空即真\n示例：'{{node1}}' == '成功'\n示例：{{count}} >= 3\n示例：'{{input}}' contains '关键'"
const TIP_CONDITION = '全部条件不成立时走「默认」分支；表达式为空的分支视为命中。'
const TIP_BRANCH_OFF = '表达式为真走「是」分支，为假走「否」分支。'

const multiBranch = computed(() => isMultiBranch(props.node?.data))
/** 分支列表：直接引用 config.branches 中的对象，保证表单编辑可写回 */
const branches = computed<Array<{ key: string; label: string; expression: string }>>(() => {
  const list = props.cfg.branches
  return Array.isArray(list) ? list : []
})

function toggleMulti(val: boolean) {
  if (val) {
    props.cfg.branches = initialBranches()
    delete props.cfg.expression
  } else {
    const keys = branches.value.map((b) => b.key)
    delete props.cfg.branches
    props.cfg.expression = ''
    removeEdgesOf(keys)
  }
  syncBranchEdgeLabels()
}

function addBranch() {
  const list = branches.value.slice()
  list.push({
    key: nextBranchKey(props.node?.data),
    label: `分支${list.length + 1}`,
    expression: ''
  })
  props.cfg.branches = list
}

function removeBranch(i: number) {
  const list = branches.value.slice()
  const [removed] = list.splice(i, 1)
  props.cfg.branches = list
  if (removed) removeEdgesOf([removed.key])
}

/** 删除指定 handle 上的全部出边 */
function removeEdgesOf(keys: string[]) {
  if (!keys.length) return
  for (let i = props.edges.length - 1; i >= 0; i--) {
    const e = props.edges[i]
    if (e.source === props.node?.id && keys.includes(e.sourceHandle)) {
      props.edges.splice(i, 1)
    }
  }
}

/** 分支改名后同步连线标签 */
function syncBranchEdgeLabels() {
  for (const b of branches.value) {
    for (const e of props.edges) {
      if (e.source === props.node?.id && e.sourceHandle === b.key) {
        e.label = b.label || b.key
      }
    }
  }
}

watch(
  () => branches.value.map((b) => `${b.key}:${b.label}`).join('|'),
  () => syncBranchEdgeLabels()
)
</script>

<template>
  <el-form-item label="多分支模式">
    <el-switch :model-value="multiBranch" @update:model-value="toggleMulti" />
    <span class="field-tip inline">关闭为 是/否 二分分支，开启可配置任意条分支 + 默认分支</span>
  </el-form-item>

  <template v-if="multiBranch">
    <el-divider content-position="left">分支规则（自上而下命中即止）</el-divider>
    <div class="branch-list">
      <div v-for="(b, i) in branches" :key="b.key" class="branch-item">
        <div class="branch-item-head">
          <el-tag size="small" effect="light" type="primary">{{ b.key }}</el-tag>
          <el-input v-model="b.label" placeholder="分支名" size="small" class="branch-label" />
          <el-button text circle size="small" :icon="Delete" @click="removeBranch(i)" />
        </div>
        <el-input
          v-model="b.expression"
          type="textarea"
          :rows="2"
          :placeholder="conditionPlaceholder"
        />
        <VarChips :vars="vars" :target="b" field="expression" />
      </div>
      <el-button size="small" plain :icon="Plus" @click="addBranch">添加分支</el-button>
    </div>
    <p class="field-tip">{{ TIP_CONDITION }}</p>
  </template>
  <template v-else>
    <el-form-item label="判断条件">
      <el-input
        v-model="cfg.expression"
        type="textarea"
        :rows="2"
        :placeholder="conditionPlaceholder"
      />
      <VarChips :vars="vars" :target="cfg" field="expression" />
    </el-form-item>
    <p class="field-tip">{{ TIP_BRANCH_OFF }}</p>
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
.branch-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 10px;
}
.branch-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px;
  background: #fafbfc;
}
.branch-item-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.branch-label {
  flex: 1;
  min-width: 0;
}
</style>
