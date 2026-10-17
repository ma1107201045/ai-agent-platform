<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { guardApi } from '@/api/guard'
import type { GuardAppBindVO, GuardRule } from '@/api/guard'
import { actionLabel } from '../guardMeta'

/** 应用绑定抽屉：expose open(row)；保存成功 emit saved */
defineProps<{ allRules: GuardRule[] }>()
const emit = defineEmits<{ saved: [] }>()

const bindDrawer = ref(false)
const bindSaving = ref(false)
const currentApp = ref<GuardAppBindVO | null>(null)
const bindForm = reactive<{ checked: number[]; mode: string; enabled: number }>({
  checked: [],
  mode: 'enforce',
  enabled: 1
})

function parseRuleIds(json?: string): number[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.map(Number).filter((n: number) => !Number.isNaN(n)) : []
  } catch {
    return []
  }
}

function open(row: GuardAppBindVO) {
  currentApp.value = row
  bindForm.mode = row.bindMode || 'enforce'
  bindForm.enabled = row.bindEnabled ?? 1
  bindForm.checked = parseRuleIds(row.ruleIds)
  bindDrawer.value = true
}

async function saveBind() {
  if (!currentApp.value) return
  bindSaving.value = true
  try {
    await guardApi.saveBind(currentApp.value.appId, {
      ruleIds: bindForm.checked,
      mode: bindForm.mode,
      enabled: bindForm.enabled
    })
    ElMessage.success('绑定已保存')
    bindDrawer.value = false
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    bindSaving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-drawer
    v-model="bindDrawer"
    :title="`内容安全绑定 · ${currentApp?.appName ?? ''}`"
    size="520px"
  >
    <template v-if="currentApp">
      <el-form label-width="80px">
        <el-form-item label="运行模式">
          <el-radio-group v-model="bindForm.mode">
            <el-radio value="enforce">强制（命中拦截即拒绝）</el-radio>
            <el-radio value="log">仅记录（不拦截，日志留痕）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="启用绑定">
          <el-switch
            v-model="bindForm.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
        <el-form-item label="选择规则">
          <div class="rule-picker">
            <div class="picker-group">
              <div class="picker-group-title">输入侧</div>
              <el-checkbox-group v-model="bindForm.checked">
                <el-checkbox
                  v-for="r in allRules.filter((x) => x.direction === 'input')"
                  :key="r.id"
                  :value="r.id"
                  :disabled="r.enabled !== 1"
                >
                  <span>{{ r.name }}</span>
                  <span class="muted">（{{ actionLabel(r.action) }}）</span>
                </el-checkbox>
              </el-checkbox-group>
              <div
                v-if="!allRules.filter((x) => x.direction === 'input').length"
                class="picker-empty"
              >
                暂无输入侧规则
              </div>
            </div>
            <div class="picker-group">
              <div class="picker-group-title">输出侧</div>
              <el-checkbox-group v-model="bindForm.checked">
                <el-checkbox
                  v-for="r in allRules.filter((x) => x.direction === 'output')"
                  :key="r.id"
                  :value="r.id"
                  :disabled="r.enabled !== 1"
                >
                  <span>{{ r.name }}</span>
                  <span class="muted">（{{ actionLabel(r.action) }}）</span>
                </el-checkbox>
              </el-checkbox-group>
              <div
                v-if="!allRules.filter((x) => x.direction === 'output').length"
                class="picker-empty"
              >
                暂无输出侧规则
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <div class="form-tip">
        已选择 {{ bindForm.checked.length }} 条规则。停用状态的规则不可选。
      </div>
    </template>
    <template #footer>
      <el-button @click="bindDrawer = false">取消</el-button>
      <el-button type="primary" class="btn-gradient" :loading="bindSaving" @click="saveBind"
        >保存绑定</el-button
      >
    </template>
  </el-drawer>
</template>

<style scoped>
.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
}
.rule-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px;
  background: var(--fill-lighter);
}
.picker-group {
  display: flex;
  flex-direction: column;
}
.picker-group-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.el-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.el-checkbox {
  height: auto;
  margin-right: 0;
}
.picker-empty {
  font-size: 12px;
  color: var(--text-tertiary);
  padding: 4px 0;
}
</style>
