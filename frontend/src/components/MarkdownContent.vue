<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{ content: string }>()

/* ---------------- 块级解析 ---------------- */
type Block =
  | { type: 'code'; lang: string; code: string }
  | { type: 'h'; level: number; text: string }
  | { type: 'quote'; text: string }
  | { type: 'ul'; items: string[] }
  | { type: 'ol'; items: string[] }
  | { type: 'table'; head: string[]; rows: string[][] }
  | { type: 'hr' }
  | { type: 'p'; text: string }

function parseBlocks(md: string): Block[] {
  const lines = md.replace(/\r\n/g, '\n').split('\n')
  const blocks: Block[] = []
  let i = 0

  const pushP = (text: string) => {
    if (!text.trim()) return
    const last = blocks[blocks.length - 1]
    if (last && last.type === 'p') last.text += ' ' + text.trim()
    else blocks.push({ type: 'p', text: text.trim() })
  }

  while (i < lines.length) {
    const line = lines[i]

    // 代码块
    const codeMatch = line.match(/^```([\w+-]*)\s*$/)
    if (codeMatch) {
      const lang = codeMatch[1] || ''
      const buf: string[] = []
      i++
      while (i < lines.length && !/^```\s*$/.test(lines[i])) {
        buf.push(lines[i])
        i++
      }
      i++ // 跳过结束 ```
      blocks.push({ type: 'code', lang, code: buf.join('\n') })
      continue
    }

    // 标题
    const hMatch = line.match(/^(#{1,6})\s+(.*)$/)
    if (hMatch) {
      blocks.push({ type: 'h', level: hMatch[1].length, text: hMatch[2].trim() })
      i++
      continue
    }

    // 分隔线
    if (/^\s*([-*_])\1{2,}\s*$/.test(line)) {
      blocks.push({ type: 'hr' })
      i++
      continue
    }

    // 引用（连续行合并）
    if (/^\s*>\s?/.test(line)) {
      const buf: string[] = []
      while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
        buf.push(lines[i].replace(/^\s*>\s?/, ''))
        i++
      }
      blocks.push({ type: 'quote', text: buf.join(' ') })
      continue
    }

    // 表格
    if (/^\s*\|.*\|\s*$/.test(line) && /^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1] ?? '')) {
      const splitRow = (r: string) =>
        r.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim())
      const head = splitRow(line)
      i += 2 // 跳过表头和分隔行
      const rows: string[][] = []
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) {
        rows.push(splitRow(lines[i]))
        i++
      }
      blocks.push({ type: 'table', head, rows })
      continue
    }

    // 无序列表
    if (/^\s*[-*+]\s+/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*[-*+]\s+/, '').trim())
        i++
      }
      blocks.push({ type: 'ul', items })
      continue
    }

    // 有序列表
    if (/^\s*\d+[.、)]\s+/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\s*\d+[.、)]\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*\d+[.、)]\s+/, '').trim())
        i++
      }
      blocks.push({ type: 'ol', items })
      continue
    }

    // 空行
    if (!line.trim()) {
      i++
      continue
    }

    pushP(line)
    i++
  }

  return blocks
}

