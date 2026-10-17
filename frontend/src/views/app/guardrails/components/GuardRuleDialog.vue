<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { guardApi } from '@/api/guard'
import type { GuardRule } from '@/api/guard'
import { DIRS, MATCH_TYPES, ACTIONS, actionDesc } from '../guardMeta'

/** 规则新建/编辑弹窗：expose openCreate/openEdit，保存成功 emit saved */
const emit = defineEmits<{ saved: [] }>()

const dialogVisible = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<Partial<GuardRule>>({})

function resetForm() {
  Object.assign(form, {
    name: '',
    description: '',
    direction: 'input',
    matchType: 'keyword',
    ruleContent: '',
    action: 'block',
    replaceText: '****',
    riskLevel: 3,
    enabled: 1,
    priority: 1
  })
}

function openCreate() {
  resetForm()
  editId.value = null
  dialogVisible.value = true
}

function openEdit(row: GuardRule) {
  editId.value = row.id
  Object.assign(form, {
    name: row.name,
    description: row.description || '',
    direction: row.direction,
    matchType: row.matchType,
    ruleContent: row.ruleContent,
    action: row.action,
    replaceText: row.replaceText || '****',
    riskLevel: row.riskLevel,
    enabled: row.enabled,
    priority: row.priority
  })
  dialogVisible.value = true
}

async function saveRule() {
  if (!form.name?.trim()) return ElMessage.warning('请输入规则名称')
  if (!form.ruleContent?.trim()) return ElMessage.warning('请输入匹配内容')
  if ((form.action === 'mask' || form.action === 'replace') && !form.replaceText?.trim()) {
    return ElMessage.warning('打码/替换需指定替换文本')
  }
  saving.value = true
  const payload: Partial<GuardRule> = {
    name: form.name.trim(),
    description: form.description?.trim() || undefined,
    direction: form.direction as 'input' | 'output',
    matchType: form.matchType!,
    ruleContent: form.ruleContent.trim(),
    action: form.action!,
    replaceText: form.replaceText?.trim() || '****',
    riskLevel: form.riskLevel ?? 3,
    enabled: form.enabled ?? 1,
    priority: form.priority ?? 1
  }
  try {
    if (editId.value != null) {
      await guardApi.updateRule(editId.value, payload)
    } else {
      await guardApi.createRule(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="editId ? '编辑规则' : '新建规则'"
    width="640px"
    :close-on-click-modal="false"
  >
    <el-form label-width="92px">
      <el-form-item label="规则名称" required>
        <el-input
          v-model="form.name"
          placeholder="如 输入-色情低俗词拦截 / 输出-注入提示词"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="规则说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="200"
          placeholder="规则用途说明（可选）"
        />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="作用方向" required>
            <el-radio-group v-model="form.direction">
              <el-radio-button v-for="d in DIRS" :key="d.value" :value="d.value">{{
                d.label
              }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="风险等级">
            <el-rate v-model="form.riskLevel" :max="5" show-score style="height: 32px" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="匹配方式" required>
        <el-radio-group v-model="form.matchType">
          <el-radio-button v-for="m in MATCH_TYPES" :key="m.value" :value="m.value">{{
            m.label
          }}</el-radio-button>
        </el-radio-group>
        <div class="form-tip">
          关键词以英文/中文逗号分隔，如
          <span class="mono">赌博, 色情, 毒品</span>；正则模式直接填写表达式，如
          <span class="mono">1[3-9]\d{9}</span>。
        </div>
      </el-form-item>
      <el-form-item label="匹配内容" required>
        <el-input
          v-model="form.ruleContent"
          type="textarea"
          :rows="2"
          class="mono"
          :placeholder="
            form.matchType === 'regex' ? '输入正则表达式' : '输入敏感关键词（逗号分隔）'
          "
        />
      </el-form-item>
      <el-form-item label="处置动作" required>
        <el-radio-group v-model="form.action">
          <el-radio-button v-for="a in ACTIONS" :key="a.value" :value="a.value">{{
            a.label
          }}</el-radio-button>
        </el-radio-group>
        <div class="form-tip">{{ actionDesc(String(form.action)) }}</div>
      </el-form-item>
      <el-form-item v-if="form.action === 'mask' || form.action === 'replace'" label="替换文本">
        <el-input v-model="form.replaceText" maxlength="64" placeholder="如 ****" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="优先级">
            <el-input-number v-model="form.priority" :min="1" :max="99" style="width: 100%" />
            <div class="form-tip">数字越小越先执行</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="启用">
            <el-switch
              v-model="form.enabled"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" class="btn-gradient" :loading="saving" @click="saveRule"
        >保存</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped>
.mono {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12.5px;
}
.form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
}
</style>
