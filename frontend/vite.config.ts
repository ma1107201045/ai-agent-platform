import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, '')
  return {
    plugins: [
      vue(),
      // Element Plus 按需引入：模板组件与 API（ElMessage 等）自动导入并附带样式
      AutoImport({ resolvers: [ElementPlusResolver()], dts: 'src/auto-imports.d.ts' }),
      // 注意：不开 components.d.ts——全局组件类型会让 el-table 插槽 row 等存量松散写法
      // 全部变成类型错误（127 处），与改造前「运行时全局注册、模板不做组件类型检查」保持同一水位
      Components({ resolvers: [ElementPlusResolver()], dts: false })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173,
      proxy: {
        [env.VITE_API_BASE || '/api']: {
          target: env.VITE_API_TARGET || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
