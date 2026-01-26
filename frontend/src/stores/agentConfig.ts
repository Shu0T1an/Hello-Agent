/**
 * Agent 配置管理 Store
 */

import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { AgentConfig, CreateAgentDTO, UpdateAgentDTO } from '@/types/agent'
import * as agentApi from '@/api/agent'

export const useAgentConfigStore = defineStore('agentConfig', () => {
  // 状态
  const agents = ref<AgentConfig[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 计算属性
  const activeAgents = computed(() => agents.value.filter(a => a.isActive))
  const inactiveAgents = computed(() => agents.value.filter(a => !a.isActive))

  /**
   * 获取所有 Agent
   */
  async function fetchAgents() {
    loading.value = true
    error.value = null
    try {
      agents.value = await agentApi.fetchAgents()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch agents'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 根据 ID 获取 Agent
   */
  async function fetchAgent(id: number) {
    loading.value = true
    error.value = null
    try {
      return await agentApi.fetchAgent(id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch agent'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建 Agent
   */
  async function createAgent(dto: CreateAgentDTO) {
    loading.value = true
    error.value = null
    try {
      const created = await agentApi.createAgent(dto)
      agents.value.push(created)
      return created
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to create agent'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 更新 Agent
   */
  async function updateAgent(id: number, dto: UpdateAgentDTO) {
    loading.value = true
    error.value = null
    try {
      const updated = await agentApi.updateAgent(id, dto)
      const index = agents.value.findIndex(a => a.id === id)
      if (index !== -1) {
        agents.value[index] = updated
      }
      return updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to update agent'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 删除 Agent
   */
  async function deleteAgent(id: number) {
    loading.value = true
    error.value = null
    try {
      await agentApi.deleteAgent(id)
      agents.value = agents.value.filter(a => a.id !== id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to delete agent'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 激活 Agent
   */
  async function activateAgent(id: number) {
    loading.value = true
    error.value = null
    try {
      await agentApi.activateAgent(id)
      const agent = agents.value.find(a => a.id === id)
      if (agent) {
        agent.isActive = true
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to activate agent'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 停用 Agent
   */
  async function deactivateAgent(id: number) {
    loading.value = true
    error.value = null
    try {
      await agentApi.deactivateAgent(id)
      const agent = agents.value.find(a => a.id === id)
      if (agent) {
        agent.isActive = false
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to deactivate agent'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 重载 Agent
   */
  async function reloadAgent(id: number) {
    loading.value = true
    error.value = null
    try {
      await agentApi.reloadAgent(id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to reload agent'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 重载所有 Agent
   */
  async function reloadAllAgents() {
    loading.value = true
    error.value = null
    try {
      await agentApi.reloadAllAgents()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to reload all agents'
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    // 状态
    agents,
    loading,
    error,
    // 计算属性
    activeAgents,
    inactiveAgents,
    // 方法
    fetchAgents,
    fetchAgent,
    createAgent,
    updateAgent,
    deleteAgent,
    activateAgent,
    deactivateAgent,
    reloadAgent,
    reloadAllAgents
  }
})
