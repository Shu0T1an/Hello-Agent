<template>
  <form @submit.prevent="handleSubmit" class="model-form">
    <div class="form-group">
      <label>模型名称 *</label>
      <input
        v-model="formData.modelName"
        type="text"
        required
        placeholder="my-model"
        :disabled="!!model"
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
        placeholder="我的模型"
      />
    </div>

    <div class="form-group">
      <label>提供商 *</label>
      <select v-model="formData.provider" required>
        <option value="">请选择提供商</option>
        <option value="OPENAI">OpenAI</option>
        <option value="DEEPSEEK">DeepSeek</option>
        <option value="GROQ">Groq</option>
        <option value="PERPLEXITY">Perplexity</option>
        <option value="OPENAI_COMPATIBLE">OpenAI 兼容</option>
      </select>
    </div>

    <div class="form-group">
      <label>模型 ID *</label>
      <input
        v-model="formData.modelId"
        type="text"
        required
        placeholder="gpt-4, deepseek-chat, 等"
      />
      <small>提供商特定的模型标识符</small>
    </div>

    <div class="form-group">
      <label>Base URL</label>
      <input
        v-model="formData.baseUrl"
        type="url"
        placeholder="https://api.openai.com/v1"
      />
      <small>可选，留空使用默认地址</small>
    </div>

    <div class="form-group">
      <label>API 密钥 *{{ model ? '（留空保持不变）' : '' }}</label>
      <input
        v-model="formData.apiKey"
        :type="showApiKey ? 'text' : 'password'"
        :required="!model"
        placeholder="sk-..."
      />
      <button
        type="button"
        @click="showApiKey = !showApiKey"
        class="btn-toggle-visibility"
      >
        {{ showApiKey ? '隐藏' : '显示' }}
      </button>
    </div>

    <div class="form-group">
      <label class="checkbox-label">
        <input v-model="formData.isActive" type="checkbox" />
        <span>启用此模型</span>
      </label>
    </div>

    <div class="form-actions">
      <button type="button" @click="$emit('close')" class="btn-secondary">
        取消
      </button>
      <button type="submit" class="btn-primary" :disabled="submitting">
        {{ submitting ? '保存中...' : (model ? '更新' : '创建') }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ModelConfig, CreateModelDTO, UpdateModelDTO } from '@/types/model'

const props = defineProps<{
  model?: ModelConfig | null
}>()

const emit = defineEmits<{
  close: []
  save: [data: CreateModelDTO | UpdateModelDTO]
}>()

const submitting = ref(false)
const showApiKey = ref(false)

const formData = ref({
  modelName: '',
  displayName: '',
  provider: '',
  modelId: '',
  baseUrl: '',
  apiKey: '',
  isActive: true
})

if (props.model) {
  formData.value = {
    modelName: props.model.modelName,
    displayName: props.model.displayName,
    provider: props.model.provider,
    modelId: props.model.modelId,
    baseUrl: props.model.baseUrl || '',
    apiKey: '',
    isActive: props.model.isActive
  }
}

async function handleSubmit() {
  submitting.value = true
  try {
    const data = props.model
      ? {
          displayName: formData.value.displayName,
          provider: formData.value.provider,
          modelId: formData.value.modelId,
          baseUrl: formData.value.baseUrl || undefined,
          apiKey: formData.value.apiKey || undefined,
          isActive: formData.value.isActive
        } as UpdateModelDTO
      : {
          modelName: formData.value.modelName,
          displayName: formData.value.displayName,
          provider: formData.value.provider,
          modelId: formData.value.modelId,
          baseUrl: formData.value.baseUrl || undefined,
          apiKey: formData.value.apiKey,
          isActive: formData.value.isActive
        } as CreateModelDTO

    emit('save', data)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.model-form {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
  position: relative;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
}

.form-group input[type="text"],
.form-group input[type="url"],
.form-group input[type="password"],
.form-group select {
  width: 100%;
  padding: 10px 12px;
  padding-right: 80px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.2s;
}

.form-group input:focus,
.form-group select:focus {
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

.btn-toggle-visibility {
  position: absolute;
  right: 8px;
  top: 34px;
  padding: 6px 12px;
  background: #f3f4f6;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-toggle-visibility:hover {
  background: #e5e7eb;
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
