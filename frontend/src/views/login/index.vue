<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatLineSquare, Collection, Cpu, Right } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import ThemeSwitch from '@/components/ThemeSwitch.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({
  username: '',
  password: ''
})

async function handleLogin() {
  if (loading.value) return
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
  <main class="login-page">
    <section class="studio">
      <div class="brand"><img src="/favicon.svg" alt="" width="34" height="34" />AgentForge</div>
      <div class="studio-body">
        <h2>智能体开发工作台</h2>
        <figure class="blueprint">
          <figcaption><span>知识库问答</span><small>对话流示例</small></figcaption>
          <ol class="flow">
            <li>
              <el-icon><ChatLineSquare /></el-icon><span>用户提问</span>
            </li>
            <li>
              <el-icon><Collection /></el-icon><span>知识库检索</span>
            </li>
            <li>
              <el-icon><Cpu /></el-icon><span>生成回答</span>
            </li>
          </ol>
        </figure>
      </div>
    </section>
    <section class="entry">
      <div class="theme-corner"><ThemeSwitch /></div>
      <div class="login-form">
        <h1>登录工作台</h1>
        <el-form label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="用户名">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="输入用户名"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              placeholder="输入密码"
              autocomplete="current-password"
              show-password
            />
          </el-form-item>
          <el-button
            class="submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="loading"
          >
            {{ loading ? '正在登录…' : '登录' }}<el-icon v-if="!loading"><Right /></el-icon>
          </el-button>
        </el-form>
        <details class="account-note">
          <summary>首次登录</summary>
          <p>默认用户名和密码均为 <code>admin</code>。登录后可在「账号与安全」中修改密码。</p>
        </details>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  min-height: 100dvh;
  background: var(--bg-card);
}
.studio {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--bg-aside);
  color: var(--text-primary);
  border-inline-end: 1px solid var(--border-color);
  padding: clamp(30px, 4.5vw, 76px);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 21px;
  font-weight: 650;
  letter-spacing: -0.7px;
}
.studio-body {
  width: 100%;
  max-width: 520px;
  margin: auto;
  padding: 54px 0;
}
.studio-body h2 {
  font-size: clamp(27px, 3vw, 46px);
  font-weight: 580;
  letter-spacing: -0.05em;
  margin-bottom: 28px;
}
.blueprint {
  border-block: 1px solid var(--border-color);
}
figcaption {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 16px 0;
  font-size: 14px;
}
figcaption small {
  font-size: 12px;
  color: var(--text-secondary);
}
.flow {
  list-style: none;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  row-gap: 32px;
  padding: 26px 6px 44px;
  background-image: radial-gradient(var(--canvas-dot) 1px, transparent 1px);
  background-size: 14px 14px;
}
.flow li {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--bg-card);
  border: 1px solid var(--flow-edge);
  border-radius: 5px;
  min-height: 48px;
  font-size: 13px;
  white-space: nowrap;
}
.flow li:nth-child(2) {
  grid-column: 2;
  grid-row: 2;
  background: var(--accent-surface);
  border-color: var(--text-secondary);
  font-weight: 600;
}
.flow li:nth-child(3) {
  grid-column: 3;
  grid-row: 3;
}
.flow li:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 100%;
  width: calc(100% + 1px);
  height: 32px;
  --connector: linear-gradient(var(--text-tertiary), var(--text-tertiary));
  background:
    var(--connector) left top / 1px 50%,
    var(--connector) center / 100% 1px,
    var(--connector) right bottom / 1px 50%;
  background-repeat: no-repeat;
}
.entry {
  position: relative;
  display: grid;
  place-items: center;
  padding: 80px clamp(30px, 6vw, 108px);
}
.theme-corner {
  position: absolute;
  right: 28px;
  top: 22px;
}
.login-form {
  width: 100%;
  max-width: 360px;
}
.login-form h1 {
  font-size: 32px;
  font-weight: 620;
  letter-spacing: -0.055em;
  margin-bottom: 34px;
}
.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}
.login-form :deep(.el-form-item__label) {
  font-weight: 560;
  color: var(--text-primary);
}
.login-form :deep(.el-input__wrapper) {
  min-height: 44px;
}
.submit {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  font-weight: 600;
}
.submit .el-icon {
  margin-left: 10px;
}
.account-note {
  margin-top: 26px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
  font-size: 13px;
  color: var(--text-secondary);
}
.account-note summary {
  cursor: pointer;
  width: fit-content;
}
.account-note p {
  margin-top: 10px;
  line-height: 1.7;
}
@media (max-width: 760px) {
  .login-page {
    display: flex;
    flex-direction: column;
  }
  .studio {
    padding: 20px 24px;
    border-inline-end: 0;
    border-block-end: 1px solid var(--border-color);
  }
  .studio-body {
    display: none;
  }
  .entry {
    flex: 1;
    align-items: start;
    padding: clamp(65px, 14vh, 110px) 26px 50px;
  }
  .theme-corner {
    top: 14px;
    right: 18px;
  }
}
</style>
