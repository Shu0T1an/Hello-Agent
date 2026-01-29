<template>
  <div class="p-6 max-w-7xl mx-auto">
    <!-- 页面头部 -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
      <div>
        <h1 class="text-2xl font-semibold text-slate-900">Agent 管理</h1>
        <p class="text-sm text-slate-500 mt-1">创建和管理您的 AI Agent</p>
      </div>
      <BaseButton variant="primary" @click="handleCreate" class="btn-hover-lift">
        <Plus :size="18" />
        创建 Agent
      </BaseButton>
    </div>

    <!-- 加载状态 -->
    <BaseSkeleton v-if="agentStore.loading" type="card" :rows="3" />

    <!-- 错误状态 -->
    <BaseCard v-else-if="agentStore.error" class="p-8 text-center">
      <div class="flex flex-col items-center gap-3">
        <AlertCircle :size="48" class="text-error-500" />
        <p class="text-error-600 font-medium">{{ agentStore.error }}</p>
        <BaseButton variant="secondary" @click="agentStore.fetchAgents()">
          重试
        </BaseButton>
      </div>
    </BaseCard>

    <!-- Agent 列表 -->
    <div v-else>
      <!-- 搜索和筛选 -->
      <div class="flex flex-col sm:flex-row gap-4 mb-6">
        <BaseInput
          v-model="searchQuery"
          placeholder="搜索 Agent..."
          class="flex-1"
        >
          <template #prefix>
            <Search :size="18" class="text-slate-400" />
          </template>
        </BaseInput>
        <div class="flex gap-2">
          <BaseButton
            :variant="filterStatus === 'all' ? 'primary' : 'ghost'"
            size="sm"
            @click="filterStatus = 'all'"
          >
            全部
          </BaseButton>
          <BaseButton
            :variant="filterStatus === 'active' ? 'primary' : 'ghost'"
            size="sm"
            @click="filterStatus = 'active'"
          >
            激活
          </BaseButton>
          <BaseButton
            :variant="filterStatus === 'inactive' ? 'primary' : 'ghost'"
            size="sm"
            @click="filterStatus = 'inactive'"
          >
            停用
          </BaseButton>
        </div>
      </div>

      <!-- Agent 卡片网格 -->
      <div v-if="filteredAgents.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <BaseCard
          v-for="agent in filteredAgents"
          :key="agent.id"
          hoverable
          class="group"
        >
          <template #default>
            <div class="flex items-start justify-between mb-4">
              <div class="flex-1 min-w-0">
                <h3 class="text-lg font-semibold text-slate-900 truncate">
                  {{ agent.displayName }}
                </h3>
                <p class="text-sm text-slate-500 font-mono mt-1">
                  {{ agent.agentName }}
                </p>
              </div>
              <span
                :class="[
                  'px-3 py-1 rounded-full text-xs font-medium flex-shrink-0 ml-2',
                  agent.isActive
                    ? 'bg-success-100 text-success-700'
                    : 'bg-slate-100 text-slate-600'
                ]"
              >
                {{ agent.isActive ? '激活' : '停用' }}
              </span>
            </div>

            <p v-if="agent.description" class="text-sm text-slate-600 mb-4 line-clamp-2">
              {{ agent.description }}
            </p>

            <div class="flex flex-wrap gap-2 mb-4">
              <span
                v-if="agent.modelConfig"
                class="inline-flex items-center gap-1 px-2 py-1 bg-indigo-50 text-indigo-700 rounded-md text-xs font-medium"
              >
                <Cpu :size="14" />
                {{ agent.modelConfig.displayName }}
              </span>
              <span
                v-if="agent.toolDefinitions"
                class="inline-flex items-center gap-1 px-2 py-1 bg-slate-100 text-slate-600 rounded-md text-xs font-medium"
              >
                <Wrench :size="14" />
                {{ agent.toolDefinitions.length }} 个工具
              </span>
            </div>

            <!-- 操作按钮 -->
            <div class="flex items-center gap-2 pt-4 border-t border-slate-100">
              <BaseButton
                variant="ghost"
                size="sm"
                class="flex-1"
                @click="handleEdit(agent)"
              >
                <Edit :size="16" />
                编辑
              </BaseButton>

              <BaseDropdown :items="getDropdownItems(agent)">
                <template #trigger>
                  <BaseButton variant="ghost" size="sm" class="p-2">
                    <MoreVertical :size="18" />
                  </BaseButton>
                </template>
              </BaseDropdown>
            </div>
          </template>
        </BaseCard>
      </div>

      <!-- 空状态 -->
      <BaseEmpty
        v-else-if="agentStore.agents.length === 0"
        type="inbox"
        title="暂无 Agent"
        description="点击右上角创建您的第一个 Agent"
        action-text="创建 Agent"
        @action="handleCreate"
      />
      <BaseEmpty
        v-else
        type="search"
        title="未找到匹配的 Agent"
        description="请尝试调整搜索关键词或筛选条件"
        :show-action="false"
      />
    </div>

    <!-- Agent 表单模态框 -->
    <BaseModal
      v-model:visible="showForm"
      :title="editingAgent ? '编辑 Agent' : '创建 Agent'"
      width="lg"
    >
      <AgentForm
        :agent="editingAgent"
        @close="showForm = false"
        @save="handleSave"
      />
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAgentConfigStore } from '@/stores/agentConfig'
import { useToast } from '@/composables/useToast'
import type { AgentConfig, CreateAgentDTO, UpdateAgentDTO } from '@/types/agent'
import AgentForm from '@/components/agent/AgentForm.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseCard from '@/components/base/BaseCard.vue'
import BaseModal from '@/components/base/BaseModal.vue'
import BaseEmpty from '@/components/base/BaseEmpty.vue'
import BaseSkeleton from '@/components/base/BaseSkeleton.vue'
import BaseDropdown from '@/components/base/BaseDropdown.vue'
import {
  Plus,
  Search,
  Edit,
  Cpu,
  Wrench,
  MoreVertical,
  AlertCircle,
  Power,
  RefreshCw,
  Trash2,
} from 'lucide-vue-next'

