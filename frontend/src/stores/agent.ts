import { ref } from 'vue'
import { defineStore } from 'pinia'

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

export const useAgentStore = defineStore('agent', () => {
  const agents = ref<string[]>([])
  const currentAgent = ref<string>('StreamingTestAgent')
  const loading = ref(false)

  async function fetchAgents() {
    loading.value = true
    try {
      const response = await fetch(`${API_BASE}/api/stream/agents`)
      if (response.ok) {
        agents.value = await response.json()
        // 如果没有选中的 Agent 或当前 Agent 不在列表中，设置第一个为默认
        if (!currentAgent.value || !agents.value.includes(currentAgent.value)) {
          if (agents.value.length > 0 && agents.value[0]) {
            currentAgent.value = agents.value[0]!
          }
        }
      }
    } finally {
      loading.value = false
    }
  }

  async function checkAgentExists(agentName: string): Promise<boolean> {
    try {
      const response = await fetch(`${API_BASE}/api/stream/agent/${encodeURIComponent(agentName || '')}/exists`)
      if (response.ok) {
        return await response.json()
      }
      return false
    } catch {
      return false
    }
  }

  function setCurrentAgent(agentName: string) {
    currentAgent.value = agentName
  }

  return { agents, currentAgent, loading, fetchAgents, checkAgentExists, setCurrentAgent }
})
