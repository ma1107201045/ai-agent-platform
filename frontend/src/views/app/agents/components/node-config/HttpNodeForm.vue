<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import VarChips from '../VarChips.vue'
import type { VarItem } from '@/utils/flow'

/** HTTP 请求节点：地址 / 方法 / Headers / 鉴权 / 请求体 / 响应处理 */
const props = defineProps<{ cfg: Record<string, any>; vars: VarItem[] }>()

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']

// ---------- HTTP Headers（JSON 文本 <-> 对象） ----------
const headersText = ref('')

function syncHeadersText() {
  const h = props.cfg?.headers
  headersText.value = h && typeof h === 'object' ? JSON.stringify(h, null, 2) : ''
}

function parseHeaders() {
  const t = headersText.value.trim()
  if (!t) {
    delete props.cfg.headers
    return
  }
  try {
    const parsed = JSON.parse(t)
    if (typeof parsed !== 'object' || Array.isArray(parsed) || parsed === null) {
      ElMessage.warning('Headers 必须是 JSON 对象')
      return
    }
    props.cfg.headers = parsed
  } catch {
    ElMessage.warning('Headers 必须是合法 JSON 对象')
  }
}

// 切换节点时（cfg 对象被整体替换）重新同步 headers 文本
watch(() => props.cfg, syncHeadersText, { immediate: true })
</script>

<template>
  <el-form-item label="请求地址">
    <el-input v-model="cfg.url" placeholder="https://api.example.com/path（支持 {{input}} 变量）" />
    <VarChips :vars="vars" :target="cfg" field="url" />
  </el-form-item>
  <el-form-item label="请求方式">
    <el-select v-model="cfg.method" style="width: 100%">
      <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
    </el-select>
  </el-form-item>
  <el-form-item label="自定义 Headers">
    <el-input
      v-model="headersText"
      type="textarea"
      :rows="3"
      placeholder='JSON 格式，如 {"X-Api-Key":"xxx"}，值支持 {{input}} 变量'
      @blur="parseHeaders"
    />
  </el-form-item>

  <el-divider content-position="left">鉴权</el-divider>
  <el-form-item label="鉴权方式">
    <el-select v-model="cfg.authType" style="width: 100%">
      <el-option label="无" value="none" />
      <el-option label="Bearer Token" value="bearer" />
      <el-option label="Basic" value="basic" />
    </el-select>
  </el-form-item>
  <el-form-item v-if="cfg.authType === 'bearer'" label="Token">
    <el-input v-model="cfg.authToken" placeholder="Bearer Token" />
  </el-form-item>
  <template v-if="cfg.authType === 'basic'">
    <el-form-item label="用户名">
      <el-input v-model="cfg.authUsername" />
    </el-form-item>
    <el-form-item label="密码">
      <el-input v-model="cfg.authPassword" type="password" show-password />
    </el-form-item>
  </template>

  <el-divider content-position="left">请求体</el-divider>
  <el-form-item label="请求体类型">
    <el-select v-model="cfg.bodyType" style="width: 100%">
      <el-option label="无（GET/DELETE 常用）" value="none" />
      <el-option label="JSON" value="json" />
      <el-option label="表单 form-urlencoded" value="form" />
      <el-option label="纯文本 raw" value="raw" />
    </el-select>
  </el-form-item>
  <el-form-item v-if="cfg.bodyType !== 'none'" label="请求体模板">
    <el-input
      v-model="cfg.bodyTemplate"
      type="textarea"
      :rows="5"
      :placeholder="
        cfg.bodyType === 'form'
          ? 'JSON 对象或 k1=v1&k2=v2 形式，支持变量'
          : '请求体内容，支持变量插值'
      "
    />
    <VarChips :vars="vars" :target="cfg" field="bodyTemplate" />
  </el-form-item>

  <el-divider content-position="left">响应处理</el-divider>
  <el-form-item label="响应类型">
    <el-select v-model="cfg.responseType" style="width: 100%">
      <el-option label="纯文本（原样输出）" value="text" />
      <el-option label="JSON（可抽取字段）" value="json" />
    </el-select>
  </el-form-item>
  <el-form-item v-if="cfg.responseType === 'json'" label="字段路径">
    <el-input v-model="cfg.jsonPath" placeholder="如 data.choices[0].text；留空返回完整 JSON" />
  </el-form-item>
  <el-form-item label="忽略非 2xx 状态码">
    <el-switch v-model="cfg.ignoreStatus" />
    <span class="field-tip inline">开启后即使返回 4xx/5xx 也作为正常输出继续流程</span>
  </el-form-item>
</template>

<style scoped>
.field-tip {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
.field-tip.inline {
  margin-left: 8px;
}
</style>
