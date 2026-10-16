import { createApp } from 'vue'
import { createPinia } from 'pinia'
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

app.mount('#app')
