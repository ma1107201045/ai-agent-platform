<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { appAgentApi } from '@/api/app-agent'
import type { AppAgent } from '@/api/types'

const base = location.origin + '/api'

// ---------------- 发布应用列表 ----------------
const apps = ref<AppAgent[]>([])
const selectedApp = ref<number | null>(null)
const loadingApps = ref(false)
const appInput = ref('你好，介绍一下你自己')
const apiKey = ref('')
const testing = ref(false)
const testResp = ref<{ answer?: string; detail?: unknown } | null>(null)
const testErr = ref('')

async function loadApps() {
  loadingApps.value = true
  try {
    const data = await appAgentApi.page({ page: 1, size: 500 })
    apps.value = data.records.filter((a) => a.status === 1)
    if (apps.value.length) selectedApp.value = apps.value[0].id
  } finally {
    loadingApps.value = false
  }
}
const currentApp = computed(() => apps.value.find((a) => a.id === selectedApp.value))

// ---------------- 代码示例 ----------------
const snippets = computed(() => {
  const id = selectedApp.value ?? '{APP_ID}'
  const keyHeader = apiKey.value.trim()
    ? `  "Authorization": "Bearer ${apiKey.value.trim()}",\n`
    : ''
  const body = JSON.stringify({ messages: [{ role: 'user', content: appInput.value || '你好' }] }, null, 2)

  const curl = `curl -X POST '${base}/portal/public/app-agents/${id}/chat' \\
${keyHeader ? `  -H 'Authorization: Bearer ${apiKey.value.trim()}' \\\n` : ''}  -H 'Content-Type: application/json' \\
  -d '${body.replace(/\n/g, '\\n')}'`

  const node = `// 需要 Node 18+ 环境
const API_KEY = '${apiKey.value.trim() || 'YOUR_API_KEY'}';

const resp = await fetch('${base}/portal/public/app-agents/${id}/chat', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    ...(API_KEY && API_KEY !== 'YOUR_API_KEY'
      ? { Authorization: 'Bearer ' + API_KEY }
      : {})
  },
  body: JSON.stringify({
    messages: [{ role: 'user', content: '${appInput.value || '你好'}' }]
  })
});
const data = await resp.json();
console.log(data.data.answer); // 平台统一返回结构 {code,message,data}`

  const python = `# 需要 requests 库：pip install requests
import requests

API_KEY = '${apiKey.value.trim() || 'YOUR_API_KEY'}'
url = '${base}/portal/public/app-agents/${id}/chat'
headers = {'Content-Type': 'application/json'}
if API_KEY and API_KEY != 'YOUR_API_KEY':
    headers['Authorization'] = 'Bearer ' + API_KEY

payload = {
    'messages': [{'role': 'user', 'content': '${appInput.value || '你好'}'}]
}
resp = requests.post(url, json=payload, headers=headers)
print(resp.json()['data']['answer'])`

  const java = `// 使用 okhttp3
OkHttpClient client = new OkHttpClient();
String json = """
${body.replace(/"/g, '\\"').split('\n').join('\n')}
""";
Request request = new Request.Builder()
        .url("${base}/portal/public/app-agents/${id}/chat")
        .addHeader("Content-Type", "application/json")
        ${keyHeader ? `.addHeader("Authorization", "Bearer ${apiKey.value.trim()}")` : ''}
        .post(RequestBody.create(json, MediaType.parse("application/json; charset=utf-8")))
        .build();
try (Response response = client.newCall(request).execute()) {
    System.out.println(response.body().string());
}`

  return [
    { lang: 'cURL', langIcon: '>_', code: curl },
    { lang: 'Node.js', langIcon: 'JS', code: node },
    { lang: 'Python', langIcon: 'Py', code: python },
    { lang: 'Java', langIcon: 'Jv', code: java }
  ]
})
const activeSnippet = ref(0)

