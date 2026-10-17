import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElMessage } from 'element-plus'
// Element Plus 组件按需引入（见 vite.config.ts 的 unplugin 配置）；
// 这里只保留：基础样式 + 命令式 API（ElMessage 等被显式 import 时不会自动带样式）+ 暗色主题变量
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import {
  Aim,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Bell,
  BellFilled,
  ChatDotRound,
  Check,
  CircleCheck,
  CircleCloseFilled,
  Clock,
  Collection,
  Connection,
  CopyDocument,
  Cpu,
  DataAnalysis,
  Delete,
  Document,
  Download,
  Edit,
  EditPen,
  Expand,
  Files,
  Fold,
  FolderOpened,
  InfoFilled,
  Key,
  Link,
  Loading,
  Lock,
  MagicStick,
  Monitor,
  MoreFilled,
  Notebook,
  OfficeBuilding,
  Paperclip,
  Plus,
  Promotion,
  QuestionFilled,
  Rank,
  Refresh,
  RefreshLeft,
  Right,
  Search,
  Setting,
  SwitchButton,
  Tools,
  Upload,
  UploadFilled,
  User,
  VideoPause,
  VideoPlay,
  View,
  Warning,
  WarningFilled
} from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)

// 图标按需注册：仅注册模板中以 <IconName /> 简写使用、未在 script 显式 import 的图标。
// 新增模板图标简写时需在此补充（或改为在组件内显式 import）。
const templateIcons = {
  Aim,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Bell,
  BellFilled,
  ChatDotRound,
  Check,
  CircleCheck,
  CircleCloseFilled,
  Clock,
  Collection,
  Connection,
  CopyDocument,
  Cpu,
  DataAnalysis,
  Delete,
  Document,
  Download,
  Edit,
  EditPen,
  Expand,
  Files,
  Fold,
  FolderOpened,
  InfoFilled,
  Key,
  Link,
  Loading,
  Lock,
  MagicStick,
  Monitor,
  MoreFilled,
  Notebook,
  OfficeBuilding,
  Paperclip,
  Plus,
  Promotion,
  QuestionFilled,
  Rank,
  Refresh,
  RefreshLeft,
  Right,
  Search,
  Setting,
  SwitchButton,
  Tools,
  Upload,
  UploadFilled,
  User,
  VideoPause,
  VideoPlay,
  View,
  Warning,
  WarningFilled
}
for (const [key, component] of Object.entries(templateIcons)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)

// ---------- 全局错误兜底 ----------
// 组件渲染/生命周期/侦听器中的未捕获错误
app.config.errorHandler = (err, _instance, info) => {
  console.error('[VueError]', info, err)
  ElMessage.error(err instanceof Error ? err.message : '页面出现异常，请刷新重试')
}
// 未处理的 Promise 拒绝（未被 catch 的异步异常）
window.addEventListener('unhandledrejection', (event) => {
  console.error('[UnhandledRejection]', event.reason)
  // 请求层已统一提示过的错误不再重复弹窗（axios/流式拒绝带 message）
  const reason = event.reason as { response?: unknown; message?: string } | null
  if (reason?.response) return
  if (reason?.message) ElMessage.error(reason.message)
})
// 资源加载等运行时错误（跨域脚本错误 message 为空，只留日志）
window.addEventListener('error', (event) => {
  if (event.message) console.error('[WindowError]', event.message, event.filename, event.lineno)
})

app.mount('#app')
