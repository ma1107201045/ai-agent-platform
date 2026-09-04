<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userSecurityApi, type SysProfile } from '@/api/user-security'

const loading = ref(false)
const profile = ref<SysProfile | null>(null)

const profileForm = reactive({ nickname: '', email: '', phone: '', avatar: '' })
const savingProfile = ref(false)
async function load() {
  loading.value = true
  try {
    const p = await userSecurityApi.get()
    profile.value = p
    profileForm.nickname = p.nickname || ''
    profileForm.email = p.email || ''
    profileForm.phone = p.phone || ''
    profileForm.avatar = p.avatar || ''
  } finally {
    loading.value = false
  }
}
async function saveProfile() {
  if (!profileForm.nickname.trim()) return ElMessage.warning('昵称不能为空')
  savingProfile.value = true
  try {
    await userSecurityApi.update({
      nickname: profileForm.nickname.trim(),
      email: profileForm.email.trim() || undefined,
      phone: profileForm.phone.trim() || undefined,
      avatar: profileForm.avatar.trim() || undefined
    })
    ElMessage.success('资料已保存')
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    savingProfile.value = false
  }
}
function avatarText() {
  const name = profileForm.nickname || profile.value?.username || 'U'
  return name.charAt(0).toUpperCase()
}

/* 密码 */
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const savingPwd = ref(false)
async function changePwd() {
  if (!pwdForm.oldPassword) return ElMessage.warning('请输入当前密码')
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (pwdForm.newPassword !== pwdForm.confirm) return ElMessage.warning('两次输入的新密码不一致')
  savingPwd.value = true
  try {
    await userSecurityApi.changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirm = ''
  } catch (e) {
    ElMessage.error((e as Error).message || '修改失败')
  } finally {
    savingPwd.value = false
  }
}

/* MFA 绑定 */
const mfaVisible = ref(false)
const mfaStep = ref(1)
const mfaPassword = ref('')
const mfaSecret = ref('')
const mfaUrl = ref('')
const mfaCode = ref('')
const mfaBusy = ref(false)

async function bindMfa() {
  mfaStep.value = 1
  mfaPassword.value = ''
  mfaCode.value = ''
  mfaVisible.value = true
}
async function mfaStep1() {
  if (!mfaPassword.value) return ElMessage.warning('请输入当前密码')
  mfaBusy.value = true
  try {
    const data = await userSecurityApi.mfaInit({ password: mfaPassword.value })
    mfaSecret.value = data.secret
    mfaUrl.value = data.otpauthUrl
    mfaStep.value = 2
  } catch (e) {
    ElMessage.error((e as Error).message || '初始化失败')
  } finally {
    mfaBusy.value = false
  }
}
async function mfaStep2() {
  if (!mfaCode.value.trim()) return ElMessage.warning('请输入动态口令')
  mfaBusy.value = true
  try {
    await userSecurityApi.mfaConfirm({ code: mfaCode.value.trim() })
    ElMessage.success('MFA 已开启')
    mfaVisible.value = false
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '校验失败')
  } finally {
    mfaBusy.value = false
  }
}
async function copySecret() {
  try {
    await navigator.clipboard.writeText(mfaSecret.value)
    ElMessage.success('密钥已复制')
  } catch {
    ElMessage.info(`请手动复制：${mfaSecret.value}`)
  }
}

/* MFA 关闭 */
const disableVisible = ref(false)
const disableForm = reactive({ password: '', code: '' })
const disabling = ref(false)
async function disableMfa() {
  if (!disableForm.password) return ElMessage.warning('请输入当前密码')
  if (!disableForm.code.trim()) return ElMessage.warning('请输入动态口令')
  disabling.value = true
  try {
    await userSecurityApi.mfaDisable({ password: disableForm.password, code: disableForm.code.trim() })
    ElMessage.success('MFA 已关闭')
    disableVisible.value = false
    disableForm.password = ''
    disableForm.code = ''
    load()
  } catch (e) {
    ElMessage.error((e as Error).message || '关闭失败')
  } finally {
    disabling.value = false
  }
}

