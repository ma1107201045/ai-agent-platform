<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import { appPromptApi } from '@/api/app-prompt'
import type { PromptTemplate, PromptTemplateVersion } from '@/api/types'
import { formatTime } from '@/utils/format'
import { copyText } from '@/utils/clipboard'

const emit = defineEmits<{ changed: [] }>()

const versionVisible = ref(false)
const versions = ref<PromptTemplateVersion[]>([])
const currentTemplate = ref<PromptTemplate | null>(null)
const versionView = ref<PromptTemplateVersion | null>(null)
const versionViewVisible = ref(false)
const rolling = ref(false)

async function open(row: PromptTemplate) {
  currentTemplate.value = row
  versions.value = await appPromptApi.versions(row.id)
  versionVisible.value = true
}

function viewVersion(v: PromptTemplateVersion) {
  versionView.value = v
  versionViewVisible.value = true
}

async function rollback(v: PromptTemplateVersion) {
  if (!currentTemplate.value) return
  ElMessageBox.confirm(
    `确认将「${currentTemplate.value.name}」回退到 v${v.version}？回退会生成一条新的历史版本用于留痕。`,
    '回退确认',
    { type: 'warning' }
  )
    .then(async () => {
      rolling.value = true
      try {
        const updated = await appPromptApi.rollback(currentTemplate.value!.id, v.version)
        ElMessage.success(`已回退到 v${v.version}，当前版本 v${updated.version}`)
        versionVisible.value = false
        emit('changed')
      } finally {
        rolling.value = false
      }
    })
    .catch(() => {})
}

/** 复制当前查看版本的正文字段（content / variables） */
function copyVersionField(field: 'content' | 'variables') {
  const v = versionView.value
  if (!v) return
  if (field === 'content') {
    copyText(v.content, '模板正文已复制')
  } else if (v.variables) {
    copyText(v.variables, '变量定义已复制')
  }
}

defineExpose({ open })
</script>

<template>
  <!-- 版本历史抽屉 -->
  <el-drawer v-model="versionVisible" title="版本历史" size="480px">
    <template v-if="currentTemplate">
      <div class="version-summary">
        <span class="vs-name">{{ currentTemplate.name }}</span>
        <el-tag size="small" type="primary" effect="light"
          >当前 v{{ currentTemplate.version }}</el-tag
        >
      </div>
      <div v-if="versions.length === 0" class="drawer-empty">暂无历史版本</div>
      <el-timeline v-else class="version-timeline">
        <el-timeline-item
          v-for="v in versions"
          :key="v.id"
          :timestamp="formatTime(v.createTime)"
          placement="top"
          :type="v.version === currentTemplate.version ? 'primary' : 'info'"
          :hollow="v.version !== currentTemplate.version"
        >
          <div class="version-item">
            <div class="vi-head">
              <span class="vi-version">v{{ v.version }}</span>
              <span class="vi-remark">{{ v.remark || '版本更新' }}</span>
              <el-tag
                v-if="v.version === currentTemplate.version"
                size="small"
                type="success"
                effect="light"
                >当前</el-tag
              >
            </div>
            <div class="vi-actions">
              <el-button link type="primary" size="small" @click="viewVersion(v)"
                >查看内容</el-button
              >
              <el-button
                v-if="v.version !== currentTemplate.version"
                link
                type="warning"
                size="small"
                :loading="rolling"
                @click="rollback(v)"
              >
                回退到此版本
              </el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </template>
  </el-drawer>

  <!-- 版本内容查看 -->
  <el-dialog v-model="versionViewVisible" title="版本内容" width="680px">
    <template v-if="versionView">
      <div class="view-meta">
        <el-tag size="small" type="primary" effect="light">v{{ versionView.version }}</el-tag>
        <span class="view-time"
          >{{ formatTime(versionView.createTime) }} · {{ versionView.remark || '版本更新' }}</span
        >
      </div>
      <div class="view-block">
        <div class="view-block-head">
          <span>模板正文</span>
          <el-button
            link
            type="primary"
            size="small"
            :icon="CopyDocument"
            @click="copyVersionField('content')"
          >
            复制正文
          </el-button>
        </div>
        <pre class="content-pre">{{ versionView.content }}</pre>
      </div>
      <div v-if="versionView.variables" class="view-block">
        <div class="view-block-head">
          <span>变量定义</span>
          <el-button
            link
            type="primary"
            size="small"
            :icon="CopyDocument"
            @click="copyVersionField('variables')"
          >
            复制变量
          </el-button>
        </div>
        <pre class="content-pre var-pre">{{ versionView.variables }}</pre>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
/* ---------- 版本抽屉 ---------- */
.version-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.vs-name {
  font-size: 15px;
  font-weight: 600;
}
.drawer-empty {
  text-align: center;
  color: var(--text-tertiary);
  padding: 40px 0;
}
.version-timeline {
  padding-left: 4px;
}
.version-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.vi-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.vi-version {
  font-weight: 700;
  color: var(--brand-1);
}
.vi-remark {
  font-size: 12.5px;
  color: var(--text-secondary);
}
.vi-actions {
  display: flex;
  gap: 12px;
}

/* ---------- 版本内容查看 ---------- */
.view-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.view-time {
  font-size: 12px;
  color: var(--text-tertiary);
}
.content-pre {
  background: var(--bg-fill, #f5f7fa);
  border-radius: 10px;
  padding: 14px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 60vh;
  overflow: auto;
}
.view-block {
  margin-bottom: 14px;
}
.view-block:last-child {
  margin-bottom: 0;
}
.view-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.var-pre {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
