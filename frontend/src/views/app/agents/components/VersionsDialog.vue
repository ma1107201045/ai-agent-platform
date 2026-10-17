<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgentVersion } from '@/api/types'
import { formatTime } from '@/utils/format'

/** 版本历史弹窗：加载 / 回滚自闭环，回滚成功后通知父组件刷新画布 */
const props = defineProps<{ appId: number }>()

const emit = defineEmits<{
  rolledBack: []
}>()

const visible = defineModel<boolean>('visible', { required: true })

const versions = ref<AppAgentVersion[]>([])
const loadingVersions = ref(false)
const rollingBack = ref(false)

async function openVersions() {
  loadingVersions.value = true
  try {
    versions.value = await appAgentApi.versions(props.appId)
  } catch (e) {
    ElMessage.error((e as Error)?.message || '加载版本列表失败')
  } finally {
    loadingVersions.value = false
  }
}

async function rollbackTo(version: AppAgentVersion) {
  await ElMessageBox.confirm(
    `将把 v${version.version} 的工作流恢复到当前画布（草稿），不会自动发布，确认回滚？`,
    '回滚确认',
    { confirmButtonText: '回滚', cancelButtonText: '取消', type: 'warning' }
  )
  rollingBack.value = true
  try {
    await appAgentApi.rollback(props.appId, version.id)
    ElMessage.success(`已回滚到 v${version.version}，请检查后重新发布`)
    visible.value = false
    emit('rolledBack')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '回滚失败')
  } finally {
    rollingBack.value = false
  }
}

watch(visible, (v) => {
  if (v) openVersions()
})
</script>

<template>
  <el-dialog v-model="visible" title="历史版本" width="560px">
    <div v-loading="loadingVersions" min-height="120">
      <el-empty
        v-if="!loadingVersions && versions.length === 0"
        description="暂无发布版本，点击「发布」生成版本快照"
        :image-size="70"
      />
      <div v-else class="version-list">
        <div v-for="v in versions" :key="v.id" class="version-item">
          <div class="version-info">
            <div class="version-head">
              <span class="version-no">v{{ v.version }}</span>
              <el-tag v-if="v.isPublished === 1" size="small" type="success" effect="light"
                >当前线上</el-tag
              >
              <el-tag v-else size="small" type="info" effect="plain">历史</el-tag>
            </div>
            <div class="version-time">
              {{ formatTime(v.createTime, true) }} · {{ v.createdBy || '未知' }}
            </div>
          </div>
          <el-button size="small" :disabled="rollingBack" @click="rollbackTo(v)">
            回滚到该版本
          </el-button>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.version-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  transition: all 0.15s ease;
}

.version-item:hover {
  border-color: var(--brand-3);
  background: var(--fill-lighter);
}

.version-info {
  min-width: 0;
}

.version-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-no {
  font-weight: 700;
  font-size: 13px;
  color: var(--brand-1);
}

.version-time {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
