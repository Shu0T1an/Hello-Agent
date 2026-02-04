<template>
  <div class="p-6 max-w-7xl mx-auto">
    <!-- 页面头部 -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
      <div>
        <h1 class="text-2xl font-semibold text-zinc-900">模型配置</h1>
        <p class="text-sm text-zinc-500 mt-1">配置和管理 LLM 模型</p>
      </div>
      <BaseButton variant="primary" @click="handleCreate" class="btn-hover-lift">
        <Plus :size="18" />
        添加模型
      </BaseButton>
    </div>

    <!-- 加载状态 -->
    <BaseSkeleton v-if="modelStore.loading" type="card" :rows="3" />

    <!-- 错误状态 -->
    <BaseCard v-else-if="modelStore.error" class="p-8 text-center">
      <div class="flex flex-col items-center gap-3">
        <AlertCircle :size="48" class="text-error-500" />
        <p class="text-error-600 font-medium">{{ modelStore.error }}</p>
        <BaseButton variant="secondary" @click="modelStore.fetchModels()">
          重试
        </BaseButton>
      </div>
    </BaseCard>

    <!-- 模型列表 -->
    <div v-else>
      <!-- 搜索 -->
      <div class="mb-6">
        <BaseInput
          v-model="searchQuery"
          placeholder="搜索模型..."
          class="max-w-md"
        >
          <template #prefix>
            <Search :size="18" class="text-zinc-400" />
          </template>
        </BaseInput>
      </div>

      <!-- 模型卡片网格 -->
      <div v-if="filteredModels.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <BaseCard
          v-for="model in filteredModels"
          :key="model.id"
          hoverable
          class="group"
        >
          <template #default>
            <div class="flex items-start justify-between mb-4">
              <div class="flex-1 min-w-0">
                <h3 class="text-lg font-semibold text-zinc-900 truncate">
                  {{ model.displayName }}
                </h3>
                <p class="text-sm text-zinc-500 font-mono mt-1">
                  {{ model.modelName }}
                </p>
              </div>
              <span
                :class="[
                  'px-3 py-1 rounded-full text-xs font-medium flex-shrink-0 ml-2',
                  model.isActive
                    ? 'bg-success-100 text-success-700'
                    : 'bg-zinc-100 text-zinc-600'
                ]"
              >
                {{ model.isActive ? '激活' : '停用' }}
              </span>
            </div>

            <!-- Provider 标签 -->
            <div class="mb-4">
              <span
                :class="[
                  'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold',
                  getProviderBadgeClasses(model.provider)
                ]"
              >
                <component :is="getProviderIcon(model.provider)" :size="14" />
                {{ ProviderDisplayNames[model.provider] || model.provider }}
              </span>
            </div>

            <!-- 模型元信息 -->
            <div class="space-y-2 mb-4 text-sm">
              <div v-if="model.modelId" class="flex items-center gap-2 text-zinc-600">
                <Hash :size="16" class="text-zinc-400" />
                <span class="truncate">{{ model.modelId }}</span>
              </div>
              <div v-if="model.baseUrl" class="flex items-center gap-2 text-zinc-600">
                <Globe :size="16" class="text-zinc-400" />
                <span class="truncate">{{ formatBaseUrl(model.baseUrl) }}</span>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="flex items-center gap-2 pt-4 border-t border-zinc-100">
              <BaseButton
                variant="ghost"
                size="sm"
                class="flex-1"
                @click="handleEdit(model)"
              >
                <Edit :size="16" />
                编辑
              </BaseButton>
              <BaseButton
                variant="ghost"
                size="sm"
                class="text-error-600 hover:text-error-700 hover:bg-error-50"
                @click="handleDelete(model.id)"
              >
                <Trash2 :size="16" />
                删除
              </BaseButton>
            </div>
          </template>
        </BaseCard>
      </div>

      <!-- 空状态 -->
      <BaseEmpty
        v-else-if="modelStore.models.length === 0"
        type="inbox"
        title="暂无模型配置"
        description="点击右上角添加您的第一个模型配置"
        action-text="添加模型"
        @action="handleCreate"
      />
      <BaseEmpty
        v-else
        type="search"
        title="未找到匹配的模型"
        description="请尝试调整搜索关键词"
        :show-action="false"
      />
    </div>

    <!-- 模型表单模态框 -->
    <BaseModal
      v-model:visible="showForm"
      :title="editingModel ? '编辑模型' : '添加模型'"
      width="lg"
    >
      <ModelForm
        :model="editingModel"
        @close="showForm = false"
        @save="handleSave"
      />
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useModelConfigStore } from '@/stores/modelConfig'
import { useToast } from '@/composables/useToast'
import { ProviderDisplayNames, type ModelConfig, type CreateModelDTO, type UpdateModelDTO, type ModelProvider } from '@/types/model'
import ModelForm from '@/components/agent/ModelForm.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseCard from '@/components/base/BaseCard.vue'
import BaseModal from '@/components/base/BaseModal.vue'
import BaseEmpty from '@/components/base/BaseEmpty.vue'
import BaseSkeleton from '@/components/base/BaseSkeleton.vue'
import {
  Plus,
  Search,
  Edit,
  Hash,
  Globe,
  Trash2,
  AlertCircle,
  type Component,
} from 'lucide-vue-next'

