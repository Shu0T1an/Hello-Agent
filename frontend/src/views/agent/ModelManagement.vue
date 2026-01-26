<template>
  <div class="model-management">
    <div class="page-header">
      <h1>模型配置</h1>
      <button class="btn-primary" @click="handleCreate">
        <span>+</span> 添加模型
      </button>
    </div>

    <div v-if="modelStore.loading" class="loading">加载中...</div>
    <div v-else-if="modelStore.error" class="error">{{ modelStore.error }}</div>
    <div v-else class="model-list">
      <div v-for="model in modelStore.models" :key="model.id" class="model-item">
        <div class="model-info">
          <div class="model-header">
            <h3>{{ model.displayName }}</h3>
            <span :class="['status-badge', model.isActive ? 'active' : 'inactive']">
              {{ model.isActive ? '激活' : '停用' }}
            </span>
          </div>
          <p class="model-name">{{ model.modelName }}</p>
          <div class="model-meta">
            <span class="provider-badge">{{ model.provider }}</span>
            <span class="model-id">{{ model.modelId }}</span>
            <span v-if="model.baseUrl" class="base-url">{{ formatBaseUrl(model.baseUrl) }}</span>
          </div>
        </div>
        <div class="model-actions">
          <button @click="handleEdit(model)" class="btn-secondary">编辑</button>
          <button @click="handleDelete(model.id)" class="btn-danger">删除</button>
        </div>
      </div>

      <div v-if="modelStore.models.length === 0" class="empty-state">
        <p>暂无模型配置，点击右上角添加</p>
      </div>
    </div>

    <!-- 模型表单对话框 -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2>{{ editingModel ? '编辑模型' : '添加模型' }}</h2>
          <button @click="showForm = false" class="btn-close">×</button>
        </div>
        <ModelForm
          :model="editingModel"
          @close="showForm = false"
          @save="handleSave"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useModelConfigStore } from '@/stores/modelConfig'
import { ProviderDisplayNames, type ModelConfig, type CreateModelDTO, type UpdateModelDTO } from '@/types/model'
import ModelForm from '@/components/agent/ModelForm.vue'

const modelStore = useModelConfigStore()

const showForm = ref(false)
const editingModel = ref<ModelConfig | null>(null)

onMounted(() => {
  modelStore.fetchModels()
})

function handleCreate() {
  editingModel.value = null
  showForm.value = true
}

function handleEdit(model: ModelConfig) {
  editingModel.value = model
  showForm.value = true
}

async function handleSave(data: CreateModelDTO | UpdateModelDTO) {
  try {
    if (editingModel.value) {
      await modelStore.updateModel(editingModel.value.id, data as UpdateModelDTO)
    } else {
      await modelStore.createModel(data as CreateModelDTO)
    }
    showForm.value = false
  } catch (error) {
    console.error('Failed to save model:', error)
    alert('保存失败，请重试')
  }
}

async function handleDelete(id: number) {
  if (!confirm('确定删除此模型配置？')) return

  try {
    await modelStore.deleteModel(id)
  } catch (error) {
    console.error('Failed to delete model:', error)
    alert('删除失败，请重试')
  }
}

function formatBaseUrl(url?: string): string {
  if (!url) return ''
  try {
    const urlObj = new URL(url)
    return urlObj.hostname
  } catch {
    return url.length > 30 ? url.substring(0, 30) + '...' : url
  }
}
</script>

<style scoped>
.model-management {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
}

.btn-primary {
  padding: 10px 20px;
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-primary:hover {
  background: #4f46e5;
}

.loading, .error {
  text-align: center;
  padding: 40px;
  color: #6b7280;
}

.error {
  color: #ef4444;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.model-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  transition: box-shadow 0.2s;
}

.model-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.model-info {
  flex: 1;
}

.model-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.model-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.active {
  background: #dcfce7;
  color: #166534;
}

.status-badge.inactive {
  background: #f3f4f6;
  color: #6b7280;
}

.model-name {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 12px 0;
  font-family: monospace;
}

.model-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.provider-badge {
  padding: 4px 12px;
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.model-id, .base-url {
  font-size: 13px;
  color: #6b7280;
  padding: 4px 0;
}

.model-actions {
  display: flex;
  gap: 8px;
}

.btn-secondary, .btn-danger {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: opacity 0.2s;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.btn-danger {
  background: #fee2e2;
  color: #991b1b;
}

.btn-secondary:hover,
.btn-danger:hover {
  opacity: 0.8;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #9ca3af;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 16px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.btn-close {
  background: none;
  border: none;
  font-size: 28px;
  cursor: pointer;
  color: #6b7280;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-close:hover {
  color: #374151;
}
</style>
