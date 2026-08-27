<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { appApi } from '@/api/app'
import type { AgentApp } from '@/api/types'

const route = useRoute()
const router = useRouter()
const appId = Number(route.params.id)
const app = ref<AgentApp | null>(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    app.value = await appApi.get(appId)
  } finally {
    loading.value = false
  }
})

async function save() {
  if (!app.value) return
  await appApi.update(appId, {
    name: app.value.name,
    description: app.value.description,
    type: app.value.type
  })
  ElMessage.success('已保存')
}

function back() {
  router.push('/apps')
}
</script>

<template>
  <div v-loading="loading" class="edit-page">
    <div class="edit-header">
      <el-button link @click="back">
        <el-icon><Back /></el-icon>&nbsp;返回
      </el-button>
      <span class="title">{{ app?.name || '应用编排' }}</span>
      <el-button type="primary" size="small" @click="save">保存</el-button>
    </div>

    <el-card shadow="never" v-if="app">
      <el-form label-width="80px" style="max-width: 480px">
        <el-form-item label="名称">
          <el-input v-model="app.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="app.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="app.type">
            <el-option label="对话流 Chatflow" value="chatflow" />
            <el-option label="工作流 Workflow" value="workflow" />
            <el-option label="智能体 Agent" value="agent" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-empty description="可视化编排画布（Vue Flow）将在下一步接入" style="padding: 80px 0" />
  </div>
</template>

<style scoped>
.edit-page {
  height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.edit-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
}
</style>
