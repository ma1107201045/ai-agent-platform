<script setup lang="ts">
import { computed } from 'vue'
import { Check, Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { useThemeStore } from '@/stores/theme'
import type { ThemeMode } from '@/stores/theme'

withDefaults(defineProps<{ size?: number }>(), { size: 17 })

const themeStore = useThemeStore()

const options: { value: ThemeMode; label: string; icon: typeof Sunny }[] = [
  { value: 'light', label: '亮色', icon: Sunny },
  { value: 'dark', label: '暗色', icon: Moon },
  { value: 'auto', label: '跟随系统', icon: Monitor }
]

const currentIcon = computed(() => {
  if (themeStore.mode === 'auto') return Monitor
  return themeStore.isDark ? Moon : Sunny
})

const tip = computed(() => {
  const map: Record<ThemeMode, string> = {
    light: '当前：亮色',
    dark: '当前：暗色',
    auto: `跟随系统（${themeStore.systemDark ? '暗色' : '亮色'}）`
  }
  return map[themeStore.mode]
})

function select(value: ThemeMode) {
  themeStore.setMode(value)
}
</script>

<template>
  <el-dropdown trigger="click" @command="select">
    <button class="theme-btn" :aria-label="tip">
      <el-icon :size="size"><component :is="currentIcon" /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="opt in options"
          :key="opt.value"
          :command="opt.value"
          :class="{ 'is-selected': themeStore.mode === opt.value }"
        >
          <el-icon class="opt-icon"><component :is="opt.icon" /></el-icon>
          <span class="opt-label">{{ opt.label }}</span>
          <el-icon v-if="themeStore.mode === opt.value" class="opt-check"><Check /></el-icon>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.theme-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}
.theme-btn:hover {
  background: var(--hover-bg);
  color: var(--text-primary);
}
.opt-icon {
  margin-right: 8px;
}
.opt-label {
  flex: 1;
}
.opt-check {
  margin-left: 12px;
  color: var(--brand-1);
}
.is-selected {
  color: var(--brand-1);
  font-weight: 600;
}
</style>
