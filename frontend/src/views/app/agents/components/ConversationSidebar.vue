<script setup lang="ts">
import { ChatDotRound, Delete, EditPen } from '@element-plus/icons-vue'
import type { ChatConversation } from '@/api/types'
import { formatTime } from '@/utils/format'

defineProps<{
  conversations: ChatConversation[]
  loading: boolean
  /** 当前会话 id（null 表示新会话） */
  activeId: number | null
  total: number
}>()

const emit = defineEmits<{
  open: [conv: ChatConversation]
  rename: [conv: ChatConversation]
  remove: [conv: ChatConversation]
  loadMore: []
}>()
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-head">
      <el-icon :size="14"><ChatDotRound /></el-icon>
      <span>会话历史</span>
    </div>
    <el-scrollbar v-loading="loading" class="sidebar-scroll">
      <div
        v-for="c in conversations"
        :key="c.id"
        class="conv-item"
        :class="{ active: c.id === activeId }"
        @click="emit('open', c)"
        @dblclick.stop="emit('rename', c)"
      >
        <div class="conv-title">{{ c.title || '未命名会话' }}</div>
        <div class="conv-meta">
          <el-tag
            size="small"
            :type="c.mode === 'workflow' ? 'warning' : c.mode === 'agent' ? 'success' : 'primary'"
            effect="plain"
          >
            {{ c.mode === 'workflow' ? '工作流' : c.mode === 'agent' ? '智能体' : '直连' }}
          </el-tag>
          <span class="conv-time">{{ formatTime(c.updateTime) }}</span>
          <span class="conv-ops">
            <el-icon class="conv-del conv-edit" @click.stop="emit('rename', c)"
              ><EditPen
            /></el-icon>
            <el-icon class="conv-del" @click.stop="emit('remove', c)"><Delete /></el-icon>
          </span>
        </div>
      </div>
      <el-empty
        v-if="!loading && conversations.length === 0"
        description="暂无会话"
        :image-size="60"
      />
      <div v-if="conversations.length < total" class="conv-more">
        <el-button link type="primary" :loading="loading" @click="emit('loadMore')">
          加载更多
        </el-button>
      </div>
    </el-scrollbar>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 250px;
  flex-shrink: 0;
  background: var(--bg-aside);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}
.sidebar-head {
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.sidebar-scroll {
  flex: 1;
  min-height: 0;
}
.conv-more {
  display: flex;
  justify-content: center;
  padding: 6px 0 12px;
}

.conv-item {
  margin: 4px 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}
.conv-item:hover {
  background: var(--bg-page);
}
.conv-item.active {
  background: var(--brand-gradient-soft);
  border-color: rgba(91, 108, 255, 0.3);
}
.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-meta {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.conv-time {
  font-size: 11px;
  color: var(--text-tertiary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-del {
  color: var(--text-tertiary);
  font-size: 14px;
  opacity: 0;
  transition: all 0.18s ease;
}
.conv-item:hover .conv-del {
  opacity: 1;
}
.conv-ops {
  display: flex;
  align-items: center;
  gap: 4px;
}
.conv-del:hover {
  color: var(--el-color-danger);
}
.conv-edit:hover {
  color: var(--brand-1, #5b6cff);
}
</style>
