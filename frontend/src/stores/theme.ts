import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/** 主题模式：亮色 / 暗色 / 跟随系统 */
export type ThemeMode = 'light' | 'dark' | 'auto'

export const THEME_STORAGE_KEY = 'agentforge-theme-mode'

const media =
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null

function isThemeMode(v: unknown): v is ThemeMode {
  return v === 'light' || v === 'dark' || v === 'auto'
}

function readMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY)
    if (isThemeMode(saved)) return saved
  } catch {
    /* localStorage 不可用时忽略 */
  }
  return 'auto'
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readMode())
  /** 系统偏好（仅 auto 模式生效） */
  const systemDark = ref(media ? media.matches : false)

  /** 实际是否处于暗色 */
  const isDark = computed(() => (mode.value === 'auto' ? systemDark.value : mode.value === 'dark'))

  let transitionTimer: number | undefined

  /** 将主题写入 <html>，Element Plus 依靠 html.dark 生效 */
  function apply(withTransition = false) {
    if (typeof document === 'undefined') return
    const root = document.documentElement
    if (withTransition) {
      root.classList.add('theme-transition')
      clearTimeout(transitionTimer)
      transitionTimer = window.setTimeout(() => root.classList.remove('theme-transition'), 300)
    }
    root.classList.toggle('dark', isDark.value)
    root.dataset.themeMode = mode.value
    // 让原生控件（滚动条、输入框、日期选择器）跟随主题
    root.style.colorScheme = isDark.value ? 'dark' : 'light'
  }

  function setMode(next: ThemeMode) {
    mode.value = next
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next)
    } catch {
      /* 忽略隐私模式下的写入失败 */
    }
    apply(true)
  }

  /** 在亮 / 暗之间快速切换（从 auto 切换时落到与当前相反的具体模式） */
  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  /** 监听系统主题变化，auto 模式下实时跟随 */
  function watchSystem() {
    if (!media) return () => {}
    const onChange = (e: MediaQueryListEvent) => {
      systemDark.value = e.matches
      apply(true)
    }
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }

  return { mode, systemDark, isDark, setMode, toggle, apply, watchSystem }
})
