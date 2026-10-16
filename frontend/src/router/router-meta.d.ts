/**
 * 路由 meta 类型扩展（模块增强必须放在独立的 module 文件中，
 * 不能写进全局 env.d.ts，否则会整体覆盖 vue-router 的类型）。
 */
import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题（拼接进 document.title） */
    title?: string
    /** 是否在侧边栏菜单中隐藏（详情页等） */
    hidden?: boolean
    /** 允许访问的角色编码（为空表示登录即可访问；权限控制落地后由路由守卫校验） */
    roles?: string[]
  }
}

export {}
