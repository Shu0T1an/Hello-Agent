<template>
  <form @submit.prevent="handleSubmit" class="agent-form">
    <div class="form-group">
      <label>Agent 名称 *</label>
      <input
        v-model="formData.agentName"
        type="text"
        required
        placeholder="my-agent"
        :disabled="!!agent"
        pattern="[a-z0-9-]+"
        title="只能使用小写字母、数字和连字符"
      />
      <small>唯一标识，只能使用小写字母、数字和连字符</small>
    </div>

    <div class="form-group">
      <label>显示名称 *</label>
      <input
        v-model="formData.displayName"
        type="text"
        required
        placeholder="我的 Agent"
      />
    </div>

    <div class="form-group">
      <label>描述</label>
      <textarea
        v-model="formData.description"
        rows="2"
        placeholder="描述这个 Agent 的用途..."
      ></textarea>
    </div>

    <div class="form-group">
      <label>模型 *</label>
      <select v-model="formData.modelId" required>
        <option value="">请选择模型</option>
        <option v-for="model in activeModels" :key="model.id" :value="model.id">
          {{ model.displayName }} ({{ model.provider }} / {{ model.modelId }})
        </option>
      </select>
    </div>

    <div class="form-group">
      <label>系统提示词</label>
      <textarea
        v-model="formData.systemPrompt"
        rows="4"
        placeholder="你是一个有用的助手..."
      ></textarea>
    </div>

    <div class="form-row">
      <div class="form-group">
        <label>最大迭代次数</label>
        <input
          v-model.number="formData.maxIterations"
          type="number"
          min="1"
          max="50"
        />
      </div>

      <div class="form-group">
        <label>温度</label>
        <input
          v-model.number="formData.temperature"
          type="number"
          min="0"
          max="2"
          step="0.1"
        />
      </div>
    </div>

    <div class="form-group">
      <label class="checkbox-label">
        <input v-model="formData.enableStreaming" type="checkbox" />
        <span>启用流式输出</span>
      </label>
    </div>

    <div class="form-group">
      <label>选择工具</label>
      <ToolSelector v-model="formData.toolIds" />
    </div>

    <div class="form-actions">
      <button type="button" @click="$emit('close')" class="btn-secondary">
        取消
      </button>
      <button type="submit" class="btn-primary" :disabled="submitting">
        {{ submitting ? '保存中...' : (agent ? '更新' : '创建') }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useModelConfigStore } from '@/stores/modelConfig'
import { useToolDefinitionStore } from '@/stores/toolDefinition'
import type { AgentConfig, CreateAgentDTO, UpdateAgentDTO } from '@/types/agent'
import ToolSelector from './ToolSelector.vue'

const props = defineProps<{
  agent?: AgentConfig | null
}>()

const emit = defineEmits<{
  close: []
  save: [data: CreateAgentDTO | UpdateAgentDTO]
}>()

const modelStore = useModelConfigStore()
const toolStore = useToolDefinitionStore()

const submitting = ref(false)

const formData = ref({
  agentName: '',
  displayName: '',
  description: '',
  modelId: 0,
  systemPrompt: '',
  maxIterations: 10,
  temperature: 0.7,
  enableStreaming: true,
  toolIds: [] as number[]
})

const activeModels = computed(() => modelStore.activeModels)

onMounted(async () => {
  await modelStore.fetchActiveModels()
  await toolStore.fetchTools()

  if (props.agent) {
    formData.value = {
      agentName: props.agent.agentName,
      displayName: props.agent.displayName,
      description: props.agent.description || '',
      modelId: props.agent.modelId,
      systemPrompt: props.agent.systemPrompt || '',
      maxIterations: props.agent.maxIterations || 10,
      temperature: props.agent.temperature || 0.7,
      enableStreaming: props.agent.enableStreaming ?? true,
      toolIds: props.agent.toolIds || []
    }
  }
})

async function handleSubmit() {
  submitting.value = true
  try {
    const data = props.agent
      ? {
          displayName: formData.value.displayName,
          description: formData.value.description,
          modelId: formData.value.modelId,
          systemPrompt: formData.value.systemPrompt,
          maxIterations: formData.value.maxIterations,
          temperature: formData.value.temperature,
          enableStreaming: formData.value.enableStreaming,
          toolIds: formData.value.toolIds
        } as UpdateAgentDTO
      : {
          agentName: formData.value.agentName,
          displayName: formData.value.displayName,
          description: formData.value.description,
          modelId: formData.value.modelId,
          systemPrompt: formData.value.systemPrompt,
          maxIterations: formData.value.maxIterations,
          temperature: formData.value.temperature,
          enableStreaming: formData.value.enableStreaming,
          toolIds: formData.value.toolIds
        } as CreateAgentDTO

    emit('save', data)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.agent-form {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
}

.form-group input[type="text"],
.form-group input[type="number"],
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.2s;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #6366f1;
}

.form-group input:disabled {
  background: #f3f4f6;
  color: #6b7280;
}

.form-group small {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
}

.checkbox-label span {
  font-size: 14px;
  color: #374151;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}

.btn-secondary,
.btn-primary {
  padding: 10px 24px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.btn-primary {
  background: #6366f1;
  color: white;
}

.btn-secondary:hover,
.btn-primary:hover:not(:disabled) {
  opacity: 0.8;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
