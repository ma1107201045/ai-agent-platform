<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, Files } from '@element-plus/icons-vue'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgentType } from '@/api/types'
import { appTemplates, typeLabels, typeMeta, type AppTemplate } from '../agentMeta'

/** 新建应用向导弹窗（选择方式 → 模板/表单）：expose open()；创建成功 emit created */
const emit = defineEmits<{ created: [] }>()

const dialogVisible = ref(false)
const creating = ref(false)
/** 创建向导步骤：choose=选择创建方式 template=模板选择 form=填写信息 */
const step = ref<'choose' | 'template' | 'form'>('choose')
const createForm = reactive({
  name: '',
  type: 'chatflow' as AppAgentType,
  description: ''
})

const dialogWidth = computed(() =>
  step.value === 'template' ? '760px' : step.value === 'choose' ? '640px' : '520px'
)

function open() {
  createForm.name = ''
  createForm.type = 'chatflow'
  createForm.description = ''
  step.value = 'choose'
  dialogVisible.value = true
}

function goTemplate() {
  step.value = 'template'
}

function goBlank() {
  createForm.name = ''
  createForm.type = 'chatflow'
  createForm.description = ''
  step.value = 'form'
}

function selectTemplate(tpl: AppTemplate) {
  createForm.name = tpl.name
  createForm.type = tpl.type
  createForm.description = tpl.desc
  step.value = 'form'
}

function backToChoose() {
  step.value = 'choose'
}

