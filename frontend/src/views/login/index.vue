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
    <!-- 背景光斑：与 Dashboard 欢迎横幅同源的柔和氛围层（纯 radial 渐变，无 filter/毛玻璃，
         不参与任何重采样，主题切换零抖动） -->
    <div class="deco deco-a"></div>
    <div class="deco deco-b"></div>
    <div class="deco deco-c"></div>

    <!-- 主题切换：直接使用组件默认样式，与主页顶栏同款透明图标按钮 -->
    <div class="theme-corner">
      <ThemeSwitch />
    </div>

    <div class="login-wrap">
      <!-- 品牌标识：与主页侧边栏同款（渐变方块 badge + 渐变文字） -->
      <div class="brand">
        <div class="logo-badge">
          <el-icon :size="20"><Monitor /></el-icon>
        </div>
        <span class="logo-text">AgentForge</span>
      </div>
      <p class="brand-sub">一站式智能体开发平台</p>

      <!-- 表单卡片：沿用全局 hover-card 卡片体系（bg-card / border-color / radius-xl） -->
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
/* 整页复用全局主题令牌：亮色下为浅灰蓝内容页背景，暗色下自动切换为 #0f1220，
   与登录后的主页内容区视觉同源，随主题整体切换 */
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 32px 24px;
  background: var(--bg-page);
}

.theme-corner {
  position: absolute;
  top: 20px;
  right: 24px;
  z-index: 5;
}

/* ---------- 背景光斑（无动画、无模糊滤镜，仅柔和渐变圆，主题切换不会引起重绘抖动） ---------- */
.deco {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.deco-a {
  width: 560px;
  height: 560px;
  top: -180px;
  left: -140px;
  background: radial-gradient(closest-side, rgba(91, 108, 255, 0.18), transparent 72%);
}
.deco-b {
  width: 640px;
  height: 640px;
  bottom: -220px;
  right: -180px;
  background: radial-gradient(closest-side, rgba(139, 92, 246, 0.16), transparent 72%);
}
.deco-c {
  width: 320px;
  height: 320px;
  top: 4%;
  right: 10%;
  background: radial-gradient(closest-side, rgba(79, 172, 254, 0.1), transparent 72%);
}
:global(html.dark) .deco-a {
  background: radial-gradient(closest-side, rgba(106, 122, 255, 0.22), transparent 72%);
}
:global(html.dark) .deco-b {
  background: radial-gradient(closest-side, rgba(155, 107, 250, 0.2), transparent 72%);
}
:global(html.dark) .deco-c {
  background: radial-gradient(closest-side, rgba(56, 189, 248, 0.12), transparent 72%);
}

.login-wrap {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ---------- 品牌标识：同 DefaultLayout 侧边栏（渐变 badge + 渐变文字） ---------- */
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  animation: rise 0.5s ease-out both;
}
.logo-badge {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--brand-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(91, 108, 255, 0.28);
}
.logo-text {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.3px;
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.brand-sub {
  margin-top: 12px;
  font-size: 13px;
  letter-spacing: 1px;
  color: var(--text-tertiary);
  animation: rise 0.5s ease-out 0.06s both;
}

/* ---------- 登录卡片：全局卡片体系（hover-card 同源） ---------- */
.login-card {
  width: 420px;
  max-width: 100%;
  margin-top: 36px;
  padding: 38px 40px 30px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-card);
  animation: rise 0.5s ease-out 0.12s both;
}
.card-head {
  margin-bottom: 26px;
}
.card-head h2 {
  font-size: 22px;
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
  margin-top: 2px;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
}
.tip {
  margin-top: 18px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 520px) {
  .login-page {
    padding: 24px 16px;
  }
  .login-card {
    padding: 28px 22px 20px;
  }
  .deco {
    display: none;
  }
}
</style>
