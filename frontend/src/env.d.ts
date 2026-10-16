/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 前端 axios baseURL（默认 /api） */
  readonly VITE_API_BASE?: string
  /** 开发环境后端地址（dev server 代理目标） */
  readonly VITE_API_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
