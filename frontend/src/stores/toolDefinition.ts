/**
 * 工具定义管理 Store
 */

import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { ToolDefinition, CreateToolDTO, UpdateToolDTO, ToolType } from '@/types/tool'
import * as toolApi from '@/api/tool'

export const useToolDefinitionStore = defineStore('toolDefinition', () => {
  // 状态
  const tools = ref<ToolDefinition[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 计算属性
  const localTools = computed(() => tools.value.filter(t => t.toolType === 'LOCAL'))
  const mcpTools = computed(() => tools.value.filter(t => t.toolType === 'MCP'))
  const activeTools = computed(() => tools.value.filter(t => t.isActive))
  const inactiveTools = computed(() => tools.value.filter(t => !t.isActive))

  /**
   * 获取所有工具
   */
  async function fetchTools() {
    loading.value = true
    error.value = null
    try {
      tools.value = await toolApi.fetchTools()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch tools'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 根据 ID 获取工具
   */
  async function fetchTool(id: number) {
    loading.value = true
    error.value = null
    try {
      return await toolApi.fetchTool(id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch tool'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 根据类型获取工具
   */
  async function fetchToolsByType(type: ToolType) {
    loading.value = true
    error.value = null
    try {
      return await toolApi.fetchToolsByType(type)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch tools by type'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取本地工具
   */
  async function fetchLocalTools() {
    return fetchToolsByType('LOCAL' as ToolType)
  }

  /**
   * 获取 MCP 工具
   */
  async function fetchMcpTools() {
    return fetchToolsByType('MCP' as ToolType)
  }

  /**
   * 创建工具定义
   */
  async function createTool(dto: CreateToolDTO) {
    loading.value = true
    error.value = null
    try {
      const created = await toolApi.createTool(dto)
      tools.value.push(created)
      return created
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to create tool'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 更新工具定义
   */
  async function updateTool(id: number, dto: UpdateToolDTO) {
    loading.value = true
    error.value = null
    try {
      const updated = await toolApi.updateTool(id, dto)
      const index = tools.value.findIndex(t => t.id === id)
      if (index !== -1) {
        tools.value[index] = updated
      }
      return updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to update tool'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 删除工具定义
   */
  async function deleteTool(id: number) {
    loading.value = true
    error.value = null
    try {
      await toolApi.deleteTool(id)
      tools.value = tools.value.filter(t => t.id !== id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to delete tool'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 手动触发本地工具扫描
   */
  async function scanLocalTools() {
    loading.value = true
    error.value = null
    try {
      const result = await toolApi.scanLocalTools()
      // 重新获取工具列表
      await fetchTools()
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to scan local tools'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 手动触发 MCP 工具同步
   */
  async function syncMcpTools(connectionName: string) {
    loading.value = true
    error.value = null
    try {
      const result = await toolApi.syncMcpTools(connectionName)
      // 重新获取工具列表
      await fetchTools()
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to sync MCP tools'
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    // 状态
    tools,
    loading,
    error,
    // 计算属性
    localTools,
    mcpTools,
    activeTools,
    inactiveTools,
    // 方法
    fetchTools,
    fetchTool,
    fetchToolsByType,
    fetchLocalTools,
    fetchMcpTools,
    createTool,
    updateTool,
    deleteTool,
    scanLocalTools,
    syncMcpTools
  }
})
