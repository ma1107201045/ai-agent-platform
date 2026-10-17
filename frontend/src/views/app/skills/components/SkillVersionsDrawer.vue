<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, RefreshLeft } from '@element-plus/icons-vue'
import { appSkillApi } from '@/api/app-skill'
import type { Skill, SkillVersion } from '@/api/types'
import { formatTime } from '@/utils/format'
import { copyText } from '@/utils/clipboard'

/** 技能版本历史抽屉：expose open(skill)，回退成功后 emit rolledBack */
const emit = defineEmits<{ rolledBack: [] }>()

const versionVisible = ref(false)
const versions = ref<SkillVersion[]>([])
const currentSkill = ref<Skill | null>(null)
const rolling = ref(false)

async function open(row: Skill) {
  currentSkill.value = row
  versions.value = await appSkillApi.versions(row.id)
  versionVisible.value = true
}

async function rollback(v: SkillVersion) {
  if (!currentSkill.value) return
  ElMessageBox.confirm(
    `确认将「${currentSkill.value.name}」回退到 v${v.version}？回退会生成一条新的历史版本用于留痕。`,
    '回退确认',
    { type: 'warning' }
  )
    .then(async () => {
      rolling.value = true
      try {
        const updated = await appSkillApi.rollback(currentSkill.value!.id, v.version)
        ElMessage.success(`已回退到 v${v.version}，当前版本 v${updated.version}`)
        versionVisible.value = false
        emit('rolledBack')
      } finally {
        rolling.value = false
      }
    })
    .catch(() => {})
}

defineExpose({ open })
</script>

<template>
  <el-drawer
    v-model="versionVisible"
    :title="currentSkill ? `版本历史 · ${currentSkill.name}` : '版本历史'"
    size="560px"
  >
    <div v-loading="rolling" class="version-body">
      <el-timeline v-if="versions.length">
        <el-timeline-item
          v-for="v in versions"
          :key="v.id"
          :type="v.version === currentSkill?.version ? 'primary' : 'info'"
          :hollow="v.version === currentSkill?.version"
          :timestamp="formatTime(v.createTime)"
        >
          <div class="version-item">
            <div class="version-title">
              <el-tag size="small" :type="v.version === currentSkill?.version ? 'primary' : 'info'"
                >v{{ v.version }}</el-tag
              >
              <span v-if="v.version === currentSkill?.version" class="current-tag">当前</span>
              <span class="dim-text">{{ v.remark || '内容更新' }}</span>
            </div>
            <pre class="version-content">{{ v.content }}</pre>
            <div class="version-actions">
              <el-button
                size="small"
                link
                :icon="CopyDocument"
                @click="copyText(v.content, `v${v.version} 正文已复制`)"
                >复制</el-button
              >
              <el-button
                v-if="v.version !== currentSkill?.version"
                size="small"
                link
                type="warning"
                :icon="RefreshLeft"
                @click="rollback(v)"
                >回退到此版本</el-button
              >
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无版本记录" :image-size="80" />
    </div>
  </el-drawer>
</template>

<style scoped>
.version-body {
  padding: 0 4px;
}
.version-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}
.version-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.current-tag {
  font-size: 11px;
  font-weight: 600;
  color: var(--brand-1);
}
.dim-text {
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.version-content {
  margin: 8px 0 4px;
  padding: 8px 10px;
  background: var(--bg-muted, #f8fafc);
  border-radius: var(--radius-sm);
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 180px;
  overflow: auto;
}
.version-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
