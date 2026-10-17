<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleCloseFilled, WarningFilled } from '@element-plus/icons-vue'
import { guardApi } from '@/api/guard'
import type { GuardRule, GuardTestResult } from '@/api/guard'
import { riskInfo, dirLabel, dirTag, actionLabel, actionTag } from '../guardMeta'

/** 命中测试弹窗：expose open(rule?)；指定规则则只测该规则，否则测全部启用规则 */
const testVisible = ref(false)
const testLoading = ref(false)
const testRuleId = ref<number | null>(null)
const testText = ref('')
const testResult = ref<GuardTestResult | null>(null)
const testError = ref('')

function open(row?: GuardRule) {
  testRuleId.value = row ? row.id : null
  testText.value = ''
  testResult.value = null
  testError.value = ''
  testVisible.value = true
}

async function runTest() {
  if (!testText.value.trim()) return ElMessage.warning('请输入待检测文本')
  testLoading.value = true
  testError.value = ''
  testResult.value = null
  try {
    testResult.value = await guardApi.test({
      ruleIds: testRuleId.value ? [testRuleId.value] : undefined,
      text: testText.value
    })
  } catch (e) {
    testError.value = e instanceof Error ? e.message : String(e)
  } finally {
    testLoading.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="testVisible"
    :title="testRuleId ? '规则命中测试' : '命中测试（全部启用规则）'"
    width="620px"
  >
    <div class="test-field">
      <el-input
        v-model="testText"
        type="textarea"
        :rows="4"
        placeholder="输入待检测的文本，例如：请联系我微信 138xxxx8888 并可提供赌博服务"
      />
      <el-button
        type="primary"
        class="btn-gradient"
        style="margin-top: 10px"
        :loading="testLoading"
        @click="runTest"
      >
        {{ testLoading ? '检测中…' : '开始检测' }}
      </el-button>
    </div>
    <div v-if="testError" class="test-block">
      <div class="test-block-title">检测失败</div>
      <pre class="mono">{{ testError }}</pre>
    </div>
    <template v-else-if="testResult">
      <div v-if="testResult.blocked" class="test-block blocked">
        <div class="test-block-title">
          <el-icon><CircleCloseFilled /></el-icon> 已拦截 · 命中
          {{ testResult.hitCount }} 条规则（含拦截动作）
        </div>
      </div>
      <div v-else-if="testResult.hits.length" class="test-block">
        <div class="test-block-title">
          <el-icon><WarningFilled /></el-icon> 检测通过 · 命中
          {{ testResult.hitCount }} 条（无拦截动作）
        </div>
        <div v-if="testResult.changed" class="test-output">
          处理后文本：<span class="mono">{{ testResult.output }}</span>
        </div>
      </div>
      <div v-else class="test-clean">
        <el-icon><CircleCheck /></el-icon> 未命中任何启用规则，文本通过检测
      </div>
      <div v-if="testResult.hits.length" class="hit-list">
        <div v-for="(h, i) in testResult.hits" :key="h.ruleId" class="hit-item">
          <div class="hit-head">
            <span class="hit-index">{{ i + 1 }}</span>
            <b>{{ h.name }}</b>
            <el-tag :type="dirTag(h.direction)" size="small" effect="plain">{{
              dirLabel(h.direction)
            }}</el-tag>
            <el-tag :type="actionTag(h.action)" size="small" effect="plain">{{
              actionLabel(h.action)
            }}</el-tag>
            <span class="risk-dot" :style="{ background: riskInfo(h.riskLevel).color }"></span>
          </div>
          <div class="hit-matched">
            <span class="muted">命中内容：</span>
            <el-tag
              v-for="(mm, j) in h.matched"
              :key="j"
              size="small"
              type="danger"
              effect="light"
              >{{ mm }}</el-tag
            >
          </div>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.muted {
  color: var(--text-tertiary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
.test-field {
  display: flex;
  flex-direction: column;
}
.test-clean {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 12px;
  border-radius: var(--radius-md);
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  font-weight: 600;
}
.test-block {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
}
.test-block.blocked {
  background: var(--el-color-danger-light-9);
}
.test-block-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: var(--el-color-danger);
  font-size: 13px;
}
.test-block pre {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-color-danger);
}
.test-output {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
.hit-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hit-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 8px 10px;
}
.hit-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hit-index {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.hit-matched {
  margin-top: 6px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.risk-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
</style>