const agentStore = useAgentConfigStore()
const toast = useToast()

const showForm = ref(false)
const editingAgent = ref<AgentConfig | null>(null)
const searchQuery = ref('')
const filterStatus = ref<'all' | 'active' | 'inactive'>('all')

// 筛选后的 Agent 列表
const filteredAgents = computed(() => {
  let result = agentStore.agents

  // 状态筛选
  if (filterStatus.value === 'active') {
    result = result.filter(a => a.isActive)
  } else if (filterStatus.value === 'inactive') {
    result = result.filter(a => !a.isActive)
  }

  // 搜索筛选
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(a =>
      a.displayName.toLowerCase().includes(query) ||
      a.agentName.toLowerCase().includes(query) ||
      (a.description && a.description.toLowerCase().includes(query))
    )
  }

  return result
})

// 获取下拉菜单项
const getDropdownItems = (agent: AgentConfig) => {
  return [
    {
      label: agent.isActive ? '停用' : '激活',
      icon: Power,
      action: () => agent.isActive ? handleDeactivate(agent.id) : handleActivate(agent.id),
      variant: agent.isActive ? 'warning' : 'success',
    },
    {
      label: '重载',
      icon: RefreshCw,
      action: () => handleReload(agent.id),
    },
    {
      label: '删除',
      icon: Trash2,
      action: () => handleDelete(agent.id),
      variant: 'danger',
    },
  ]
}

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
      toast.success('Agent 更新成功')
    } else {
      await agentStore.createAgent(data as CreateAgentDTO)
      toast.success('Agent 创建成功')
    }
    showForm.value = false
  } catch (error) {
    console.error('Failed to save agent:', error)
    toast.error(editingAgent.value ? '更新失败，请重试' : '创建失败，请重试')
  }
}

async function handleActivate(id: number) {
  try {
    await agentStore.activateAgent(id)
    toast.success('Agent 已激活')
  } catch (error) {
    console.error('Failed to activate agent:', error)
    toast.error('激活失败，请重试')
  }
}

async function handleDeactivate(id: number) {
  try {
    await agentStore.deactivateAgent(id)
    toast.success('Agent 已停用')
  } catch (error) {
    console.error('Failed to deactivate agent:', error)
    toast.error('停用失败，请重试')
  }
}

async function handleReload(id: number) {
  try {
    await agentStore.reloadAgent(id)
    toast.success('Agent 重载成功')
  } catch (error) {
    console.error('Failed to reload agent:', error)
    toast.error('重载失败，请重试')
  }
}

async function handleDelete(id: number) {
  try {
    await agentStore.deleteAgent(id)
    toast.success('Agent 已删除')
  } catch (error) {
    console.error('Failed to delete agent:', error)
    toast.error('删除失败，请重试')
  }
}
</script>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
