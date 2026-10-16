<script setup lang="ts">
import { useRouter } from 'vue-router'
import { CircleCheck, CopyDocument } from '@element-plus/icons-vue'
import type { AppAgentTool, KnowledgeDataset, Skill } from '@/api/types'

/** 智能体应用（agent 类型）的绑定配置面板：工具 / 知识库 / 技能 / 应用设置 */
defineProps<{
  allTools: AppAgentTool[]
  datasets: KnowledgeDataset[]
  allSkills: Skill[]
  saving: boolean
  publishing: boolean
}>()

const emit = defineEmits<{
  save: []
  publish: []
}>()

const router = useRouter()

const boundToolIds = defineModel<number[]>('toolIds', { required: true })
const boundDatasetIds = defineModel<number[]>('datasetIds', { required: true })
const boundSkillIds = defineModel<number[]>('skillIds', { required: true })
const welcomeMessage = defineModel<string>('welcome', { required: true })
const openingQuestionsText = defineModel<string>('openingQuestions', { required: true })
</script>

<template>
  <div class="agent-config">
    <el-card shadow="never" class="agent-card">
      <template #header>
        <div class="agent-card-head">
          <span>绑定工具</span>
          <el-button link type="primary" @click="router.push('/tools')">前往工具管理</el-button>
        </div>
      </template>
      <p class="agent-tip">
        智能体会根据对话内容自主决定调用哪些工具。请从可用工具中选择本应用可以使用的工具。
      </p>
      <el-checkbox-group v-model="boundToolIds" class="tool-check-list">
        <el-checkbox v-for="t in allTools" :key="t.id" :value="t.id" class="tool-check">
          <div class="tool-check-body">
            <div class="tool-check-name">
              <code class="tool-code">{{ t.name }}</code>
              <el-tag size="small" :type="t.type === 'http' ? 'warning' : 'info'" effect="plain">
                {{ t.type === 'http' ? 'HTTP' : '代码' }}
              </el-tag>
            </div>
            <div class="tool-check-desc">{{ t.description }}</div>
          </div>
        </el-checkbox>
      </el-checkbox-group>
      <el-empty
        v-if="allTools.length === 0"
        description="暂无可用工具，请先在「工具管理」中创建"
        :image-size="60"
      />
    </el-card>

    <el-card shadow="never" class="agent-card">
      <template #header>
        <div class="agent-card-head">
          <span>绑定知识库</span>
          <el-button link type="primary" @click="router.push('/data/knowledge')"
            >前往知识库管理</el-button
          >
        </div>
      </template>
      <p class="agent-tip">
        智能体会基于用户提问检索绑定数据集中的内容，并将命中的资料作为上下文辅助回答（RAG）。
      </p>
      <el-checkbox-group v-model="boundDatasetIds" class="tool-check-list">
        <el-checkbox v-for="d in datasets" :key="d.id" :value="d.id" class="tool-check">
          <div class="tool-check-body">
            <div class="tool-check-name">
              <code class="tool-code">{{ d.name }}</code>
              <el-tag size="small" type="success" effect="plain">知识库</el-tag>
            </div>
            <div class="tool-check-desc">{{ d.description || '暂无描述' }}</div>
          </div>
        </el-checkbox>
      </el-checkbox-group>
      <el-empty
        v-if="datasets.length === 0"
        description="暂无数据集，请先在「知识库」中创建"
        :image-size="60"
      />
    </el-card>

    <el-card shadow="never" class="agent-card">
      <template #header>
        <div class="agent-card-head">
          <span>绑定技能</span>
          <el-button link type="primary" @click="router.push('/app/skills')">技能库管理</el-button>
        </div>
      </template>
      <p class="agent-tip">
        技能是可复用能力包（指令 + 依赖工具/知识库）。启用后指令自动注入提示词，
        依赖工具与参考数据集在运行期自动合并，让智能体学会该类任务；无需技能可留空。
      </p>
      <el-checkbox-group v-model="boundSkillIds" class="tool-check-list">
        <el-checkbox v-for="s in allSkills" :key="s.id" :value="s.id" class="tool-check">
          <div class="tool-check-body">
            <div class="tool-check-name">
              <code class="tool-code">{{ s.name }}</code>
              <el-tag
                size="small"
                effect="plain"
                :type="s.type === 'tool' ? 'warning' : s.type === 'knowledge' ? 'success' : 'info'"
              >
                {{ s.type === 'tool' ? '工具复合' : s.type === 'knowledge' ? '知识技能' : '指令' }}
              </el-tag>
            </div>
            <div class="tool-check-desc">{{ s.description || '暂无描述' }}</div>
          </div>
        </el-checkbox>
      </el-checkbox-group>
      <el-empty
        v-if="allSkills.length === 0"
        description="暂无技能，请先在「技能库」中创建"
        :image-size="60"
      />
    </el-card>

    <el-card shadow="never" class="agent-card">
      <template #header>
        <div class="agent-card-head">
          <span>应用设置</span>
        </div>
      </template>
      <el-form label-position="top" size="small">
        <el-form-item label="开场白">
          <el-input
            v-model="welcomeMessage"
            type="textarea"
            :rows="3"
            placeholder="对话开始时的欢迎语，留空则使用默认文案"
          />
        </el-form-item>
        <el-form-item label="推荐问题">
          <el-input
            v-model="openingQuestionsText"
            type="textarea"
            :rows="4"
            placeholder="每行一个推荐问题，展示在聊天页欢迎区，点击即可提问"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <div class="agent-actions">
      <el-button :icon="CopyDocument" :loading="saving" @click="emit('save')">保存配置</el-button>
      <el-button
        :icon="CircleCheck"
        class="btn-gradient"
        :loading="publishing"
        @click="emit('publish')"
        >发布</el-button
      >
    </div>
  </div>
</template>

<style scoped>
.agent-config {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: 28px;
  display: flex;
  justify-content: center;
}

.agent-card {
  width: 680px;
  height: fit-content;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.agent-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.agent-tip {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--fill-light);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px 14px;
}

.tool-check-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tool-check {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  transition: all 0.2s ease;
  white-space: normal;
}

.tool-check:hover {
  border-color: var(--brand-3);
  background: var(--fill-lighter);
}

.tool-check-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 4px;
}

.tool-check-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
  color: var(--brand-1);
  background: var(--fill-light);
  padding: 2px 6px;
  border-radius: 4px;
}

.tool-check-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.5;
}

.agent-actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