// Provider 图标映射（简化版，实际项目中可以使用品牌图标）
const providerIcons: Record<ModelProvider, Component> = {
  openai: Plus, // 可以替换为 OpenAI 图标
  anthropic: Plus, // 可以替换为 Anthropic 图标
  azure: Plus, // 可以替换为 Azure 图标
  ollama: Plus, // 可以替换为 Ollama 图标
  qwen: Plus, // 可以替换为 Qwen 图标
  moonshot: Plus, // 可以替换为 Moonshot 图标
  zhipu: Plus, // 可以替换为智谱图标
  baichuan: Plus, // 可以替换为百川图标
  deepseek: Plus, // 可以替换为 DeepSeek 图标
  other: Plus,
}

// Provider 颜色类
const providerBadgeClasses: Record<ModelProvider, string> = {
  openai: 'bg-emerald-100 text-emerald-700',
  anthropic: 'bg-amber-100 text-amber-700',
  azure: 'bg-sky-100 text-sky-700',
  ollama: 'bg-indigo-100 text-indigo-700',
  qwen: 'bg-orange-100 text-orange-700',
  moonshot: 'bg-indigo-100 text-indigo-700',
  zhipu: 'bg-blue-100 text-blue-700',
  baichuan: 'bg-teal-100 text-teal-700',
  deepseek: 'bg-rose-100 text-rose-700',
  other: 'bg-zinc-100 text-zinc-700',
}

const modelStore = useModelConfigStore()
const toast = useToast()

const showForm = ref(false)
const editingModel = ref<ModelConfig | null>(null)
const searchQuery = ref('')

// 筛选后的模型列表
const filteredModels = computed(() => {
  if (!searchQuery.value) {
    return modelStore.models
  }

  const query = searchQuery.value.toLowerCase()
  return modelStore.models.filter(m =>
    m.displayName.toLowerCase().includes(query) ||
    m.modelName.toLowerCase().includes(query) ||
    m.provider.toLowerCase().includes(query) ||
    (m.modelId && m.modelId.toLowerCase().includes(query))
  )
})

// 获取 Provider 图标
const getProviderIcon = (provider: ModelProvider) => {
  return providerIcons[provider] || providerIcons.other
}

// 获取 Provider 徽章类
const getProviderBadgeClasses = (provider: ModelProvider) => {
  return providerBadgeClasses[provider] || providerBadgeClasses.other
}

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
      toast.success('模型配置更新成功')
    } else {
      await modelStore.createModel(data as CreateModelDTO)
      toast.success('模型配置添加成功')
    }
    showForm.value = false
  } catch (error) {
    console.error('Failed to save model:', error)
    toast.error(editingModel.value ? '更新失败，请重试' : '添加失败，请重试')
  }
}

async function handleDelete(id: number) {
  try {
    await modelStore.deleteModel(id)
    toast.success('模型配置已删除')
  } catch (error) {
    console.error('Failed to delete model:', error)
    toast.error('删除失败，请重试')
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
