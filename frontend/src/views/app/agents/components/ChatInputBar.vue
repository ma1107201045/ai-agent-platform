<script setup lang="ts">
defineProps<{ sending: boolean }>()

const emit = defineEmits<{ send: []; stop: [] }>()

const input = defineModel<string>({ default: '' })
</script>

<template>
  <div class="input-bar">
    <div class="input-shell">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        @keydown.enter.exact.prevent="emit('send')"
      />
      <div class="input-actions">
        <el-button v-if="sending" class="stop-btn" @click="emit('stop')">
          <span class="stop-square"></span>停止生成
        </el-button>
        <el-button
          v-else
          type="primary"
          class="send-btn btn-gradient"
          :disabled="!input.trim()"
          @click="emit('send')"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.input-bar {
  background: var(--bg-header);
  border-top: 1px solid var(--border-color);
  padding: 14px 20px 16px;
  flex-shrink: 0;
}
.input-shell {
  max-width: 860px;
  margin: 0 auto;
  background: var(--bg-card);
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 10px 12px 10px 16px;
  transition: all 0.25s ease;
  box-shadow: var(--shadow-card);
}
.input-shell:focus-within {
  border-color: var(--brand-1);
  box-shadow:
    0 0 0 4px rgba(91, 108, 255, 0.1),
    var(--shadow-card-hover);
}
.input-shell :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none !important;
  padding: 0;
  font-size: 14px;
  line-height: 1.6;
}
.input-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
.send-btn {
  border-radius: 10px;
  height: 34px;
  min-width: 84px;
}
.send-btn:disabled {
  background: var(--el-color-primary-light-7) !important;
  box-shadow: none;
  transform: none;
}
.stop-btn {
  border-radius: 10px;
  height: 34px;
  color: var(--el-color-danger);
  border-color: var(--el-color-danger-light-8);
}
.stop-btn:hover {
  background: var(--el-color-danger-light-9);
}
.stop-square {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  background: currentColor;
  display: inline-block;
  margin-right: 6px;
}
</style>
