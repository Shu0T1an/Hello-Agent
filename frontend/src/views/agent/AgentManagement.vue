<template>
  <div class="agent-management">
    <div class="page-header">
      <h1>Agent 管理</h1>
      <button class="btn-primary" @click="handleCreate">
        <span>+</span> 创建 Agent
      </button>
    </div>

    <div v-if="agentStore.loading" class="loading">加载中...</div>
    <div v-else-if="agentStore.error" class="error">{{ agentStore.error }}</div>
    <div v-else class="agent-list">
      <div v-for="agent in agentStore.agents" :key="agent.id" class="agent-item">
        <div class="agent-info">
          <div class="agent-header">
            <h3>{{ agent.displayName }}</h3>
            <span :class="['status-badge', agent.isActive ? 'active' : 'inactive']">
              {{ agent.isActive ? '激活' : '停用' }}
            </span>
          </div>
          <p class="agent-name">{{ agent.agentName }}</p>
          <p v-if="agent.description" class="agent-description">{{ agent.description }}</p>
          <div class="agent-meta">
            <span v-if="agent.modelConfig">{{ agent.modelConfig.displayName }}</span>
            <span v-if="agent.toolDefinitions">{{ agent.toolDefinitions.length }} 个工具</span>
          </div>
        </div>
        <div class="agent-actions">
          <button @click="handleEdit(agent)" class="btn-secondary">编辑</button>
          <button v-if="!agent.isActive" @click="handleActivate(agent.id)" class="btn-success">激活</button>
          <button v-else @click="handleDeactivate(agent.id)" class="btn-warning">停用</button>
          <button @click="handleReload(agent.id)" class="btn-secondary">重载</button>
          <button @click="handleDelete(agent.id)" class="btn-danger">删除</button>
        </div>
      </div>

      <div v-if="agentStore.agents.length === 0" class="empty-state">
        <p>暂无 Agent，点击右上角创建</p>
      </div>
    </div>

    <!-- Agent 表单对话框 -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2>{{ editingAgent ? '编辑 Agent' : '创建 Agent' }}</h2>
          <button @click="showForm = false" class="btn-close">×</button>
        </div>
        <AgentForm
          :agent="editingAgent"
          @close="showForm = false"
          @save="handleSave"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAgentConfigStore } from '@/stores/agentConfig'
import type { AgentConfig, CreateAgentDTO, UpdateAgentDTO } from '@/types/agent'
import AgentForm from '@/components/agent/AgentForm.vue'

const agentStore = useAgentConfigStore()

const showForm = ref(false)
const editingAgent = ref<AgentConfig | null>(null)

onMounted(() => {
  agentStore.fetchAgents()
})

function handleCreate() {
  editingAgent.value = null
  showForm.value = true
}

function handleEdit(agent: AgentConfig) {
  editingAgent.value = agent
  showForm.value = true
}

async function handleSave(data: CreateAgentDTO | UpdateAgentDTO) {
  try {
    if (editingAgent.value) {
      await agentStore.updateAgent(editingAgent.value.id, data as UpdateAgentDTO)
    } else {
      await agentStore.createAgent(data as CreateAgentDTO)
    }
    showForm.value = false
  } catch (error) {
    console.error('Failed to save agent:', error)
    alert('保存失败，请重试')
  }
}

async function handleActivate(id: number) {
  try {
    await agentStore.activateAgent(id)
  } catch (error) {
    console.error('Failed to activate agent:', error)
    alert('激活失败，请重试')
  }
}

async function handleDeactivate(id: number) {
  try {
    await agentStore.deactivateAgent(id)
  } catch (error) {
    console.error('Failed to deactivate agent:', error)
    alert('停用失败，请重试')
  }
}

async function handleReload(id: number) {
  try {
    await agentStore.reloadAgent(id)
    alert('重载成功')
  } catch (error) {
    console.error('Failed to reload agent:', error)
    alert('重载失败，请重试')
  }
}

async function handleDelete(id: number) {
  if (!confirm('确定删除此 Agent？')) return

  try {
    await agentStore.deleteAgent(id)
  } catch (error) {
    console.error('Failed to delete agent:', error)
    alert('删除失败，请重试')
  }
}
</script>

<style scoped>
.agent-management {
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

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.agent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  transition: box-shadow 0.2s;
}

.agent-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.agent-info {
  flex: 1;
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.agent-header h3 {
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

.agent-name {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 8px 0;
  font-family: monospace;
}

.agent-description {
  font-size: 14px;
  color: #374151;
  margin: 0 0 12px 0;
}

.agent-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #6b7280;
}

.agent-actions {
  display: flex;
  gap: 8px;
}

.btn-secondary, .btn-success, .btn-warning, .btn-danger {
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

.btn-success {
  background: #dcfce7;
  color: #166534;
}

.btn-warning {
  background: #fef9c3;
  color: #854d0e;
}

.btn-danger {
  background: #fee2e2;
  color: #991b1b;
}

.btn-secondary:hover,
.btn-success:hover,
.btn-warning:hover,
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
  max-width: 700px;
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