// ---------------- 在线调试 ----------------
async function run() {
  if (!selectedApp.value) return ElMessage.warning('请先选择一个应用')
  testing.value = true
  testErr.value = ''
  testResp.value = null
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const key = apiKey.value.trim()
  if (key) headers.Authorization = `Bearer ${key}`
  const token = localStorage.getItem('agent_platform_token')
  if (token && !key) headers.Authorization = `Bearer ${token}`
  try {
    const resp = await fetch(`${base}/portal/public/app-agents/${selectedApp.value}/chat`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ messages: [{ role: 'user', content: appInput.value || '你好' }] })
    })
    const raw = await resp.json()
    if (!resp.ok || raw.code !== 0) {
      testErr.value = JSON.stringify(raw, null, 2)
    } else {
      testResp.value = raw.data
    }
  } catch (e) {
    testErr.value = String(e)
  } finally {
    testing.value = false
  }
}

function copyCode(code: string) {
  navigator.clipboard?.writeText(code).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.error('复制失败')
  )
}

onMounted(loadApps)
</script>

<template>
  <div class="page-container docs-page">
    <div class="docs-head">
      <div>
        <h2 class="head-title">API 文档</h2>
        <p class="head-desc">基于 HTTP 将已发布应用接入你的网站 / 公众号 / 小程序等任意系统</p>
      </div>
    </div>

    <el-card shadow="never" class="doc-card">
      <!-- 应用选择 -->
      <div class="doc-section">
        <div class="section-title">1 · 选择应用并生成调用凭证</div>
        <div v-loading="loadingApps" class="choose-row">
          <el-select v-model="selectedApp" style="width: 320px" filterable placeholder="选择已发布应用" :loading="loadingApps">
            <el-option v-for="a in apps" :key="a.id" :label="`${a.name}（${a.type}）`" :value="a.id" />
          </el-select>
          <div class="api-key-input">
            <span class="muted">API Key（可选）：</span>
            <el-input v-model="apiKey" placeholder="留空则匿名调用；生产环境请使用应用 API 密钥" clearable style="flex: 1" />
          </div>
        </div>
        <div v-if="currentApp" class="endpoint-box">
          <div class="ep-row">
            <el-tag type="success" size="small" effect="dark">GET</el-tag>
            <code class="mono ep-url">{{ base }}/portal/public/app-agents/{{ currentApp.id }}</code>
            <span class="muted"> · 获取应用信息（开场白/推荐问题）</span>
          </div>
          <div class="ep-row">
            <el-tag type="danger" size="small" effect="dark">POST</el-tag>
            <code class="mono ep-url">{{ base }}/portal/public/app-agents/{{ currentApp.id }}/chat</code>
            <span class="muted"> · 发送对话</span>
          </div>
        </div>
      </div>

      <!-- 协议说明 -->
      <div class="doc-section">
        <div class="section-title">2 · 调用说明</div>
        <div class="rules">
          <div class="rule">
            <b>鉴权</b>
            <p>接口默认<b>免鉴权</b>开放（适合官网嵌入/临时演示）。生产环境建议在「应用 API 密钥」创建密钥后，通过
              <code>Authorization: Bearer &lt;api-key&gt;</code> 或 <code>X-API-Key: &lt;api-key&gt;</code> 请求头携带，支持启用 / 过期 / 限流控制。</p>
          </div>
          <div class="rule">
            <b>请求体</b>
            <p><code>messages</code> 为完整对话历史数组，每条为 <code>{role, content}</code>（role 取值 user / assistant），
              <b>最后一条必须是 user 消息</b>，作为本次输入；之前的消息作为上下文。</p>
          </div>
          <div class="rule">
            <b>响应结构</b>
            <p>平台统一包装：<code>{code: 0, message: "ok", data: {...}}</code>。<code>data.answer</code> 为最终回复；
              <code>data.detail</code> 为执行细节（workflow 为节点轨迹 / agent 为工具调用步骤 / chatflow 为 null）。</p>
          </div>
          <div class="rule">
            <b>行为</b>
            <p>仅<b>已发布</b>应用可被外部调用。workflow 执行最新发布版本 DSL；agent 具备工具调用能力；chatflow 直连默认对话模型。</p>
          </div>
        </div>
      </div>

      <!-- 代码示例 -->
      <div class="doc-section">
        <div class="section-title">3 · 代码示例</div>
        <div class="snippet-tabs">
          <button
            v-for="(s, i) in snippets"
            :key="s.lang"
            class="snippet-tab"
            :class="{ active: activeSnippet === i }"
            @click="activeSnippet = i"
          >
            <span class="snippet-icon">{{ s.langIcon }}</span>{{ s.lang }}
          </button>
        </div>
        <div class="code-block">
          <pre><code>{{ snippets[activeSnippet].code }}</code></pre>
          <el-button class="copy-btn" size="small" @click="copyCode(snippets[activeSnippet].code)">
            <el-icon style="margin-right: 3px"><CopyDocument /></el-icon>复制
          </el-button>
        </div>
      </div>

      <!-- 在线调试 -->
      <div class="doc-section">
        <div class="section-title">4 · 在线调试</div>
        <el-input v-model="appInput" type="textarea" :rows="2" placeholder="输入一条测试消息" />
        <el-button type="primary" class="btn-gradient" style="margin-top: 10px" :loading="testing" @click="run">
          {{ testing ? '请求中…' : '发送请求' }}
        </el-button>
        <div v-if="testResp" class="resp-block">
          <div class="resp-title">返回结果</div>
          <pre class="mono">{{ testResp.answer }}</pre>
        </div>
        <div v-if="testErr" class="resp-block err">
          <div class="resp-title">调用失败</div>
          <pre class="mono">{{ testErr }}</pre>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.docs-page {
  max-width: 1080px;
  margin: 0 auto;
}
.docs-head {
  margin-bottom: 16px;
}
.head-title {
  font-size: 22px;
  font-weight: 700;
}
.head-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-tertiary);
}
.doc-card {
  border-radius: var(--radius-lg);
  padding: 6px 20px 20px;
}
.doc-section {
  padding: 18px 0;
  border-bottom: 1px dashed var(--border-color);
}
.doc-section:last-child {
  border-bottom: none;
}
.section-title {
  font-weight: 700;
  margin-bottom: 12px;
  font-size: 15px;
}
.choose-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.api-key-input {
  flex: 1;
  min-width: 340px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.endpoint-box {
  margin-top: 12px;
  background: var(--fill-lighter);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ep-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ep-url {
  color: var(--brand);
  font-size: 13px;
}
.rules {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rule b {
  color: var(--text-secondary);
}
.rule p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}
.rule code,
.endpoint-box code,
.muted code {
  background: var(--fill-lighter);
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 12px;
  color: var(--brand);
}
.snippet-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.snippet-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--fill-lighter);
  padding: 5px 12px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}
.snippet-tab.active {
  border-color: var(--brand);
  background: var(--brand);
  color: #fff;
}
.snippet-icon {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: rgba(127, 127, 127, 0.18);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
}
.snippet-tab.active .snippet-icon {
  background: rgba(255, 255, 255, 0.25);
}
.code-block {
  position: relative;
  background: #1e2430;
  color: #d7e0f2;
  border-radius: var(--radius-md);
  padding: 14px 14px 16px;
  overflow: auto;
}
.code-block pre {
  margin: 0;
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.2);
  color: #fff;
}
.resp-block {
  margin-top: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  background: var(--fill-lighter);
}
.resp-block.err {
  border-color: var(--el-color-danger-light-7);
}
.resp-title {
  font-weight: 700;
  margin-bottom: 6px;
  font-size: 13px;
}
.resp-block pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}
.mono {
  font-family: 'JetBrains Mono', 'Consolas', monospace;
}
.muted {
  color: var(--text-tertiary);
  font-size: 12.5px;
}
</style>
