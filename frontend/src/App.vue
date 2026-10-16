<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
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
  <!-- Element Plus 按需引入后，通过 el-config-provider 提供中文语言包 -->
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </el-config-provider>
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
