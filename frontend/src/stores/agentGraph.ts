import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchAgentGraph } from '@/api/agentGraph'
import type { AgentGraphNode, AgentGraphResponse, GraphLayoutMode } from '@/types/agent-graph'

export const useAgentGraphStore = defineStore('agentGraph', () => {
  const graph = ref<AgentGraphResponse | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const selectedNodeId = ref<string | null>(null)
  const layoutMode = ref<GraphLayoutMode>('LR')

  const selectedNode = computed<AgentGraphNode | null>(() => {
    if (!graph.value || !selectedNodeId.value) {
      return null
    }
    return graph.value.nodes.find(node => node.id === selectedNodeId.value) ?? null
  })

  async function loadGraph(agentId: number) {
    loading.value = true
    error.value = null
    try {
      graph.value = await fetchAgentGraph(agentId)
      selectedNodeId.value = graph.value.nodes[0]?.id ?? null
    } catch (e) {
      graph.value = null
      selectedNodeId.value = null
      error.value = e instanceof Error ? e.message : 'Failed to load graph'
      throw e
    } finally {
      loading.value = false
    }
  }

  function setLayout(mode: GraphLayoutMode) {
    layoutMode.value = mode
  }

  function selectNode(nodeId: string | null) {
    selectedNodeId.value = nodeId
  }

  function reset() {
    graph.value = null
    loading.value = false
    error.value = null
    selectedNodeId.value = null
    layoutMode.value = 'LR'
  }

  return {
    graph,
    loading,
    error,
    selectedNodeId,
    selectedNode,
    layoutMode,
    loadGraph,
    setLayout,
    selectNode,
    reset
  }
})

