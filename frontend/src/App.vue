<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

let stopWatchSystem: (() => void) | undefined

onMounted(() => {
  // 立即生效一次，保证与 index.html 中的预加载脚本结果一致
  themeStore.apply()
  stopWatchSystem = themeStore.watchSystem()
})

onBeforeUnmount(() => stopWatchSystem?.())
</script>

<template>
  <router-view v-slot="{ Component }">
    <transition name="fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<style>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
