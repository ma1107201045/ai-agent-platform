<script setup lang="ts">
const stats = [
  { label: '应用总数', value: '-' },
  { label: '已发布应用', value: '-' },
  { label: '模型供应商', value: '-' },
  { label: '今日调用量', value: '-' }
]
</script>

<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col v-for="item in stats" :key="item.label" :span="6">
        <el-card shadow="hover">
          <div class="stat">
            <div class="stat-value">{{ item.value }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>快速开始</template>
      <el-space direction="vertical" alignment="start">
        <span>1. 在「模型管理」中添加模型供应商（如 DeepSeek）并配置 API Key</span>
        <span>2. 在「应用管理」中创建智能体应用</span>
        <span>3. 编排工作流并发布，通过 Web 对话 / API 接入</span>
      </el-space>
    </el-card>
  </div>
</template>

<style scoped>
.stat {
  text-align: center;
  padding: 8px 0;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}
.stat-label {
  margin-top: 8px;
  color: #909399;
  font-size: 14px;
}
</style>