async function confirmCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入应用名称')
    return
  }
  creating.value = true
  try {
    await appAgentApi.create({
      name: createForm.name.trim(),
      type: createForm.type,
      description: createForm.description
    })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    emit('created')
  } finally {
    creating.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="新建智能体应用"
    :width="dialogWidth"
    :close-on-click-modal="false"
    class="create-dialog"
  >
    <el-steps
      v-if="step !== 'choose'"
      :active="1"
      align-center
      finish-status="success"
      class="create-steps"
    >
      <el-step title="选择创建方式" />
      <el-step title="填写信息" />
    </el-steps>

    <!-- Step 1：选择创建方式 -->
    <div v-if="step === 'choose'" class="create-ways">
      <div class="way-card" @click="goTemplate">
        <div class="way-icon" style="background: linear-gradient(135deg, #5b6cff, #8b5cf6)">
          <el-icon :size="26"><Files /></el-icon>
        </div>
        <div class="way-title">
          从模板创建
          <el-tag size="small" type="primary" effect="light" class="way-tag">推荐</el-tag>
        </div>
        <div class="way-desc">基于内置场景模板快速开始，自动预填配置，适合首次使用</div>
        <div class="way-go">查看场景模板 →</div>
      </div>
      <div class="way-card" @click="goBlank">
        <div class="way-icon" style="background: linear-gradient(135deg, #0ea5e9, #38bdf8)">
          <el-icon :size="26"><EditPen /></el-icon>
        </div>
        <div class="way-title">空白创建</div>
        <div class="way-desc">从零开始搭建，完全自定义应用类型、名称与配置</div>
        <div class="way-go">自定义创建 →</div>
      </div>
    </div>

    <!-- Step 2a：模板选择 -->
    <div v-else-if="step === 'template'" class="template-panel">
      <div class="template-hint">
        选择场景模板将自动预填应用名称与描述，创建后仍可在编辑器中修改
      </div>
      <div class="template-grid">
        <div
          v-for="tpl in appTemplates"
          :key="tpl.key"
          class="template-card"
          @click="selectTemplate(tpl)"
        >
          <div
            class="tpl-cover"
            :style="{ background: `linear-gradient(135deg, ${tpl.color[0]}, ${tpl.color[1]})` }"
          >
            <span class="tpl-icon">{{ tpl.icon }}</span>
          </div>
          <div class="tpl-body">
            <div class="tpl-title-row">
              <span class="tpl-name">{{ tpl.name }}</span>
              <el-tag size="small" effect="plain">{{ typeLabels[tpl.type] }}</el-tag>
            </div>
            <p class="tpl-desc">{{ tpl.desc }}</p>
            <div class="tpl-foot">
              <span class="tpl-use">{{ tpl.useCase }}</span>
              <span class="tpl-arrow">使用 →</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Step 2b：填写信息 -->
    <div v-else class="create-form">
      <div class="create-field-label">应用名称</div>
      <el-input
        v-model="createForm.name"
        placeholder="例如：智能客服助手"
        maxlength="30"
        show-word-limit
      />
      <div class="create-field-label">应用类型</div>
      <div class="type-grid">
        <div
          v-for="(meta, key) in typeMeta"
          :key="key"
          class="type-option"
          :class="{ active: createForm.type === key }"
          @click="createForm.type = key as AppAgentType"
        >
          <span class="type-emoji">{{ meta.icon }}</span>
          <div class="type-info">
            <div class="type-name">{{ meta.label }}</div>
            <div class="type-desc">{{ meta.desc }}</div>
          </div>
        </div>
      </div>
      <div class="create-field-label">应用描述</div>
      <el-input
        v-model="createForm.description"
        type="textarea"
        :rows="2"
        placeholder="简单描述这个应用的用途（可选）"
      />
    </div>

    <template #footer>
      <template v-if="step === 'choose'">
        <el-button @click="dialogVisible = false">取消</el-button>
      </template>
      <template v-else-if="step === 'template'">
        <el-button @click="backToChoose">返回</el-button>
        <el-button type="primary" class="btn-gradient" @click="goBlank"
          >跳过模板，空白创建</el-button
        >
      </template>
      <template v-else>
        <el-button @click="backToChoose">返回</el-button>
        <el-button type="primary" class="btn-gradient" :loading="creating" @click="confirmCreate">
          创建应用
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<style scoped>
.create-dialog :deep(.el-dialog__body) {
  padding-top: 4px;
}
.create-steps {
  margin-bottom: 18px;
  padding-top: 4px;
}

/* Step 1：创建方式 */
.create-ways {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 8px 4px 4px;
}
.way-card {
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-lg, 14px);
  padding: 22px 20px;
  cursor: pointer;
  transition: all 0.22s ease;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.way-card:hover {
  border-color: var(--brand-1);
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(91, 108, 255, 0.12);
}
.way-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.way-title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.way-tag {
  margin-left: 4px;
}
.way-desc {
  font-size: 12.5px;
  color: var(--text-tertiary);
  line-height: 1.6;
  min-height: 40px;
}
.way-go {
  font-size: 12.5px;
  color: var(--brand-1);
  font-weight: 500;
  margin-top: auto;
}

/* Step 2a：模板选择 */
.template-panel {
  padding: 4px 2px 0;
}
.template-hint {
  font-size: 12.5px;
  color: var(--text-tertiary);
  margin-bottom: 14px;
}
.template-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.template-card {
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.22s ease;
  display: flex;
  flex-direction: column;
}
.template-card:hover {
  border-color: var(--brand-1);
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(91, 108, 255, 0.12);
}
.tpl-cover {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.tpl-icon {
  font-size: 26px;
  filter: drop-shadow(0 3px 6px rgba(0, 0, 0, 0.18));
}
.tpl-body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.tpl-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.tpl-name {
  font-size: 13.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tpl-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.55;
  min-height: 38px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tpl-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tpl-use {
  font-size: 11px;
  color: var(--text-secondary);
}
.tpl-arrow {
  font-size: 12px;
  color: var(--brand-1);
  font-weight: 500;
}

/* Step 2b：表单 */
.create-form {
  padding: 4px 8px 0;
}
.create-field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 14px 0 8px;
}
.create-field-label:first-child {
  margin-top: 0;
}
.type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.type-option {
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.type-option:hover {
  border-color: var(--brand-1);
  transform: translateY(-2px);
}
.type-option.active {
  border-color: var(--brand-1);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 3px rgba(91, 108, 255, 0.12);
}
.type-emoji {
  font-size: 22px;
}
.type-name {
  font-size: 13.5px;
  font-weight: 600;
}
.type-desc {
  font-size: 11.5px;
  color: var(--text-tertiary);
  line-height: 1.5;
}
</style>
