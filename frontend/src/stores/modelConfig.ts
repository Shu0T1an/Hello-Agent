/**
 * 模型配置管理 Store
 */

import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { ModelConfig, CreateModelDTO, UpdateModelDTO } from '@/types/model'
import * as modelApi from '@/api/model'

export const useModelConfigStore = defineStore('modelConfig', () => {
  // 状态
  const models = ref<ModelConfig[]>([])
  const providers = ref<string[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 计算属性
  const activeModels = computed(() => models.value.filter(m => m.isActive))
  const inactiveModels = computed(() => models.value.filter(m => !m.isActive))

  /**
   * 获取所有模型配置
   */
  async function fetchModels() {
    loading.value = true
    error.value = null
    try {
      models.value = await modelApi.fetchModels()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch models'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 根据 ID 获取模型配置
   */
  async function fetchModel(id: number) {
    loading.value = true
    error.value = null
    try {
      return await modelApi.fetchModel(id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch model'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取激活的模型配置
   */
  async function fetchActiveModels() {
    loading.value = true
    error.value = null
    try {
      models.value = await modelApi.fetchActiveModels()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch active models'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 根据提供商获取模型配置
   */
  async function fetchModelsByProvider(provider: string) {
    loading.value = true
    error.value = null
    try {
      return await modelApi.fetchModelsByProvider(provider)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch models by provider'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取所有提供商
   */
  async function fetchProviders() {
    loading.value = true
    error.value = null
    try {
      providers.value = await modelApi.fetchProviders()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch providers'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建模型配置
   */
  async function createModel(dto: CreateModelDTO) {
    loading.value = true
    error.value = null
    try {
      const created = await modelApi.createModel(dto)
      models.value.push(created)
      // 如果是新提供商，添加到提供商列表
      if (!providers.value.includes(created.provider)) {
        providers.value.push(created.provider)
      }
      return created
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to create model'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 更新模型配置
   */
  async function updateModel(id: number, dto: UpdateModelDTO) {
    loading.value = true
    error.value = null
    try {
      const updated = await modelApi.updateModel(id, dto)
      const index = models.value.findIndex(m => m.id === id)
      if (index !== -1) {
        models.value[index] = updated
      }
      return updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to update model'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 删除模型配置
   */
  async function deleteModel(id: number) {
    loading.value = true
    error.value = null
    try {
      await modelApi.deleteModel(id)
      models.value = models.value.filter(m => m.id !== id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to delete model'
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    // 状态
    models,
    providers,
    loading,
    error,
    // 计算属性
    activeModels,
    inactiveModels,
    // 方法
    fetchModels,
    fetchModel,
    fetchActiveModels,
    fetchModelsByProvider,
    fetchProviders,
    createModel,
    updateModel,
    deleteModel
  }
})