function fmt(s?: string) {
  if (!s) return '-'
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(load)
</script>

<template>
  <div class="page-container security-page">
    <div class="security-head">
      <h2 class="head-title">账号与安全</h2>
      <p class="head-desc">管理个人资料、登录密码与安全验证</p>
    </div>

    <div v-loading="loading" class="content-grid">
      <!-- 左侧：个人资料 -->
      <div class="grid-main">
        <el-card shadow="never" class="card">
          <div class="card-title">个人资料</div>
          <div class="profile-row">
            <div class="avatar-big">
              <span>{{ avatarText() }}</span>
            </div>
            <div class="profile-meta">
              <div class="profile-name">{{ profileForm.nickname || profile?.username }}</div>
              <div class="dim-text">账号：{{ profile?.username }} · ID：{{ profile?.id }}</div>
            </div>
          </div>
          <div class="profile-form">
            <div class="form-grid">
              <div class="field-group">
                <label>昵称</label>
                <el-input v-model="profileForm.nickname" maxlength="64" />
              </div>
              <div class="field-group">
                <label>邮箱</label>
                <el-input v-model="profileForm.email" placeholder="user@example.com" />
              </div>
              <div class="field-group">
                <label>手机号</label>
                <el-input v-model="profileForm.phone" placeholder="选填" maxlength="32" />
              </div>
              <div class="field-group">
                <label>头像地址</label>
                <el-input v-model="profileForm.avatar" placeholder="头像图片 URL（选填）" />
              </div>
            </div>
            <el-button type="primary" class="btn-gradient" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="card">
          <div class="card-title">登录安全</div>
          <div class="info-line">
            <span class="info-label">最近登录时间</span>
            <span class="dim-text">{{ fmt(profile?.lastLoginAt) }}</span>
          </div>
          <div class="info-line">
            <span class="info-label">最近登录 IP</span>
            <span class="dim-text">{{ profile?.lastLoginIp || '-' }}</span>
          </div>
          <div class="info-line">
            <span class="info-label">累计登录次数</span>
            <span class="dim-text">{{ profile?.loginCount ?? 0 }} 次</span>
          </div>
        </el-card>
      </div>

      <!-- 右侧：密码 / MFA -->
      <div class="grid-side">
        <el-card shadow="never" class="card">
          <div class="card-title">修改密码</div>
          <p class="dim-text" style="margin: 0 0 14px">建议定期更换密码，避免使用弱口令。</p>
          <div class="side-form">
            <div class="field-group">
              <label>当前密码</label>
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </div>
            <div class="field-group">
              <label>新密码</label>
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
            </div>
            <div class="field-group">
              <label>确认新密码</label>
              <el-input v-model="pwdForm.confirm" type="password" show-password placeholder="再次输入新密码" />
            </div>
            <el-button type="primary" class="btn-gradient full" :loading="savingPwd" @click="changePwd">确认修改</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="card">
          <div class="card-title">MFA 二次验证</div>
          <p class="dim-text" style="margin: 0 0 14px">开启后，登录时除密码外还需输入动态口令（Authenticator / 微信小程序 OTP）。</p>
          <div class="mfa-status">
            <div class="mfa-state">
              <span class="state-dot" :class="profile?.mfaEnabled === 1 ? 'on' : 'off'"></span>
              <span>{{ profile?.mfaEnabled === 1 ? '已开启' : '未开启' }}</span>
            </div>
            <span v-if="profile?.mfaEnabled === 1" class="dim-text">绑定于 {{ fmt(profile?.mfaBoundAt) }}</span>
          </div>
          <el-button v-if="profile?.mfaEnabled === 1" class="full" @click="disableVisible = true">关闭 MFA</el-button>
          <el-button v-else type="primary" class="btn-gradient full" @click="bindMfa">绑定 MFA</el-button>
        </el-card>
      </div>
    </div>

    <!-- MFA 绑定流程 -->
    <el-dialog v-model="mfaVisible" title="绑定 MFA 二次验证" width="520px" :close-on-click-modal="false" @closed="mfaStep = 1">
      <template v-if="mfaStep === 1">
        <p class="form-tip">绑定前需验证当前登录密码。</p>
        <el-input v-model="mfaPassword" type="password" show-password placeholder="请输入当前密码" @keyup.enter="mfaStep1" />
      </template>
      <template v-else>
        <p class="form-tip">1. 使用 Google Authenticator 等应用“手动输入密钥”方式添加账号：</p>
        <div class="secret-row">
          <code class="secret-code">{{ mfaSecret }}</code>
          <el-button size="small" @click="copySecret">复制密钥</el-button>
        </div>
        <p class="form-tip">2. 输入应用生成的 6 位动态口令完成绑定：</p>
        <el-input v-model="mfaCode" placeholder="6 位动态口令" maxlength="6" @keyup.enter="mfaStep2" />
        <el-alert type="warning" :closable="false" style="margin-top: 12px" title="请妥善保存密钥" description="开启后每次登录都需动态口令，若手机丢失且无备份将无法登录。" />
      </template>
      <template #footer>
        <el-button @click="mfaVisible = false">取消</el-button>
        <el-button v-if="mfaStep === 1" type="primary" :loading="mfaBusy" @click="mfaStep1">下一步</el-button>
        <el-button v-else type="primary" :loading="mfaBusy" @click="mfaStep2">确认绑定</el-button>
      </template>
    </el-dialog>

    <!-- MFA 关闭 -->
    <el-dialog v-model="disableVisible" title="关闭 MFA 二次验证" width="460px" :close-on-click-modal="false">
      <p class="form-tip">关闭 MFA 后，登录将不再要求动态口令，安全性降低。请输入当前密码与动态口令确认。</p>
      <div class="side-form">
        <el-input v-model="disableForm.password" type="password" show-password placeholder="当前密码" />
        <el-input v-model="disableForm.code" placeholder="6 位动态口令" maxlength="6" />
      </div>
      <template #footer>
        <el-button @click="disableVisible = false">取消</el-button>
        <el-button type="danger" :loading="disabling" @click="disableMfa">关闭 MFA</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.security-page { max-width: 1280px; margin: 0 auto; }
.security-head { margin-bottom: 20px; }
.head-title { font-size: 22px; font-weight: 700; }
.head-desc { margin-top: 4px; font-size: 13px; color: var(--text-tertiary); }
.content-grid { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); gap: 18px; align-items: start; }
.grid-main, .grid-side { display: flex; flex-direction: column; gap: 18px; }
.card { border-radius: var(--radius-lg); }
.card-title { font-size: 15px; font-weight: 700; margin-bottom: 16px; }
.profile-row { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
.avatar-big { width: 64px; height: 64px; border-radius: 50%; background: var(--brand-gradient); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 700; }
.profile-meta .profile-name { font-size: 18px; font-weight: 700; }
.profile-form { display: flex; flex-direction: column; gap: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.field-group { display: flex; flex-direction: column; gap: 6px; }
.field-group label { font-size: 12.5px; font-weight: 600; color: var(--text-secondary); }
.info-line { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px dashed var(--border-color); font-size: 13px; }
.info-label { color: var(--text-secondary); }
.side-form { display: flex; flex-direction: column; gap: 12px; }
.full { width: 100%; }
.dim-text { color: var(--text-tertiary); font-size: 12.5px; }
.mfa-status { display: flex; align-items: center; justify-content: space-between; padding: 10px 0 16px; }
.mfa-state { display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 600; }
.state-dot { width: 10px; height: 10px; border-radius: 50%; }
.state-dot.on { background: #67c23a; box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.18); }
.state-dot.off { background: var(--text-tertiary); }
.form-tip { font-size: 13px; color: var(--text-secondary); line-height: 1.7; margin: 0 0 12px; }
.secret-row { display: flex; align-items: center; gap: 10px; margin: 4px 0 16px; }
.secret-code { flex: 1; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 13px; background: var(--fill-light); padding: 8px 10px; border-radius: 6px; word-break: break-all; user-select: all; }
@media (max-width: 1100px) {
  .content-grid { grid-template-columns: 1fr; }
}
</style>
