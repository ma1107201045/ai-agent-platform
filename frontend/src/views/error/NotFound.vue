<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="not-found-code">404</div>
    <p class="not-found-text">页面不存在或已被移除</p>
    <el-button type="primary" plain @click="router.replace('/')">返回首页</el-button>
  </div>
</template>

<style scoped>
.not-found {
  height: 100%;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.not-found-code {
  font-size: 64px;
  font-weight: 700;
  color: var(--brand-1);
  letter-spacing: 4px;
}

.not-found-text {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