/* ---------------- 行内解析（先转义，防 XSS） ---------------- */
function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function inline(text: string): string {
  let s = escapeHtml(text)
  // 保护行内代码
  const codes: string[] = []
  s = s.replace(/`([^`\n]+)`/g, (_m, c: string) => {
    codes.push(c)
    return `\u0000${codes.length - 1}\u0000`
  })
  // 链接
  s = s.replace(
    /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener">$1</a>'
  )
  // 粗体
  s = s.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  // 斜体
  s = s.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
  // 还原行内代码
  s = s.replace(/\u0000(\d+)\u0000/g, (_m, i: string) => `<code>${codes[+i]}</code>`)
  return s
}

const blocks = computed<Block[]>(() => parseBlocks(props.content ?? ''))

/* ---------------- 复制代码 ---------------- */
const copiedLang = ref('')
async function copyCode(code: string, lang: string) {
  try {
    await navigator.clipboard.writeText(code)
    copiedLang.value = lang
    setTimeout(() => (copiedLang.value = ''), 1500)
  } catch {
    ElMessage.warning('复制失败')
  }
}
</script>

<template>
  <div class="md-content">
    <template v-for="(b, idx) in blocks" :key="idx">
      <div v-if="b.type === 'code'" class="md-code">
        <div class="md-code-head">
          <span class="md-code-lang">{{ b.lang || 'text' }}</span>
          <button class="md-code-copy" @click="copyCode(b.code, b.lang)">
            {{ copiedLang === b.lang ? '已复制' : '复制' }}
          </button>
        </div>
        <pre><code>{{ b.code }}</code></pre>
      </div>

      <component
        :is="`h${b.level}`"
        v-else-if="b.type === 'h'"
        class="md-h"
        :class="`md-h${b.level}`"
        v-html="inline(b.text)"
      />

      <blockquote v-else-if="b.type === 'quote'" class="md-quote" v-html="inline(b.text)" />

      <ul v-else-if="b.type === 'ul'" class="md-ul">
        <li v-for="(it, j) in b.items" :key="j" v-html="inline(it)" />
      </ul>

      <ol v-else-if="b.type === 'ol'" class="md-ol">
        <li v-for="(it, j) in b.items" :key="j" v-html="inline(it)" />
      </ol>

      <div v-else-if="b.type === 'table'" class="md-table-wrap">
        <table class="md-table">
          <thead>
            <tr>
              <th v-for="(c, j) in b.head" :key="j" v-html="inline(c)" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, j) in b.rows" :key="j">
              <td v-for="(c, k) in row" :key="k" v-html="inline(c)" />
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="b.type === 'hr'" class="md-hr" />

      <p v-else class="md-p" v-html="inline(b.text)" />
    </template>
  </div>
</template>

<style scoped>
.md-content {
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
  color: var(--el-text-color-primary, #303133);
}
.md-p {
  margin: 6px 0;
}
.md-h {
  margin: 12px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}
.md-h1 { font-size: 20px; }
.md-h2 { font-size: 18px; }
.md-h3 { font-size: 16px; }
.md-h4 { font-size: 15px; }
.md-h5, .md-h6 { font-size: 14px; }
.md-quote {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid var(--brand-1, #5b6cff);
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 0 8px 8px 0;
  color: var(--el-text-color-secondary, #606266);
}
.md-ul, .md-ol {
  margin: 6px 0;
  padding-left: 22px;
}
.md-ul li, .md-ol li {
  margin: 3px 0;
}
.md-table-wrap {
  margin: 8px 0;
  overflow-x: auto;
}
.md-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.md-table th,
.md-table td {
  border: 1px solid var(--el-border-color, #e4e7ed);
  padding: 6px 10px;
  text-align: left;
}
.md-table th {
  background: var(--el-fill-color-light, #f5f7fa);
  font-weight: 600;
}
.md-hr {
  margin: 12px 0;
  border-top: 1px dashed var(--el-border-color, #e4e7ed);
}
.md-code {
  margin: 8px 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: #1e2233;
}
.md-code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.06);
}
.md-code-lang {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.md-code-copy {
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.65);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 6px;
  transition: all 0.2s;
}
.md-code-copy:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}
.md-code pre {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  color: #e6e8f0;
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  line-height: 1.65;
}
.md-code pre code {
  background: none;
  padding: 0;
  color: inherit;
}
.md-content :deep(code) {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 0.9em;
  background: var(--el-fill-color-light, #f5f7fa);
  color: #c7254e;
  padding: 1px 5px;
  border-radius: 4px;
}
.md-content :deep(a) {
  color: var(--brand-1, #5b6cff);
  text-decoration: none;
}
.md-content :deep(a:hover) {
  text-decoration: underline;
}
.md-content :deep(strong) {
  font-weight: 600;
}
</style>
