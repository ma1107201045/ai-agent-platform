<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: ''
})

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch {
    // 错误已由拦截器统一提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo">
          <el-icon :size="28"><Cpu /></el-icon>
        </div>
        <h1>智能体平台</h1>
        <p>构建、编排与发布你的 AI 智能体</p>
      </div>

      <el-form @submit.prevent="handleLogin">
        <el-form-item>
          <el-input
            v-model="form.username"
            size="large"
            placeholder="用户名"
            :prefix-icon="User"
            autocomplete="username"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="submit"
          :loading="loading"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>

      <p class="tip">默认账号：admin / admin（首次登录后建议修改密码）</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2b4d 0%, #2d5da8 60%, #3a7bd5 100%);
}
.login-card {
  width: 380px;
  padding: 40px 36px 28px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.brand {
  text-align: center;
  margin-bottom: 28px;
}
.logo {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, #3a7bd5, #2d5da8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand h1 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
}
.brand p {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}
.submit {
  width: 100%;
  margin-top: 4px;
}
.tip {
  margin-top: 18px;
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
