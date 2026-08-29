<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Monitor, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import ThemeSwitch from '@/components/ThemeSwitch.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: ''
})

const features = [
  { title: '可视化编排', desc: '拖拽式搭建智能体工作流' },
  { title: '多模型接入', desc: '统一管理 DeepSeek 等模型供应商' },
  { title: '知识库 RAG', desc: '数据驱动，回答更精准' },
  { title: '一键发布 API', desc: 'Web 对话与开放接口开箱即用' }
]

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
    <div class="theme-corner">
      <ThemeSwitch />
    </div>

    <!-- 左侧品牌区 -->
    <div class="brand-panel">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
      <div class="grid-overlay"></div>

      <div class="brand-content">
        <div class="brand-logo">
          <div class="logo-badge">
            <el-icon :size="22"><Monitor /></el-icon>
          </div>
          <span>AgentForge</span>
        </div>
        <h1 class="slogan">
          构建、编排、发布<br />
          你的 AI 智能体
        </h1>
        <p class="sub-slogan">一站式智能体开发平台，从原型到生产仅需几步</p>

        <div class="features">
          <div v-for="(f, i) in features" :key="f.title" class="feature" :style="{ '--i': i }">
            <div class="feature-dot"></div>
            <div>
              <div class="feature-title">{{ f.title }}</div>
              <div class="feature-desc">{{ f.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧登录区 -->
    <div class="form-panel">
      <div class="login-card">
        <div class="card-head">
          <h2>欢迎回来</h2>
          <p>登录你的账号，继续智能体之旅</p>
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
            class="submit btn-gradient"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中…' : '登 录' }}
          </el-button>
        </el-form>

        <p class="tip">默认账号：admin / admin（首次登录后建议修改密码）</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  display: flex;
  background: var(--bg-card);
}
.theme-corner {
  position: absolute;
  top: 18px;
  right: 20px;
  z-index: 10;
}

/* ---------- 左侧品牌区 ---------- */
.brand-panel {
  position: relative;
  flex: 1.15;
  background: linear-gradient(135deg, #4b5ae8 0%, #7c4dff 55%, #9b5cff 100%);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.grid-overlay {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.08) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse at center, #000 30%, transparent 75%);
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.55;
  animation: float 9s ease-in-out infinite;
}
.orb-1 {
  width: 340px;
  height: 340px;
  background: #ff8f6b;
  top: -90px;
  right: -60px;
}
.orb-2 {
  width: 280px;
  height: 280px;
  background: #5be3ff;
  bottom: -70px;
  left: -50px;
  animation-delay: 2s;
}
.orb-3 {
  width: 180px;
  height: 180px;
  background: #ffd75b;
  bottom: 26%;
  right: 18%;
  animation-delay: 4s;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-22px) scale(1.06);
  }
}

.brand-content {
  position: relative;
  z-index: 2;
  padding: 48px;
  max-width: 520px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  animation: rise 0.8s ease both;
}
.logo-badge {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(6px);
}
.brand-logo span {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.slogan {
  margin-top: 36px;
  font-size: 40px;
  line-height: 1.35;
  font-weight: 700;
  animation: rise 0.8s ease 0.1s both;
}
.sub-slogan {
  margin-top: 14px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.75);
  animation: rise 0.8s ease 0.2s both;
}

.features {
  margin-top: 40px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px 24px;
}
.feature {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  animation: rise 0.8s ease both;
  animation-delay: calc(0.28s + var(--i) * 0.08s);
}
.feature-dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: #ffd75b;
  box-shadow: 0 0 10px rgba(255, 215, 91, 0.9);
  flex-shrink: 0;
}
.feature-title {
  font-size: 14px;
  font-weight: 600;
}
.feature-desc {
  margin-top: 3px;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.65);
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---------- 右侧登录区 ---------- */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  padding: 32px;
}

.login-card {
  width: 400px;
  padding: 44px 40px 32px;
  background: var(--bg-card);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-pop);
  animation: rise 0.7s ease 0.15s both;
}
.card-head {
  margin-bottom: 28px;
}
.card-head h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}
.card-head p {
  margin-top: 8px;
  font-size: 13.5px;
  color: var(--text-secondary);
}

.submit {
  width: 100%;
  margin-top: 4px;
  height: 44px;
  font-size: 15px;
  letter-spacing: 2px;
}
.tip {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
}

@media (max-width: 900px) {
  .brand-panel {
    display: none;
  }
}
</style>
