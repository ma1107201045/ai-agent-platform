import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}']
  },
  {
    name: 'app/files-to-ignore',
    ignores: [
      '**/dist/**',
      '**/dist-ssr/**',
      '**/coverage/**',
      '**/node_modules/**',
      '**/*.d.ts',
      '**/*.tsbuildinfo'
    ]
  },
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  skipFormatting,
  {
    rules: {
      // 现有大量 index.vue / edit.vue 单词组件名，先关闭
      'vue/multi-word-component-names': 'off',
      // 存量 any 较多，先 warn 不阻塞
      '@typescript-eslint/no-explicit-any': 'warn',
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      // Vue Flow 编排场景下父组件传入的 node/edges 为响应式数据，面板直接双向编辑是刻意设计
      'vue/no-mutating-props': 'off',
      // 动态组件 <component :is="`h${level}`"> 渲染标题属于合法用法
      'vue/no-v-text-v-html-on-component': 'off'
    }
  }
)
